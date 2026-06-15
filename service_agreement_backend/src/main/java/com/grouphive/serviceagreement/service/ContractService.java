package com.grouphive.serviceagreement.service;

import com.grouphive.serviceagreement.dto.ContractResponse;
import com.grouphive.serviceagreement.dto.PersonHoursResponse;
import com.grouphive.serviceagreement.dto.WorklogResponse;
import com.grouphive.serviceagreement.model.Contract;
import com.grouphive.serviceagreement.model.Worklog;
import com.grouphive.serviceagreement.repository.WorklogRepository;
import com.grouphive.serviceagreement.util.Values;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ContractService {
  private final CollaborationApiClient collaborationApiClient;
  private final WorklogRepository worklogRepository;

  public ContractService(CollaborationApiClient collaborationApiClient, WorklogRepository worklogRepository) {
    this.collaborationApiClient = collaborationApiClient;
    this.worklogRepository = worklogRepository;
  }

  public Optional<ContractResponse> findByOrderNumber(String orderNumber, String ticketNumber) {
    String normalizedOrder = Values.clean(orderNumber);
    return collaborationApiClient.findContracts(normalizedOrder).stream()
        .filter(contract -> Values.clean(contract.orderNumber()).equals(normalizedOrder))
        .findFirst()
        .map(contract -> toResponse(contract, ticketNumber));
  }

  public List<ContractResponse> findByExternalId(String externalId, String ticketNumber) {
    String normalizedExternalId = Values.clean(externalId);
    return collaborationApiClient.findContracts(normalizedExternalId).stream()
        .filter(contract -> splitExternalIds(contract.externalId()).stream()
            .anyMatch(id -> id.equals(normalizedExternalId)))
        .map(contract -> toResponse(contract, ticketNumber))
        .toList();
  }

  public ContractResponse toResponse(Contract contract, String ticketNumber) {
    String orderNumber = Values.clean(contract.orderNumber());
    List<Worklog> orderWorklogs = worklogRepository.findByOrder(orderNumber);
    String normalizedTicket = Values.clean(ticketNumber);

    BigDecimal bookedHoursInTicket = normalizedTicket.isEmpty()
        ? BigDecimal.ZERO
        : sum(orderWorklogs.stream()
            .filter(worklog -> Values.clean(worklog.ticketNumber()).equals(normalizedTicket))
            .map(Worklog::hours)
            .toList());

    BigDecimal appHoursAfterSync = sum(orderWorklogs.stream()
        .filter(worklog -> isAfterSyncCutoff(worklog, contract.importSyncedUntil()))
        .map(Worklog::hours)
        .toList());

    BigDecimal usedHoursTotal = Values.quarter(contract.usedHours().add(appHoursAfterSync));
    BigDecimal soldHours = Values.quarter(contract.soldHours());
    BigDecimal remainingHours = Values.quarter(soldHours.subtract(usedHoursTotal));

    List<PersonHoursResponse> hoursByPerson = normalizedTicket.isEmpty()
        ? List.of()
        : hoursByPerson(orderWorklogs, normalizedTicket);

    List<WorklogResponse> ticketWorklogs = normalizedTicket.isEmpty()
        ? List.of()
        : orderWorklogs.stream()
            .filter(worklog -> Values.clean(worklog.ticketNumber()).equals(normalizedTicket))
            .sorted(Comparator.comparing(Worklog::bookedAt).reversed())
            .map(this::toResponse)
            .toList();

    return new ContractResponse(
        orderNumber,
        contract.externalId(),
        contract.customer(),
        contract.country(),
        contract.start(),
        contract.validUntil(),
        contract.machineType(),
        contract.machineNo(),
        contract.contractType(),
        soldHours,
        Values.quarter(bookedHoursInTicket),
        usedHoursTotal,
        remainingHours,
        usedHoursTotal.compareTo(soldHours) >= 0,
        contract.comment(),
        hoursByPerson,
        ticketWorklogs
    );
  }

  private List<PersonHoursResponse> hoursByPerson(List<Worklog> worklogs, String ticketNumber) {
    Map<String, BigDecimal> grouped = worklogs.stream()
        .filter(worklog -> Values.clean(worklog.ticketNumber()).equals(ticketNumber))
        .collect(Collectors.groupingBy(
            Worklog::person,
            Collectors.mapping(Worklog::hours, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
        ));

    return grouped.entrySet().stream()
        .map(entry -> new PersonHoursResponse(entry.getKey(), Values.quarter(entry.getValue())))
        .sorted(Comparator.comparing(PersonHoursResponse::person))
        .toList();
  }

  private boolean isAfterSyncCutoff(Worklog worklog, Instant syncCutoff) {
    return syncCutoff == null || worklog.bookedAt().isAfter(syncCutoff);
  }

  private BigDecimal sum(List<BigDecimal> values) {
    BigDecimal total = BigDecimal.ZERO;
    for (BigDecimal value : values) {
      total = total.add(value == null ? BigDecimal.ZERO : value);
    }
    return Values.quarter(total);
  }

  private List<String> splitExternalIds(String value) {
    String raw = Values.clean(value);
    if (raw.isEmpty()) {
      return List.of();
    }
    List<String> ids = new ArrayList<>();
    for (String part : raw.split(",")) {
      String cleaned = Values.clean(part);
      if (!cleaned.isEmpty()) {
        ids.add(cleaned);
      }
    }
    return ids;
  }

  private WorklogResponse toResponse(Worklog worklog) {
    return new WorklogResponse(
        worklog.id(),
        worklog.customer(),
        worklog.person(),
        Values.quarter(worklog.hours()),
        Values.iso(worklog.bookedAt()),
        worklog.comment(),
        worklog.orderNumber(),
        worklog.ticketNumber()
    );
  }
}
