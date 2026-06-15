package com.grouphive.serviceagreement.dto;

import java.math.BigDecimal;

public record WorklogResponse(
    long id,
    String customer,
    String person,
    BigDecimal hours,
    String bookedAt,
    String comment,
    String orderNumber,
    String ticketNumber
) {
}
