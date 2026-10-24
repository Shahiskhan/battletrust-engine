package com.bte.service;
import com.bte.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
class AutomaticSettlementServiceTest {
    @Test void finalizedScoresCannotReleaseFundsWithoutAdminApproval() {
        assertThrows(BadRequestException.class, () -> new AutomaticSettlementService().settleIfFinalized(1L));
    }
}
