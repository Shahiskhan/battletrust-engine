package com.bte.service;

import com.bte.entity.MatchRanking;
import com.bte.entity.MatchResult;
import com.bte.entity.PlayerCategoryStats;
import com.bte.entity.Registration;
import com.bte.entity.Tournament;
import com.bte.entity.User;
import com.bte.enums.GameCategory;
import com.bte.repository.MatchResultRepository;
import com.bte.repository.PlayerCategoryStatsRepository;
import com.bte.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PlayerPerformanceService {

	static final int PARTICIPATION_REPUTATION = 5;
	static final int WIN_REPUTATION = 20;
	static final int KILL_REPUTATION = 1;

	private final UserRepository userRepository;
	private final MatchResultRepository matchResultRepository;
	private final PlayerCategoryStatsRepository categoryStatsRepository;

	public PlayerPerformanceService(
			UserRepository userRepository,
			MatchResultRepository matchResultRepository,
			PlayerCategoryStatsRepository categoryStatsRepository) {
		this.userRepository = userRepository;
		this.matchResultRepository = matchResultRepository;
		this.categoryStatsRepository = categoryStatsRepository;
	}

	@Transactional
	public void applyTournamentSettlement(
			Tournament tournament,
			List<Registration> registrations,
			Collection<Long> winnerUserIds) {
		List<MatchResult> results = matchResultRepository
				.findByTournamentIdOrderByMatchNumberAsc(tournament.getId());
		Map<Long, Integer> killsByUser = sumKills(results);
		for (Registration r : registrations) {
            for(var member:r.getSquadMembers())if(member.getOriginalGuestUserId()!=null && member.getWalletUserId()!=null && !member.getOriginalGuestUserId().equals(member.getWalletUserId())){Integer old=killsByUser.remove(member.getOriginalGuestUserId());if(old!=null)killsByUser.merge(member.getWalletUserId(),old,Integer::sum);}
			if (r.getGuestAccountId() != null && r.getOwnerUserId() != null && !r.getGuestAccountId().equals(r.getOwnerUserId())) {
				Integer oldKills = killsByUser.remove(r.getGuestAccountId());
				if (oldKills != null) killsByUser.merge(r.getOwnerUserId(), oldKills, Integer::sum);
			}
		}
		Set<Long> participantIds = resolveParticipants(registrations, killsByUser.keySet(), winnerUserIds);
		Set<Long> winners = winnerUserIds == null ? Set.of() : new HashSet<>(winnerUserIds);
		GameCategory category = tournament.getGameCategory();

		for (Long userId : participantIds) {
			User user = userRepository.findByIdForUpdate(userId).orElse(null);
			if (user == null || user.getRole()==com.bte.enums.Role.ROLE_ADMIN) {
				continue;
			}
			int kills = killsByUser.getOrDefault(userId, 0);
			boolean won = winners.contains(userId);
			int played=tournament.getPlannedRounds()>0?results.size():1;
            int wins=tournament.getPlannedRounds()>0?(int)results.stream().filter(match->match.getRankings().stream().filter(row->row.getRank()==1).anyMatch(row->registrations.stream().filter(r->java.util.Objects.equals(row.getPlayerUserId(),r.getOwnerUserId()) || java.util.Objects.equals(row.getPlayerUserId(),r.getGuestAccountId())).anyMatch(r->java.util.Objects.equals(userId,r.getOwnerUserId()) || r.getSquadMembers().stream().anyMatch(m->java.util.Objects.equals(userId,m.getWalletUserId()))))).count():(won?1:0);
            applyDelta(user, played,wins,kills);
			if (category != null) {
				applyCategoryDelta(user, category, played,wins,kills);
			}
		}
	}

	private Set<Long> resolveParticipants(
			List<Registration> registrations,
			Set<Long> rankedUserIds,
			Collection<Long> winnerUserIds) {
		Set<Long> participantIds = new TreeSet<>();
		if (registrations != null) {
			for (Registration registration : registrations) {
				if (registration.getOwnerUserId() != null) participantIds.add(registration.getOwnerUserId());
				registration.getSquadMembers().stream().skip(1).filter(m -> m.getWalletUserId()!=null).forEach(m -> participantIds.add(m.getWalletUserId()));
			}
		}
		participantIds.addAll(rankedUserIds);
		if (winnerUserIds != null) {
			participantIds.addAll(winnerUserIds);
		}
		return participantIds;
	}

	private void applyDelta(User user, int played,int wins, int kills) {
		user.setMatchesPlayed(user.getMatchesPlayed() + played);
		if (wins>0) {
			user.setMatchesWon(user.getMatchesWon() + wins);
		}
		user.setTotalKills(user.getTotalKills() + Math.max(kills, 0));
		user.setReputationScore(user.getReputationScore() + (PARTICIPATION_REPUTATION*played+WIN_REPUTATION*wins+Math.max(kills,0)*KILL_REPUTATION));
		user.setWinRatePercent(winRate(user.getMatchesWon(), user.getMatchesPlayed()));
	}

	private void applyCategoryDelta(User user, GameCategory category, int played,int wins,int kills) {
		PlayerCategoryStats stats = categoryStatsRepository
				.findByUserIdAndGameCategoryForUpdate(user.getId(), category)
				.orElseGet(() -> categoryStatsRepository.save(PlayerCategoryStats.builder()
						.user(user)
						.gameCategory(category)
						.build()));
		stats.setMatchesPlayed(stats.getMatchesPlayed() + played);
		if (wins>0) {
			stats.setMatchesWon(stats.getMatchesWon() + wins);
		}
		stats.setTotalKills(stats.getTotalKills() + Math.max(kills, 0));
		stats.setReputationScore(stats.getReputationScore() + (PARTICIPATION_REPUTATION*played+WIN_REPUTATION*wins+Math.max(kills,0)*KILL_REPUTATION));
		stats.setWinRatePercent(winRate(stats.getMatchesWon(), stats.getMatchesPlayed()));
	}

	private Map<Long, Integer> sumKills(List<MatchResult> results) {
		Map<Long, Integer> killsByUser = new HashMap<>();
		if (results == null) {
			return killsByUser;
		}
		for (MatchResult result : results) {
            if(!result.getPlayerScores().isEmpty()){for(var score:result.getPlayerScores())killsByUser.merge(score.getUserId(),score.getKills(),Integer::sum);continue;}
			if (result.getRankings() == null) {
				continue;
			}
			for (MatchRanking ranking : result.getRankings()) {
				if (ranking.getPlayerUserId() == null) {
					continue;
				}
				int kills = ranking.getKills() == null ? 0 : Math.max(ranking.getKills(), 0);
				killsByUser.merge(ranking.getPlayerUserId(), kills, Integer::sum);
			}
		}
		return killsByUser;
	}

	private int reputationDelta(boolean won, int kills) {
		return PARTICIPATION_REPUTATION
				+ (won ? WIN_REPUTATION : 0)
				+ (Math.max(kills, 0) * KILL_REPUTATION);
	}

	private BigDecimal winRate(int matchesWon, int matchesPlayed) {
		if (matchesPlayed <= 0) {
			return new BigDecimal("0.00");
		}
		return BigDecimal.valueOf(matchesWon)
				.multiply(new BigDecimal("100"))
				.divide(BigDecimal.valueOf(matchesPlayed), 2, RoundingMode.HALF_UP);
	}

	private void addEmail(Set<String> emails, String email) {
		if (email != null && !email.isBlank()) {
			emails.add(email.trim().toLowerCase(Locale.ROOT));
		}
	}
}
