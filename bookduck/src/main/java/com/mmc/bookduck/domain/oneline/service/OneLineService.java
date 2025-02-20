package com.mmc.bookduck.domain.oneline.service;

import com.mmc.bookduck.domain.badge.service.BadgeUnlockService;
import com.mmc.bookduck.domain.book.entity.UserBook;
import com.mmc.bookduck.domain.book.service.UserBookService;
import com.mmc.bookduck.domain.homecard.dto.common.OneLineRatingWithBookInfoUnitDto;
import com.mmc.bookduck.domain.oneline.dto.request.OneLineCreateRequestDto;
import com.mmc.bookduck.domain.oneline.dto.request.OneLineUpdateRequestDto;
import com.mmc.bookduck.domain.oneline.entity.OneLine;
import com.mmc.bookduck.domain.oneline.repository.OneLineRepository;
import com.mmc.bookduck.domain.user.entity.User;
import com.mmc.bookduck.domain.user.service.UserGrowthService;
import com.mmc.bookduck.domain.user.service.UserService;
import com.mmc.bookduck.global.common.PaginatedResponseDto;
import com.mmc.bookduck.global.exception.CustomException;
import com.mmc.bookduck.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.mmc.bookduck.global.common.EscapeSpecialCharactersService.escapeSpecialCharacters;

@Service
@RequiredArgsConstructor
@Transactional
public class OneLineService {
    private final OneLineRepository oneLineRepository;
    private final UserService userService;
    private final UserBookService userBookService;
    private final BadgeUnlockService badgeUnlockService;
    private final UserGrowthService userGrowthService;

    // 생성
    public OneLine createOneLine(OneLineCreateRequestDto requestDto) {
        User user = userService.getCurrentUser();
        UserBook userBook = userBookService.getUserBookById(requestDto.userBookId());
        userBookService.validateUserBookOwner(userBook);
        OneLine oneLine = requestDto.toEntity(user, userBook);
        checkExpAndBadgeForOneLine(userBook);
        return oneLineRepository.save(oneLine);
    }

    // 경험치 획득, ONELINE 뱃지 unlock 확인
    public void checkExpAndBadgeForOneLine(UserBook userBook) {
        userGrowthService.gainExpForOneLine(userBook);
        badgeUnlockService.checkAndUnlockBadges(userBook.getUser());
    }

    // 수정
    public void updateOneLine(Long oneLineId, OneLineUpdateRequestDto requestDto) {
        OneLine oneLine = validateOneLineCreator(oneLineId);
        oneLine.updateOneLine(requestDto.oneLineContent());
    }

    // 삭제
    public void deleteOneLine(Long oneLineId) {
        OneLine oneLine = validateOneLineCreator(oneLineId);
        oneLineRepository.delete(oneLine);
    }

    @Transactional(readOnly = true)
    public OneLine getOneLineById(Long oneLineId) {
        return oneLineRepository.findById(oneLineId)
                .orElseThrow(() -> new CustomException(ErrorCode.ONELINE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public OneLine validateOneLineCreator(Long oneLineId) {
        OneLine oneLine = getOneLineById(oneLineId);
        User currentUser = userService.getCurrentUser();
        if (!oneLine.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_REQUEST);
        }
        return oneLine;
    }

    @Transactional(readOnly = true)
    public PaginatedResponseDto<OneLineRatingWithBookInfoUnitDto> searchOneLineFromReadingSpace(String keyword, Pageable pageable) {
        User user = userService.getCurrentUser();
        String escapedWord = escapeSpecialCharacters(keyword);
        Page<OneLine> oneLinePage = oneLineRepository.searchAllByOneLineContentOrBookInfoTitleOrAuthorByUserAndCreatedTimeDesc(escapedWord, user, pageable);
        Page<OneLineRatingWithBookInfoUnitDto> oneLineDtoPage = oneLinePage.map(OneLineRatingWithBookInfoUnitDto::new);
        return PaginatedResponseDto.from(oneLineDtoPage);
    }
}