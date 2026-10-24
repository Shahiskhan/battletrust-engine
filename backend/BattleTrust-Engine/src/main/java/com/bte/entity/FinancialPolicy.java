package com.bte.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Getter @Setter @NoArgsConstructor
public class FinancialPolicy {
    @Id private Long id = 1L;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal platformFeePercent = new BigDecimal("5.00");
    @Column(nullable = false) private boolean automaticPayoutsEnabled = true;
    @Version private long version;
    private String updatedBy;
}
