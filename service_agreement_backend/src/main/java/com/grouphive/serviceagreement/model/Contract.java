package com.grouphive.serviceagreement.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Contract(
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
    BigDecimal usedHours,
    Instant importSyncedUntil,
    String comment,
    String contractOwner,
    String processedBy
) {
}
