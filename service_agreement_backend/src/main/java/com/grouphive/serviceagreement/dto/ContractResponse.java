package com.grouphive.serviceagreement.dto;

import java.math.BigDecimal;
import java.util.List;

public record ContractResponse(
    String orderNumber,
    String externalId,
    String customer,
    String country,
    String start,
    String validUntil,
    String machineType,
    String machineNo,
    String contractType,
    BigDecimal soldHours,
    BigDecimal bookedHoursInTicket,
    BigDecimal usedHoursTotal,
    BigDecimal remainingHours,
    boolean exceeded,
    String comment,
    List<PersonHoursResponse> hoursByPerson,
    List<WorklogResponse> ticketWorklogs
) {
}
