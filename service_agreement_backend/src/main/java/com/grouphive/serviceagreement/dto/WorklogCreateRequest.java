package com.grouphive.serviceagreement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record WorklogCreateRequest(
    @NotBlank String ticketNumber,
    @NotBlank String orderNumber,
    String customer,
    @NotBlank String bookedAt,
    @NotEmpty List<@Valid WorklogEntryRequest> entries
) {
}
