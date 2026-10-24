package com.bte.repository;

import com.bte.entity.LedgerTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {
	@org.springframework.data.jpa.repository.Query("select l from LedgerTransaction l where l.senderId in :userIds or l.receiverId in :userIds or l.referenceId in :references order by l.createdAt desc")
	Page<LedgerTransaction> findForOwner(java.util.List<Long> userIds, java.util.List<String> references, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select sum(l.amount) from LedgerTransaction l where l.tournamentId=:tournamentId and l.receiverId in :ids and l.transactionType in (com.bte.enums.LedgerTransactionType.WINNING_PAYOUT,com.bte.enums.LedgerTransactionType.WINNER_PAYOUT)")
    java.math.BigDecimal earnings(Long tournamentId,java.util.List<Long> ids);
    Page<LedgerTransaction> findByTournamentIdOrderByIdAsc(Long tournamentId,Pageable pageable);
    java.util.List<LedgerTransaction> findByTournamentIdAndIdGreaterThanAndIdLessThanEqualOrderByIdAsc(Long tournamentId,Long after,Long upper,Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select coalesce(max(l.id),0) from LedgerTransaction l where l.tournamentId=:id")Long maxId(Long id);
    @org.springframework.data.jpa.repository.Query("select l.tournamentId,l.transactionType,sum(l.amount) from LedgerTransaction l where l.tournamentId in :ids group by l.tournamentId,l.transactionType")java.util.List<Object[]> totals(java.util.List<Long> ids);
	Page<LedgerTransaction> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(
			Long senderId, Long receiverId, Pageable pageable);
}
