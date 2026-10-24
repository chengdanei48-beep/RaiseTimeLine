package com.raisetimeline.storage;

import com.raisetimeline.config.S3Properties;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** AWS SDK for Java v2 による実装。endpoint を指定すると S3 互換サーバー(パススタイル)に接続する。 */
@Component
public class S3ObjectStorage implements ObjectStorage {

  private final S3Properties props;
  private final S3Client client;
  private final S3Presigner presigner;

  public S3ObjectStorage(S3Properties props) {
    this.props = props;
    AwsCredentialsProvider credentials =
        props.hasStaticCredentials()
            ? StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey()))
            : DefaultCredentialsProvider.builder().build();
    Region region = Region.of(props.region());
    // 互換サーバーはバケット名をホスト名に入れられないため、パススタイルにする
    S3Configuration serviceConfig =
        S3Configuration.builder().pathStyleAccessEnabled(props.hasEndpoint()).build();

    var clientBuilder =
        S3Client.builder()
            .region(region)
            .credentialsProvider(credentials)
            .serviceConfiguration(serviceConfig)
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED);
    var presignerBuilder =
        S3Presigner.builder()
            .region(region)
            .credentialsProvider(credentials)
            .serviceConfiguration(serviceConfig);
    if (props.hasEndpoint()) {
      URI endpoint = URI.create(props.endpoint());
      clientBuilder.endpointOverride(endpoint);
      presignerBuilder.endpointOverride(endpoint);
    }
    this.client = clientBuilder.build();
    this.presigner = presignerBuilder.build();
  }

  @PreDestroy
  void close() {
    presigner.close();
    client.close();
  }

  @Override
  public PresignedUpload presignPut(String key, String contentType, long size) {
    PutObjectRequest put =
        PutObjectRequest.builder()
            .bucket(props.bucket())
            .key(key)
            .contentType(contentType)
            .contentLength(size)
            .build();
    var presigned =
        presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(props.putExpiry())
                .putObjectRequest(put)
                .build());
    return new PresignedUpload(
        presigned.url().toString(),
        props.putExpiry().toSeconds(),
        Map.of("Content-Type", contentType));
  }

  @Override
  public String presignGet(String key) {
    return presigner
        .presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(props.getExpiry())
                .getObjectRequest(
                    GetObjectRequest.builder().bucket(props.bucket()).key(key).build())
                .build())
        .url()
        .toString();
  }

  @Override
  public Optional<StoredObject> stat(String key) {
    try {
      var head =
          client.headObject(HeadObjectRequest.builder().bucket(props.bucket()).key(key).build());
      return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
      if (e.statusCode() == 404) {
        return Optional.empty();
      }
      throw e;
    }
  }
}
