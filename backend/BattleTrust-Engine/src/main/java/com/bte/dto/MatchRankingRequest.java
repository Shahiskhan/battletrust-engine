package com.bte.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MatchRankingRequest(
		@NotNull @Positive Long playerUserId,
		@NotNull @Min(1) Integer rank,
		@NotNull @Min(0) Integer kills,
		@Size(max = 100) String teamName,
        @Min(0) @jakarta.validation.constraints.Max(10000) Integer placementPoints,
        @Size(max=4) java.util.Map<@Positive Long,@NotNull @Min(0) @jakarta.validation.constraints.Max(100000) Integer> playerKills
) {
    public MatchRankingRequest(Long userId,Integer rank,Integer kills,String teamName){this(userId,rank,kills,teamName,null,null);}
}
