package com.grouphive.serviceagreement.service;

import com.grouphive.serviceagreement.dto.WorklogCreateRequest;
import com.grouphive.serviceagreement.dto.WorklogResponse;
import com.grouphive.serviceagreement.dto.WorklogUpdateRequest;
import com.grouphive.serviceagreement.model.Worklog;
import com.grouphive.serviceagreement.repository.WorklogRepository;
import com.grouphive.serviceagreement.util.Values;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorklogService {
  private final WorklogRepository repository;

  public WorklogService(WorklogRepository repository) {
    this.repository = repository;
  }

  public void create(WorklogCreateRequest request, String fallbackCustomer) {
    Instant bookedAt = Values.instant(request.bookedAt());
    if (bookedAt == null) {
      throw new IllegalArgumentException("booked_at must be a valid ISO timestamp");
    }

    for (var entry : request.entries()) {
      validateHours(entry.hours(), "Each entry must have hours > 0");
      repository.insert(
          Values.clean(request.orderNumber()),
          customer(request.customer(), fallbackCustomer),
          Values.clean(request.ticketNumber()),
          bookedAt,
          Values.clean(entry.person()),
          Values.quarter(entry.hours()),
          Values.clean(entry.comment())
      );
    }
  }

  public boolean update(long id, WorklogUpdateRequest request) {
    validateHours(request.hours(), "hours must be > 0");
    return repository.update(
        id,
        Values.clean(request.orderNumber()),
        Values.clean(request.ticketNumber()),
        customer(request.customer(), ""),
        Values.clean(request.person()),
        Values.quarter(request.hours()),
        Values.clean(request.comment())
    );
  }

  public List<WorklogResponse> findByOrder(String orderNumber, String ticketNumber) {
    List<Worklog> worklogs = Values.clean(ticketNumber).isEmpty()
        ? repository.findByOrder(Values.clean(orderNumber))
        : repository.findByOrderAndTicket(Values.clean(orderNumber), Values.clean(ticketNumber));

    return worklogs.stream()
        .map(this::toResponse)
        .toList();
  }

  public List<Worklog> findAll() {
    return repository.findAll();
  }

  private void validateHours(BigDecimal hours, String message) {
    if (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(message);
    }
    if (!Values.isQuarterStep(hours)) {
      throw new IllegalArgumentException("Hours must be in 0.25 steps");
    }
  }

  private String customer(String provided, String fallback) {
    String cleaned = Values.clean(provided);
    return cleaned.isEmpty() ? Values.clean(fallback) : cleaned;
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
