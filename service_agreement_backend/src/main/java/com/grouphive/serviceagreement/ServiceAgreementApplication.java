package com.grouphive.serviceagreement;

import com.grouphive.serviceagreement.config.AppProperties;
import com.grouphive.serviceagreement.config.CollaborationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, CollaborationProperties.class})
public class ServiceAgreementApplication {
  public static void main(String[] args) {
    SpringApplication.run(ServiceAgreementApplication.class, args);
  }
}
