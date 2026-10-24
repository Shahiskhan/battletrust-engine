package com.bte.entity;

import com.bte.enums.VerificationStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "match_results", uniqueConstraints = {
		@UniqueConstraint(name = "uk_match_result_tournament_number",
				columnNames = {"tournament_id", "match_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchResult {
    @Builder.Default @ElementCollection @CollectionTable(name="match_player_scores",joinColumns=@JoinColumn(name="match_result_id"))
    private List<MatchPlayerScore> playerScores=new ArrayList<>();
    @Builder.Default @Column(nullable=false,columnDefinition="int not null default 1") private int revision=1;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	@NotNull
	@Min(1)
	@Column(name = "match_number", nullable = false)
	private Integer matchNumber;

	@Column(columnDefinition = "TEXT")
	private String winnerDetailsJson;

	@Builder.Default
	@ElementCollection
	@CollectionTable(name = "match_result_rankings",
			joinColumns = @JoinColumn(name = "match_result_id"))
	@OrderColumn(name = "ranking_order")
	private List<MatchRanking> rankings = new ArrayList<>();

	@Builder.Default
	@ElementCollection
	@CollectionTable(name = "match_result_proof_images",
			joinColumns = @JoinColumn(name = "match_result_id"))
	@OrderColumn(name = "image_order")
	@Column(name = "proof_image_url", nullable = false, length = 2048)
	private List<String> proofImageUrls = new ArrayList<>();

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private VerificationStatus verificationStatus = VerificationStatus.YELLOW_TICK_UNVERIFIED;

	@Column(nullable = false, updatable = false)
	private LocalDateTime submittedAt;

	@Column(nullable = false)
	private LocalDateTime disputeDeadline;

	@Column(nullable = false)
	private boolean disputeWindowClosed;

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		submittedAt = now;
		if (disputeDeadline == null) {
			disputeDeadline = now.plusHours(2);
		}
	}
}
