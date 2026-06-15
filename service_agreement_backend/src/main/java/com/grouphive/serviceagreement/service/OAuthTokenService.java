package com.grouphive.serviceagreement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.grouphive.serviceagreement.config.CollaborationProperties;
import java.time.Instant;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class OAuthTokenService {
  private final CollaborationProperties properties;
  private final RestClient restClient;
  private String accessToken;
  private Instant expiresAt = Instant.EPOCH;

  public OAuthTokenService(CollaborationProperties properties, RestClient.Builder builder) {
    this.properties = properties;
    this.restClient = builder.build();
  }

  public synchronized String getAccessToken() {
    if (accessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
      return accessToken;
    }

    var oauth = properties.getOauth();
    if (isBlank(oauth.getTokenUrl()) || isBlank(oauth.getClientId()) || isBlank(oauth.getClientSecret())) {
      throw new IllegalStateException("OAuth token-url, client-id and client-secret must be configured");
    }

    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", Objects.toString(oauth.getGrantType(), "client_credentials"));
    form.add("client_id", oauth.getClientId());
    form.add("client_secret", oauth.getClientSecret());
    if (!isBlank(oauth.getScope())) {
      form.add("scope", oauth.getScope());
    }

    JsonNode response = restClient.post()
        .uri(oauth.getTokenUrl())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .body(JsonNode.class);

    if (response == null || !response.hasNonNull("access_token")) {
      throw new IllegalStateException("OAuth token response did not contain access_token");
    }

    accessToken = response.get("access_token").asText();
    long expiresIn = response.path("expires_in").asLong(3600);
    expiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn));
    return accessToken;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
