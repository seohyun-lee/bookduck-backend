package com.mmc.bookduck.domain.user.dto.response;

public record UserGrowthInfoResponseDto (
        int level,
        long expInCurrentLevel,
        long expToNextLevel
) {
}