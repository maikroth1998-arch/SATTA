package com.grouphive.serviceagreement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grouphive.serviceagreement.config.CollaborationProperties;
import com.grouphive.serviceagreement.model.Contract;
import com.grouphive.serviceagreement.util.Values;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CollaborationApiClient {
  private final CollaborationProperties properties;
  private final OAuthTokenService tokenService;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public CollaborationApiClient(
      CollaborationProperties properties,
      OAuthTokenService tokenService,
      RestClient.Builder builder,
      ObjectMapper objectMapper
  ) {
    this.properties = properties;
    this.tokenService = tokenService;
    this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
    this.objectMapper = objectMapper;
  }

  public List<Contract> findContracts(String preferredQuickFilter) {
    Map<String, Contract> contracts = new LinkedHashMap<>();
    for (String quickFilter : quickFilters(preferredQuickFilter)) {
      for (Contract contract : queryContracts(quickFilter)) {
        contracts.put(contract.orderNumber(), contract);
      }
    }
    return new ArrayList<>(contracts.values());
  }

  private List<String> quickFilters(String preferredQuickFilter) {
    List<String> filters = new ArrayList<>();
    String preferred = Values.clean(preferredQuickFilter);
    if (!preferred.isEmpty()) {
      filters.add(preferred);
    }
    if (properties.getQuickFilters() != null) {
      properties.getQuickFilters().stream()
          .map(Values::clean)
          .filter(value -> !value.isEmpty())
          .filter(value -> !filters.contains(value))
          .forEach(filters::add);
    }
    if (filters.isEmpty()) {
      filters.add("");
    }
    return filters;
  }

  private List<Contract> queryContracts(String quickFilter) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("QuickFilter", quickFilter);
    request.put("Limit", properties.getLimit());
    if (properties.getIsInactive() != null) {
      request.put("IsInactive", properties.getIsInactive());
    }
    if (!Values.clean(properties.getElementCategoryId()).isEmpty()) {
      request.put("ElementCategoryId", properties.getElementCategoryId());
    }

    JsonNode response = restClient.post()
        .uri(properties.getProductsPath())
        .headers(headers -> headers.setBearerAuth(tokenService.getAccessToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(JsonNode.class);

    List<Contract> contracts = new ArrayList<>();
    JsonNode data = response == null ? objectMapper.createArrayNode() : response.path("data");
    if (!data.isArray()) {
      return contracts;
    }

    for (JsonNode product : data) {
      JsonNode serviceContracts = product.path("Comps").path("servicecontract");
      if (!serviceContracts.isArray()) {
        continue;
      }
      for (JsonNode serviceContract : serviceContracts) {
        if (serviceContract.path("Inactive").asBoolean(false)) {
          continue;
        }

        String contractNo = firstText(serviceContract, "contractno", "contractNo", "ContractNo");
        if (contractNo.isEmpty()) {
          continue;
        }

        contracts.add(new Contract(
            contractNo,
            externalIds(product, serviceContract),
            firstText(product, "Customer", "customer", "customername", "customerName"),
            firstText(product, "Country", "country"),
            Values.monthYear(firstText(serviceContract, "servicecontractstartdate", "serviceContractStartDate")),
            Values.monthYear(firstText(serviceContract, "servicecontractenddate", "serviceContractEndDate")),
            firstText(product, "machine_type", "machineType", "Maschine type", "MachineType", "ElementCategoryName"),
            firstText(product, "machine_no", "machineNo", "Maschine no.", "serialnumber", "SerialNumber"),
            contractType(serviceContract),
            Values.decimal(firstValue(serviceContract, "additionalhoursissued", "additionalHoursIssued")),
            BigDecimal.ZERO,
            Instant.now(),
            firstText(serviceContract, "comment", "Comment", "additionalinformation"),
            firstText(serviceContract, "contractowner", "contractOwner"),
            firstText(serviceContract, "processedby", "processedBy")
        ));
      }
    }
    return contracts;
  }

  private String externalIds(JsonNode product, JsonNode serviceContract) {
    List<String> ids = new ArrayList<>();
    addIfPresent(ids, firstText(product, "ExternalId", "externalId", "external_id", "Id", "id"));
    addIfPresent(ids, firstText(serviceContract, "externalId", "ExternalId", "Id", "id"));
    return String.join(", ", ids);
  }

  private String contractType(JsonNode serviceContract) {
    String raw = firstText(serviceContract, "contracttype", "contractType");
    return switch (raw) {
      case "1" -> "HF Hotline & Remote Support";
      case "2" -> "HF basicCare Agreement";
      case "3" -> "HF basicCare plus Agreement";
      case "4" -> "HF advancedCare Agreement";
      default -> raw;
    };
  }

  private void addIfPresent(List<String> values, String value) {
    if (!value.isEmpty() && !values.contains(value)) {
      values.add(value);
    }
  }

  private String firstText(JsonNode node, String... names) {
    Object value = firstValue(node, names);
    return Values.clean(value);
  }

  private Object firstValue(JsonNode node, String... names) {
    for (String name : names) {
      JsonNode value = node.get(name);
      if (value != null && !value.isNull()) {
        return value.isValueNode() ? value.asText() : value.toString();
      }
    }
    return "";
  }
}
