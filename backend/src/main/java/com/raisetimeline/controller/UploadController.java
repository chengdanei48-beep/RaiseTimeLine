package com.raisetimeline.controller;

import com.raisetimeline.dto.PresignRequest;
import com.raisetimeline.dto.PresignResponse;
import com.raisetimeline.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

  private final UploadService uploadService;

  public UploadController(UploadService uploadService) {
    this.uploadService = uploadService;
  }

  @PostMapping("/presign")
  public PresignResponse presign(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PresignRequest request) {
    return uploadService.presign(Long.parseLong(jwt.getSubject()), request);
  }
}
