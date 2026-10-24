package com.bte.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MatchRanking {
    @Column(nullable=false,columnDefinition="int not null default 0") private int placementPoints;
    public MatchRanking(Long userId,Integer rank,Integer kills,String teamName){this.playerUserId=userId;this.rank=rank;this.kills=kills;this.teamName=teamName;}

	@NotNull
	@Column(name = "player_user_id")
	private Long playerUserId;

	@NotNull
	@Min(1)
	@Column(name = "placement_rank", nullable = false)
	private Integer rank;

	@NotNull
	@Min(0)
	@Column(nullable = false)
	private Integer kills;

	@Column(name = "team_name", length = 100)
	private String teamName;
}
