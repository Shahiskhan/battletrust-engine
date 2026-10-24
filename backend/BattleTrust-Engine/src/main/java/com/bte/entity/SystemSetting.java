package com.bte.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "system_settings")
public class SystemSetting {
    @Id private String id = "organizer.staking";
    @Column(nullable = false) private boolean enabled = false;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount = new BigDecimal("500.00");
    @Column(nullable = false, precision = 8, scale = 2) private BigDecimal multiplier = BigDecimal.ONE;
    @Column(nullable = false) private String mode = "FIXED";
    private String updatedBy;
    private Instant updatedAt = Instant.now();
    @Version private long version;
}
