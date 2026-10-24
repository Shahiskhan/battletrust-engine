package com.bte.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Getter @Setter @NoArgsConstructor
public class PlatformTreasury {
    @Id private Long id = 1L;
    @Column(nullable = false, precision = 16, scale = 2) private BigDecimal balance = BigDecimal.ZERO;
}
