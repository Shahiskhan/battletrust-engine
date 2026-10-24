package com.bte.repository;
import com.bte.entity.TournamentRound;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TournamentRoundRepository extends JpaRepository<TournamentRound,Long>{
 java.util.List<TournamentRound> findByTournamentIdOrderByRoundNumberAsc(Long id);
 java.util.Optional<TournamentRound> findByTournamentIdAndRoundNumber(Long id,int number);
}
