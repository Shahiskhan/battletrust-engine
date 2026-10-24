package com.bte.service;

import com.bte.exception.BadRequestException;
import org.springframework.stereotype.Service;

/** Retired compatibility path: finalized scores do not authorize escrow release. */
@Deprecated
@Service
public class AutomaticSettlementService {
    @Deprecated
    public void settleIfFinalized(Long id) {
        throw new BadRequestException("Escrow release requires an explicit admin-reviewed winner declaration");
    }
}
