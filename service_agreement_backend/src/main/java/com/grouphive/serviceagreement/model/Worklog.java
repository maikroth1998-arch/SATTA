package com.grouphive.serviceagreement.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Worklog(
    long id,
    String orderNumber,
    String customer,
    String ticketNumber,
    Instant bookedAt,
    String person,
    BigDecimal hours,
    String comment
) {
}
