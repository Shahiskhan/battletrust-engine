package com.bte.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "payment_vouchers", uniqueConstraints = {@UniqueConstraint(name = "uk_voucher_registration", columnNames = "registration_id"), @UniqueConstraint(name="uk_voucher_inventory",columnNames={"tournament_id","inventory_number"})})
public class PaymentVoucher {
    @Id @Column(length = 10) private String id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "registration_id") private Registration registration;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="tournament_id") private Tournament tournament;
    private Integer inventoryNumber;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false) private String status = "PENDING";
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
