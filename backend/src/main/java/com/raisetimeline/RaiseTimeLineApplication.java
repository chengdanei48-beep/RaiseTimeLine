package com.raisetimeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RaiseTimeLineApplication {

  public static void main(String[] args) {
    SpringApplication.run(RaiseTimeLineApplication.class, args);
  }
}
