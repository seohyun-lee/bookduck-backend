package com.mmc.bookduck.domain.book.dto.response;

import com.mmc.bookduck.domain.book.entity.ReadStatus;
import com.mmc.bookduck.domain.book.entity.UserBook;

public record UserBookResponseDto(
        Long userBookId,
        String title,
        String author,
        String imgPath,
        ReadStatus readStatus,
        double rating,
        Long bookInfoId,
        Boolean isCustom
) {
    public UserBookResponseDto(UserBook userBook, boolean isCustom) {
        this(
                userBook.getUserBookId(),
                userBook.getBookInfo().getTitle(),
                userBook.getBookInfo().getAuthor(),
                userBook.getBookInfo().getImgPath(),
                userBook.getReadStatus(),
                userBook.getRating(),
                userBook.getBookInfo().getBookInfoId(),
                isCustom
        );
    }
}