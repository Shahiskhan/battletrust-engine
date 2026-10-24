package com.bte.entity;
import jakarta.persistence.*;import lombok.*;
@Entity @Table(name="score_audit",uniqueConstraints=@UniqueConstraint(columnNames={"match_result_id","revision"})) @Getter @NoArgsConstructor
public class ScoreAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;
 @Column(name="match_result_id",nullable=false,updatable=false)private Long matchResultId;
 @Column(nullable=false,updatable=false)private int revision;
 @Column(nullable=false,updatable=false,length=254)private String editedBy;
 @Column(nullable=false,updatable=false,length=1000)private String reason;
 @Column(nullable=false,updatable=false,columnDefinition="LONGTEXT")private String snapshot;
 @Column(nullable=false,updatable=false)private java.time.Instant createdAt;
 public ScoreAudit(Long resultId,int revision,String editor,String reason,String snapshot){this.matchResultId=resultId;this.revision=revision;this.editedBy=editor;this.reason=reason;this.snapshot=snapshot;this.createdAt=java.time.Instant.now();}
}
