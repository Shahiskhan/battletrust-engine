package com.bte.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
public class PayoutApproval {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private Long tournamentId;
    @Column(nullable = false) private String requestedBy;
    @Column(nullable = false, columnDefinition = "TEXT") private String payload;
    @Column(nullable = false) private String status = "PENDING";
    private String reviewedBy;
    private Instant requestedAt = Instant.now();
    private Instant reviewedAt;
    @Column(length=1000) private String reviewNote;
}
