package com.mmc.bookduck.domain.folder.dto.response;

import com.mmc.bookduck.domain.folder.dto.common.CandidateFolderBookDto;

import java.util.List;

public record CandidateFolderBookListResponseDto(List<CandidateFolderBookDto> candidateFolderBookList) {
}