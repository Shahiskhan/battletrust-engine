package com.bte.service.escrow;
import java.math.*;
public record PoolDistribution(BigDecimal winners,BigDecimal organizer,BigDecimal platform) {
    public static PoolDistribution calculate(BigDecimal total,BigDecimal platformPercent,BigDecimal organizerPercent) {
        if(total.signum()<0 || platformPercent.signum()<0 || organizerPercent.signum()<0 || platformPercent.add(organizerPercent).compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Invalid pool distribution");
        BigDecimal fee=total.multiply(platformPercent).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
        BigDecimal organizer=total.multiply(organizerPercent).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP).min(total.subtract(fee));
        return new PoolDistribution(total.subtract(fee).subtract(organizer).setScale(2,RoundingMode.UNNECESSARY),organizer,fee);
    }
}
