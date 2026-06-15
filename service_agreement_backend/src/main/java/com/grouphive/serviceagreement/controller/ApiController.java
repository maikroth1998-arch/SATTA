package com.grouphive.serviceagreement.controller;

import com.grouphive.serviceagreement.config.AppProperties;
import com.grouphive.serviceagreement.dto.ContractResponse;
import com.grouphive.serviceagreement.dto.ErrorResponse;
import com.grouphive.serviceagreement.dto.MultipleContractsResponse;
import com.grouphive.serviceagreement.dto.WorklogCreateRequest;
import com.grouphive.serviceagreement.dto.WorklogUpdateRequest;
import com.grouphive.serviceagreement.service.ContractService;
import com.grouphive.serviceagreement.service.WorklogExportService;
import com.grouphive.serviceagreement.service.WorklogService;
import com.grouphive.serviceagreement.util.Values;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {
  private final ContractService contractService;
  private final WorklogService worklogService;
  private final WorklogExportService exportService;
  private final AppProperties appProperties;

  public ApiController(
      ContractService contractService,
      WorklogService worklogService,
      WorklogExportService exportService,
      AppProperties appProperties
  ) {
    this.contractService = contractService;
    this.worklogService = worklogService;
    this.exportService = exportService;
    this.appProperties = appProperties;
  }

  @GetMapping("/")
  public Map<String, Object> index() {
    return Map.of("ok", true, "message", "Java/Postgres backend is running");
  }

  @GetMapping("/contract/{orderNumber}")
  public ResponseEntity<?> contract(
      @PathVariable String orderNumber,
      @RequestParam(name = "ticket_number", required = false) String ticketNumber
  ) {
    return contractService.findByOrderNumber(orderNumber, ticketNumber)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(404)
            .body(new ErrorResponse("No contract found for order number " + orderNumber)));
  }

  @GetMapping("/contract-by-external-id/{externalId}")
  public ResponseEntity<?> contractByExternalId(
      @PathVariable String externalId,
      @RequestParam(name = "ticket_number", required = false) String ticketNumber
  ) {
    var contracts = contractService.findByExternalId(externalId, ticketNumber);
    if (contracts.isEmpty()) {
      return ResponseEntity.status(404)
          .body(new ErrorResponse("No contract found for external ID " + externalId));
    }
    if (contracts.size() == 1) {
      return ResponseEntity.ok(contracts.getFirst());
    }
    return ResponseEntity.ok(new MultipleContractsResponse(true, contracts));
  }

  @PostMapping("/worklog")
  public ResponseEntity<?> createWorklog(@Valid @RequestBody WorklogCreateRequest request) {
    ContractResponse contract = contractService.findByOrderNumber(request.orderNumber(), request.ticketNumber())
        .orElseThrow(() -> new NotFoundException("No contract found for order number " + request.orderNumber()));
    worklogService.create(request, contract.customer());
    return ResponseEntity.ok(Map.of("success", true));
  }

  @GetMapping("/worklogs/{orderNumber}")
  public ResponseEntity<?> worklogs(
      @PathVariable String orderNumber,
      @RequestParam(name = "ticket_number", required = false) String ticketNumber
  ) {
    return ResponseEntity.ok(worklogService.findByOrder(orderNumber, ticketNumber));
  }

  @PutMapping("/worklog/{id}")
  public ResponseEntity<?> updateWorklog(
      @PathVariable long id,
      @Valid @RequestBody WorklogUpdateRequest request
  ) {
    boolean updated = worklogService.update(id, request);
    if (!updated) {
      return ResponseEntity.status(404).body(new ErrorResponse("No worklog found for id " + id));
    }
    return ResponseEntity.ok(Map.of("success", true, "id", id));
  }

  @GetMapping("/export/worklogs")
  public ResponseEntity<?> exportWorklogs(@RequestHeader(name = "x-export-token", required = false) String token) {
    String expectedToken = Values.clean(appProperties.exportToken());
    if (expectedToken.isEmpty()) {
      return ResponseEntity.status(500).body(new ErrorResponse("EXPORT_TOKEN is not configured"));
    }
    if (!expectedToken.equals(Values.clean(token))) {
      return ResponseEntity.status(401).body(new ErrorResponse("Unauthorized"));
    }

    byte[] workbook = exportService.createWorkbook(worklogService.findAll());
    String fileName = "worklogs_export_" + LocalDate.now() + ".xlsx";

    return ResponseEntity.ok()
        .header("X-File-Name", fileName)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
        .body(java.util.Base64.getEncoder().encodeToString(workbook));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(NotFoundException exception) {
    return ResponseEntity.status(404).body(new ErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
  public ResponseEntity<ErrorResponse> badRequest(Exception exception) {
    return ResponseEntity.badRequest().body(new ErrorResponse("Invalid request: " + exception.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> serverError(Exception exception) {
    return ResponseEntity.status(500).body(new ErrorResponse(exception.getMessage()));
  }

  private static class NotFoundException extends RuntimeException {
    NotFoundException(String message) {
      super(message);
    }
  }
}
