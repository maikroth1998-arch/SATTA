package com.grouphive.serviceagreement.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "collaboration")
public class CollaborationProperties {
  private String baseUrl = "https://masterdata-api-prod.grouphive.de";
  private String productsPath = "/elements/aa0000000000000000000010/query";
  private int limit = 1000;
  private Boolean isInactive = true;
  private String elementCategoryId = "";
  private List<String> quickFilters = new ArrayList<>();
  private OAuth oauth = new OAuth();

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getProductsPath() {
    return productsPath;
  }

  public void setProductsPath(String productsPath) {
    this.productsPath = productsPath;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }

  public Boolean getIsInactive() {
    return isInactive;
  }

  public void setIsInactive(Boolean isInactive) {
    this.isInactive = isInactive;
  }

  public String getElementCategoryId() {
    return elementCategoryId;
  }

  public void setElementCategoryId(String elementCategoryId) {
    this.elementCategoryId = elementCategoryId;
  }

  public List<String> getQuickFilters() {
    return quickFilters;
  }

  public void setQuickFilters(List<String> quickFilters) {
    this.quickFilters = quickFilters;
  }

  public OAuth getOauth() {
    return oauth;
  }

  public void setOauth(OAuth oauth) {
    this.oauth = oauth;
  }

  public static class OAuth {
    private String tokenUrl = "";
    private String clientId = "";
    private String clientSecret = "";
    private String scope = "";
    private String grantType = "client_credentials";

    public String getTokenUrl() {
      return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
      this.tokenUrl = tokenUrl;
    }

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getClientSecret() {
      return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
    }

    public String getScope() {
      return scope;
    }

    public void setScope(String scope) {
      this.scope = scope;
    }

    public String getGrantType() {
      return grantType;
    }

    public void setGrantType(String grantType) {
      this.grantType = grantType;
    }
  }
}
