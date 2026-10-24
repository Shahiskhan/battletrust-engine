package com.bte.service;
import com.bte.entity.*;import com.bte.repository.*;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor public class ScoreAuditService {
 private final ScoreAuditRepository audits;private final tools.jackson.databind.ObjectMapper mapper;
 public void record(MatchResult result,String editor,String reason){audits.save(new ScoreAudit(result.getId(),result.getRevision(),editor,reason,mapper.writeValueAsString(java.util.Map.of("rankings",result.getRankings(),"players",result.getPlayerScores(),"evidence",result.getProofImageUrls()))));}
}
