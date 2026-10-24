package com.bte.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="tournament_rounds",uniqueConstraints=@UniqueConstraint(columnNames={"tournament_id","round_number"})) @Getter @Setter @NoArgsConstructor
public class TournamentRound {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) @JoinColumn(name="tournament_id",nullable=false) private Tournament tournament;
 @Column(name="round_number",nullable=false) private int roundNumber;
 @Column(nullable=false,length=100) private String mapName;
 @Column(nullable=false,length=16) private String status="SCHEDULED";
 @Column(length=100) private String roomId;
 @Column(length=255) private String roomPassword;
 private java.time.LocalDateTime startedAt;
 private java.time.LocalDateTime completedAt;
}
