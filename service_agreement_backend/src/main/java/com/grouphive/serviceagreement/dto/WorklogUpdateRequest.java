package com.grouphive.serviceagreement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record WorklogUpdateRequest(
    @NotBlank String ticketNumber,
    @NotBlank String orderNumber,
    String customer,
    @NotBlank String person,
    @NotNull BigDecimal hours,
    String comment
) {
}
