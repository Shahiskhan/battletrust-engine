package com.bte.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Getter @Setter @NoArgsConstructor
public class EmailVerification {
    @Id private Long userId;
    @Column(nullable=false,unique=true,length=64) private String tokenHash;
    @Column(nullable=false) private Instant expiresAt;
    @Column(nullable=false) private Instant lastSentAt;
    private Instant usedAt;
}
