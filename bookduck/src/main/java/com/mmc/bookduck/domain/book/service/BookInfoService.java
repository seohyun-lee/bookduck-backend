package com.mmc.bookduck.domain.book.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmc.bookduck.domain.book.dto.common.BookRatingUnitDto;
import com.mmc.bookduck.domain.book.dto.common.BookUnitParseDto;
import com.mmc.bookduck.domain.book.dto.common.MyRatingOneLineReadStatusDto;
import com.mmc.bookduck.domain.book.dto.request.CustomBookRequestDto;
import com.mmc.bookduck.domain.book.dto.request.CustomBookUpdateDto;
import com.mmc.bookduck.domain.book.dto.response.BookInfoAdditionalResponseDto;
import com.mmc.bookduck.domain.book.dto.response.BookUnitResponseDto;
import com.mmc.bookduck.domain.book.dto.request.UserBookRequestDto;
import com.mmc.bookduck.domain.book.dto.common.BookInfoDetailDto;
import com.mmc.bookduck.domain.book.dto.response.BookInfoBasicResponseDto;
import com.mmc.bookduck.domain.book.dto.response.BookListResponseDto;
import com.mmc.bookduck.domain.book.dto.response.CustomBookResponseDto;
import com.mmc.bookduck.domain.book.dto.common.CustomBookUnitDto;
import com.mmc.bookduck.domain.book.entity.BookInfo;
import com.mmc.bookduck.domain.book.entity.Genre;
import com.mmc.bookduck.domain.book.entity.GenreName;
import com.mmc.bookduck.domain.book.entity.UserBook;
import com.mmc.bookduck.domain.book.repository.BookInfoRepository;
import com.mmc.bookduck.domain.book.repository.UserBookRepository;
import com.mmc.bookduck.domain.oneline.entity.OneLine;
import com.mmc.bookduck.domain.oneline.repository.OneLineRepository;
import com.mmc.bookduck.domain.user.entity.User;
import com.mmc.bookduck.global.S3.S3Service;
import com.mmc.bookduck.domain.user.service.UserService;
import com.mmc.bookduck.global.exception.CustomException;
import com.mmc.bookduck.global.exception.ErrorCode;
import com.mmc.bookduck.global.google.GoogleBooksApiService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BookInfoService {
    private final BookInfoRepository bookInfoRepository;
    private final UserBookRepository userBookRepository;
    private final GenreService genreService;
    private final GoogleBooksApiService googleBooksApiService;
    private final UserService userService;
    private final OneLineRepository oneLineRepository;
    private final S3Service s3Service;


    // api 도서 목록 조회
    public BookListResponseDto<BookUnitResponseDto> searchBookList(String keyword, Long page, Long size) {
        User user = userService.getCurrentUser();
        String responseBody = googleBooksApiService.searchBookList(keyword, page, size);

        List<BookUnitParseDto> bookInfoList = parseBookInfo(responseBody);
        List<BookUnitResponseDto> bookResponseList = new ArrayList<>();
        for(BookUnitParseDto bookUnit : bookInfoList){
            BookInfo bookInfo = findBookInfoByProviderId(bookUnit.providerId())
                    .orElse(null);

            if(bookInfo != null){
                MyRatingOneLineReadStatusDto myRatingOneLine = getMyRatingOneLineReadStatus(bookInfo, user);
                BookUnitResponseDto responseDto = BookUnitResponseDto.from(bookUnit, myRatingOneLine);
                bookResponseList.add(responseDto);
            }
            else{
                BookUnitResponseDto responseDto = BookUnitResponseDto.from(bookUnit);
                bookResponseList.add(responseDto);
            }
        }
        return new BookListResponseDto<>(bookResponseList);
    }

    // 목록 정보 파싱
    public List<BookUnitParseDto> parseBookInfo(String apiResult){
        try{
            ObjectMapper objectMapper = new ObjectMapper();

            JsonNode rootNode = objectMapper.readTree(apiResult);
            JsonNode itemsNode = rootNode.path("items");

            List<BookUnitParseDto> bookList = new ArrayList<>();

            for(JsonNode itemNode : itemsNode) {
                // providerId
                String providerId = itemNode.get("id").asText();

                JsonNode info = itemNode.get("volumeInfo");
                // title
                String title = getTextNode(info, "title");
                // authors
                JsonNode authorsNode = info.get("authors");
                List<String> authors = new ArrayList<>();
                if (authorsNode != null && authorsNode.isArray()) {
                    for (JsonNode authorNode : authorsNode) {
                        authors.add(authorNode.asText());
                    }
                } else {
                    // authors가 없으면, null로 설정
                    authors = null;
                }
                // image
                String imgPath;
                JsonNode imageLink = info.get("imageLinks");
                if (imageLink != null && imageLink.has("thumbnail")) {
                    imgPath = imageLink.get("thumbnail").asText();
                } else if (imageLink != null && imageLink.has("smallThumbnail")){
                    imgPath = imageLink.get("smallThumbnail").asText();
                } else {
                    imgPath = null;
                }
                bookList.add(new BookUnitParseDto(title, authors, imgPath, providerId));
            }
            return bookList;

        }catch(Exception e){
            throw new CustomException(ErrorCode.JSON_PARSING_ERROR);
        }
    }

    private String getTextNode(JsonNode node, String fieldName) {
        if (node != null && node.has(fieldName)) {
            return node.get(fieldName).asText();
        }
        return null; // 필드가 없을 경우 null
    }

    //api 도서 기본 정보 조회
    public BookInfoBasicResponseDto getOneBookBasic(String providerId) {
        String responseBody = googleBooksApiService.searchOneBook(providerId);
        BookInfoDetailDto additional = parseBookDetail(responseBody);

        User user = userService.getCurrentUser();
        Optional<BookInfo> bookInfo = bookInfoRepository.findByProviderId(providerId);

        if(bookInfo.isPresent()){
            MyRatingOneLineReadStatusDto my = getMyRatingOneLineReadStatus(bookInfo.get(), user);
            Optional<UserBook> userBook = userBookRepository.findByUserAndBookInfo(user, bookInfo.get());
            if(userBook.isPresent()){
                return BookInfoBasicResponseDto.from(userBook.get(), getRatingAverage(bookInfo.get()), my.myOneLine(), additional);
            }else{
                return new BookInfoBasicResponseDto(bookInfo.get().getBookInfoId(), null, getRatingAverage(bookInfo.get()), null, 0.0, null, additional);
            }
        }
        else{
            return new BookInfoBasicResponseDto(null, null, null, null, 0.0, null, additional);
        }
    }

    // 기본 정보 파싱
    private BookInfoDetailDto parseBookDetail(String responseBody) {
        try{
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode info = rootNode.get("volumeInfo");

            // publisher
            String publisher = getTextNode(info, "publisher");
            // publishedDate
            String publishedDate = getTextNode(info, "publishedDate");
            // description
            String description = getTextNode(info, "description");
            // page
            Long page = info.get("pageCount").asLong(0);

            // categories
            JsonNode cateNode = info.get("categories");
            List<String> cate = new ArrayList<>();
            if (cateNode != null && cateNode.isArray()) {
                for (JsonNode c : cateNode) {
                    cate.add(c.asText());
                }
            } else {
                //카테고리가 없으면, null로 설정
                cate = null;
            }
            //장르 매칭
            Genre genre = genreService.matchGenre(cate);
            String koreanGenre = genreService.genreNameToKorean(genre);

            String language = getTextNode(info, "language");
            return new BookInfoDetailDto(publisher, publishedDate, description, page, cate, genre.getGenreId(), koreanGenre, language);

        }catch(Exception e){
            throw new CustomException(ErrorCode.JSON_PARSING_ERROR);
        }
    }

    // api bookInfo 저장
    public BookInfo saveApiBookInfo (UserBookRequestDto dto) {

        String saveAuthor = dto.authors().getFirst();
        Genre genre = genreService.findGenreById(dto.genreId());

        BookInfo bookInfo = dto.toEntity(saveAuthor,genre);
        return bookInfoRepository.save(bookInfo);
    }

    @Transactional(readOnly = true)
    public Optional<BookInfo> findBookInfoByProviderId(String providerId) {
        return bookInfoRepository.findByProviderId(providerId);
    }

    // custom bookInfo 삭제
    public void deleteCustomBookInfo(Long bookInfoId) {
        BookInfo bookInfo = bookInfoRepository.findById(bookInfoId)
                .orElseThrow(()-> new CustomException(ErrorCode.BOOKINFO_NOT_FOUND));
        if(bookInfo.getImgPath() != null){
            s3Service.deleteFile(bookInfo.getImgPath());
        }
        bookInfoRepository.delete(bookInfo);
    }

    public BookInfo saveCustomBookInfo (CustomBookRequestDto dto, User user) {
        String imgPath = null;
        if(dto.coverImage() != null){
            imgPath = s3Service.uploadFile(dto.coverImage());
        }
        Genre genre = genreService.findOrCreateGenreByGenreName(GenreName.valueOf("OTHERS"));
        BookInfo bookInfo = dto.toEntity(imgPath, genre, user.getUserId());
        return bookInfoRepository.save(bookInfo);
    }

    // custom book 목록 검색
    @Transactional(readOnly = true)
    public BookListResponseDto<CustomBookUnitDto> searchCustomBookList(String keyword, Long page, Long size) {

        User user = userService.getCurrentUser();
        List<BookInfo> bookInfoList = bookInfoRepository.searchByCreatedUserIdAndKeyword(user.getUserId(), keyword);

        List<CustomBookUnitDto> dtos = new ArrayList<>();
        for(BookInfo bookInfo : bookInfoList){
            MyRatingOneLineReadStatusDto dto = getMyRatingOneLineReadStatus(bookInfo, user);
            dtos.add(CustomBookUnitDto.from(bookInfo, dto));
        }
        return new BookListResponseDto<>(dtos);
    }


    //api도서 상세정보 - 추가정보
    @Transactional(readOnly = true)
    public BookInfoAdditionalResponseDto getApiBookAdditional(String providerId) {
        User user = userService.getCurrentUser();
        BookInfo bookInfo = bookInfoRepository.findByProviderId(providerId)
                .orElse(null);
        if(bookInfo == null){
            return new BookInfoAdditionalResponseDto(new ArrayList<>());
        }
        List<UserBook> sameBookInfo_userBookList = userBookRepository.findAllByBookInfoOrderByRatingDesc(bookInfo);

        List<BookRatingUnitDto> oneLineList = new ArrayList<>();
        if (!sameBookInfo_userBookList.isEmpty()) {
            for (UserBook book : sameBookInfo_userBookList) {
                //내 userBook 제외
                if (!book.getUser().equals(user)) {
                    Optional<OneLine> oneLine =  oneLineRepository.findByUserBook(book);
                    if(oneLine.isPresent()){
                        oneLineList.add(BookRatingUnitDto.from(oneLine.get(), book));
                    }
                    if(oneLineList.size() == 3){
                        break;
                    }
                }
            }
        }
        return new BookInfoAdditionalResponseDto(oneLineList);
    }

    // custom 기본 정보
    @Transactional(readOnly = true)
    public CustomBookResponseDto getCustomBookBasic(Long bookInfoId) {
        User user = userService.getCurrentUser();

        BookInfo bookInfo = bookInfoRepository.findById(bookInfoId)
                .orElseThrow(()-> new CustomException(ErrorCode.BOOKINFO_NOT_FOUND));
        UserBook userBook = userBookRepository.findByUserAndBookInfo(user, bookInfo)
                .orElseThrow(()-> new CustomException(ErrorCode.USERBOOK_NOT_FOUND));

        MyRatingOneLineReadStatusDto myRatingOneLine = getMyRatingOneLineReadStatus(bookInfo, user);
        return CustomBookResponseDto.from(userBook, myRatingOneLine.myRating(), myRatingOneLine.myOneLine());
    }

    @Transactional(readOnly = true)
    public Double getRatingAverage(BookInfo bookInfo) {
        double totalRating = 0.0;
        int count = 0;

        List<UserBook> userBookList = userBookRepository.findAllByBookInfo(bookInfo);
        if(userBookList.isEmpty()){
            return null;
        }

        for(UserBook book : userBookList){
            if (book.getRating() != 0.0) {
                totalRating += book.getRating();
                count++;
            }
        }

        if(count > 0){
            return (totalRating / count);
        }else{
            return null;
        }
    }

    @Transactional
    public CustomBookResponseDto updateCustomBookInfo(Long bookInfoId, CustomBookUpdateDto dto) {
        BookInfo bookInfo = bookInfoRepository.findById(bookInfoId)
                .orElseThrow(()-> new CustomException(ErrorCode.BOOKINFO_NOT_FOUND));
        User user = userService.getCurrentUser();

        if(bookInfo.getCreatedUserId() != null){
            if(bookInfo.getCreatedUserId().equals(user.getUserId())){
                if (dto.title() != null) {
                    bookInfo.setTitle(dto.title());
                }
                if (dto.author() != null) {
                    bookInfo.setAuthor(dto.author());
                }
                if (dto.pageCount() != null) {
                    bookInfo.setPageCount(dto.pageCount());
                }
                if (dto.publisher() != null) {
                    bookInfo.setPublisher(dto.publisher());
                }
                if(dto.coverImage() != null){
                    String newImgPath = s3Service.uploadFile(dto.coverImage());
                    s3Service.deleteFile(bookInfo.getImgPath());
                    bookInfo.setImgPath(newImgPath);
                }
            }else{
                throw new CustomException(ErrorCode.UNAUTHORIZED_REQUEST);
            }
        }else{
            throw new CustomException(ErrorCode.CUSTOM_BOOKINFO_NOT_FOUND);
        }

        UserBook userBook = userBookRepository.findByUserAndBookInfo(user, bookInfo)
                .orElseThrow(()-> new CustomException(ErrorCode.USERBOOK_NOT_FOUND));

        MyRatingOneLineReadStatusDto ratingDto = getMyRatingOneLineReadStatus(bookInfo, user);
        return CustomBookResponseDto.from(userBook, ratingDto.myRating(), ratingDto.myOneLine());
    }

    @Transactional(readOnly = true)
    public MyRatingOneLineReadStatusDto getMyRatingOneLineReadStatus(BookInfo bookInfo, User user) {
        UserBook userBook = userBookRepository.findByUserAndBookInfo(user, bookInfo)
                .orElse(null);
        if (userBook == null) {
            return new MyRatingOneLineReadStatusDto(null, null, null);
        } else {
            OneLine oneLineRating = oneLineRepository.findByUserBook(userBook)
                    .orElse(null);
            if (oneLineRating == null) {
                return new MyRatingOneLineReadStatusDto(userBook.getRating(), null, userBook.getReadStatus());
            } else {
                return new MyRatingOneLineReadStatusDto(userBook.getRating(), oneLineRating.getOneLineContent(), userBook.getReadStatus());
            }
        }
    }
}
