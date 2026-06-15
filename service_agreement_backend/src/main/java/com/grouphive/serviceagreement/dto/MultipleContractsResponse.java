package com.grouphive.serviceagreement.dto;

import java.util.List;

public record MultipleContractsResponse(
    boolean multiple,
    List<ContractResponse> contracts
) {
}
