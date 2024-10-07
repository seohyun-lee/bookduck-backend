package com.mmc.bookduck.domain.book.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiBookBasicResponseDto {
    private String description;
    private Long pageCount;
    private List<String> category;

    @Builder
    public ApiBookBasicResponseDto(String description, Long pageCount, List<String> category){
        this.description = description;
        this.pageCount = pageCount;
        this.category = category;
    }

}
