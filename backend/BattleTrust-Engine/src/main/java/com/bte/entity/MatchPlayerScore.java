package com.bte.entity;
import jakarta.persistence.*;
import lombok.*;
@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MatchPlayerScore {
 @Column(nullable=false) private Long userId;
 @Column(nullable=false) private Long captainUserId;
 @Column(nullable=false) private int kills;
}
