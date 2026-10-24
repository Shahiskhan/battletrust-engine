package com.bte.repository;
import com.bte.entity.ScoreAudit;import org.springframework.data.jpa.repository.JpaRepository;
public interface ScoreAuditRepository extends JpaRepository<ScoreAudit,Long>{java.util.List<ScoreAudit> findByMatchResultIdOrderByRevisionAsc(Long id);}
