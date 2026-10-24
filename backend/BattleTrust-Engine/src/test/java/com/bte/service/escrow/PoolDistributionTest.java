package com.bte.service.escrow;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class PoolDistributionTest {
    @Test void deductsConfiguredFeeBeforeWinnerRemainder(){var d=PoolDistribution.calculate(new BigDecimal("400.00"),new BigDecimal("7.5"),BigDecimal.ZERO);assertEquals(new BigDecimal("30.00"),d.platform());assertEquals(new BigDecimal("370.00"),d.winners());}
    @Test void halfCentRoundingCannotCreateMoney(){var d=PoolDistribution.calculate(new BigDecimal("0.01"),BigDecimal.ZERO,new BigDecimal("50"));assertEquals(new BigDecimal("0.01"),d.winners().add(d.organizer()).add(d.platform()));assertTrue(d.winners().signum()>=0);}
    @Test void allCentSizedPoolsConserveFunds(){for(int cents=1;cents<1000;cents++){var total=BigDecimal.valueOf(cents,2);var d=PoolDistribution.calculate(total,new BigDecimal("7.5"),new BigDecimal("10"));assertEquals(total,d.winners().add(d.organizer()).add(d.platform()));}}
}
