package com.Midterm.stock.controller;

import com.Midterm.stock.dto.CommunityCommentDto;
import com.Midterm.stock.dto.CommunityDto;

import com.Midterm.stock.repository.community.CommunityBoardDao;
import com.Midterm.stock.repository.community.CommunityCommentDao;
import com.Midterm.stock.repository.community.CommunityLikeDao;
import com.Midterm.stock.repository.community.CommunityTagDao;
import com.Midterm.stock.repository.community.CommunityExtraDao;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/community")
public class CommunityController {

    @Autowired
    private CommunityBoardDao communityBoardDao;

    @Autowired
    private CommunityCommentDao communityCommentDao;

    @Autowired
    private CommunityLikeDao communityLikeDao;

    @Autowired
    private CommunityExtraDao communityExtraDao;

    @Autowired
    private CommunityTagDao communityTagDao;

    // 게시글 목록
    @GetMapping({"", "/"})
    public String communityList(@RequestParam(value = "page", defaultValue = "1") int page,
                                @RequestParam(value = "category", required = false) String category,
                                @RequestParam(value = "keyword", required = false) String keyword,
                                @RequestParam(value = "theme", required = false) String theme,
                                Model model,
                                HttpSession session) {
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null) {
            return "redirect:/login";
        }

        int pageSize = 3;
        int start = (page - 1) * pageSize + 1;
        int end = page * pageSize;

        ArrayList<CommunityDto> lists;
        int totalCount;

        String categoryParam = category;
        String themeParam = theme;

        boolean isPopularView = "popular".equals(categoryParam);

        String categoryName = convertCategoryParamToName(categoryParam);
        String themeName = convertCategoryParamToName(themeParam);

        String searchKeyword = keyword;
        if (searchKeyword != null) {
            searchKeyword = searchKeyword.trim();
            if (searchKeyword.startsWith("#")) {
                searchKeyword = searchKeyword.substring(1).trim();
            }
        }

        boolean hasCategory = categoryName != null && !categoryName.isBlank() && !categoryName.equals("전체") && !isPopularView;
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (isPopularView) {
            lists = communityBoardDao.getPopularArticles(themeName, keyword, start, end);
            totalCount = communityBoardDao.getPopularArticleCount(themeName, keyword);
        } else if (hasCategory && hasKeyword) {
            lists = communityBoardDao.getArticlesByCategoryAndKeyword(categoryName, keyword, start, end);
            totalCount = communityBoardDao.getArticleCountByCategoryAndKeyword(categoryName, keyword);
        } else if (hasCategory) {
            lists = communityBoardDao.getArticlesByCategory(categoryName, start, end);
            totalCount = communityBoardDao.getArticleCountByCategory(categoryName);
        } else if (hasKeyword) {
            lists = communityBoardDao.searchArticles(keyword, start, end);
            totalCount = communityBoardDao.getArticleCountByKeyword(keyword);
        } else {
            lists = communityBoardDao.getArticles(start, end);
            totalCount = communityBoardDao.getArticleCount();
        }

        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        ArrayList<Map<String, Object>> popularCategories = communityExtraDao.getPopularThemeCategories();
        for (Map<String, Object> item : popularCategories) {
            String categoryNameFromDb = (String) item.get("category");
            item.put("categoryKey", convertCategoryNameToParam(categoryNameFromDb));
        }

        ArrayList<CommunityDto> featuredPosts = (!isPopularView && !hasCategory && !hasKeyword)
                ? communityBoardDao.getFeaturedArticles(2)
                : new ArrayList<>();

        model.addAttribute("lists", lists);
        model.addAttribute("selectedCategory", categoryParam);
        model.addAttribute("currentPage", "community");
        model.addAttribute("keyword", keyword);
        model.addAttribute("theme", themeParam);
        model.addAttribute("page", page);
        model.addAttribute("currentPageNum", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("popularCategories", popularCategories);
        model.addAttribute("featuredPosts", featuredPosts);
        model.addAttribute("isPopularView", isPopularView);

        return "community/list";
    }

    // 글쓰기 화면
    @GetMapping("/insert")
    public String insertForm(HttpSession session, Model model) {
        CommunityDto dto = new CommunityDto();

        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            return "redirect:/login";
        }

        dto.setUser_num(loginNum);

        model.addAttribute("currentPage", "community");
        model.addAttribute("dto", dto);
        return "community/insert";
    }

    // 관련 뉴스 검색
    @GetMapping("/news/search")
    @ResponseBody
    public ArrayList<Map<String, String>> searchRelatedNews(@RequestParam("keyword") String keyword,
                                                            HttpSession session) {
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null) {
            return new ArrayList<>();
        }

        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        if (trimmedKeyword.length() < 2) {
            return new ArrayList<>();
        }

        return communityExtraDao.searchRelatedNews(trimmedKeyword);
    }

    // 글 작성 처리
    @PostMapping("/insert")
    public String insertProc(CommunityDto dto, HttpSession session) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            return "redirect:/login";
        }

        dto.setUser_num(loginNum);

        if (dto.getTagNames() != null) {
            dto.setTagNames(dto.getTagNames().trim());
        }

        int result = communityBoardDao.insertArticle(dto);

        if (result > 0) {
            return "redirect:/community";
        } else {
            return "community/insert";
        }
    }

    // 글 상세보기
    @GetMapping("/detail")
    public String detailProc(@RequestParam("board_id") int board_id,
                             HttpSession session,
                             Model model) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            return "redirect:/login";
        }

        communityBoardDao.updateViewcount(board_id);

        CommunityDto dto = communityBoardDao.getArticle(board_id);

        if (dto == null) {
            return "redirect:/community";
        }

        int userArticleCount = communityBoardDao.getArticleCountByUserNum(dto.getUser_num());
        int userCommentCount = communityCommentDao.getCommentCountByUserNum(dto.getUser_num());

        boolean isOwner = dto.getUser_num() == loginNum;

        String relatedNewsTitle = null;
        if (dto.getNews_link() != null && !dto.getNews_link().isBlank()) {
            relatedNewsTitle = communityExtraDao.getNewsTitleByLink(dto.getNews_link());
        }

        boolean likedByMe = communityLikeDao.existsLike(board_id, loginNum);
        ArrayList<CommunityCommentDto> comments = communityCommentDao.getCommentsByBoardId(board_id);

        model.addAttribute("dto", dto);
        model.addAttribute("like" +
                "dByMe", likedByMe);
        model.addAttribute("comments", comments);
        model.addAttribute("commentCount", comments.size());
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("currentPage", "community");
        model.addAttribute("userArticleCount", userArticleCount);
        model.addAttribute("userCommentCount", userCommentCount);
        model.addAttribute("relatedNewsTitle", relatedNewsTitle);

        return "community/detail";
    }

    // 댓글 등록
    @PostMapping("/comment/insert")
    public String insertComment(@RequestParam("board_id") int board_id,
                                @RequestParam("content") String content,
                                HttpSession session) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            return "redirect:/login";
        }

        String trimmedContent = content == null ? "" : content.trim();
        if (!trimmedContent.isEmpty()) {
            communityCommentDao.insertComment(board_id, loginNum, trimmedContent);
        }

        return "redirect:/community/detail?board_id=" + board_id;
    }

    // 좋아요 처리
    @PostMapping("/like")
    @ResponseBody
    public Map<String, Object> likeArticle(@RequestParam("board_id") int board_id,
                                           HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return result;
        }

        boolean liked = communityLikeDao.existsLike(board_id, loginNum);

        if (liked) {
            communityLikeDao.deleteLike(board_id, loginNum);
            communityLikeDao.decreaseLikeCount(board_id);
            result.put("liked", false);
        } else {
            communityLikeDao.insertLike(board_id, loginNum);
            communityLikeDao.increaseLikeCount(board_id);
            result.put("liked", true);
        }

        int likeCount = communityLikeDao.getLikeCount(board_id);

        result.put("success", true);
        result.put("likeCount", likeCount);
        return result;
    }

    // 글 삭제
    @PostMapping("/delete")
    public String deleteProc(@RequestParam("board_id") int board_id,
                             HttpSession session) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            return "redirect:/login";
        }

        CommunityDto dto = communityBoardDao.getArticle(board_id);

        if (dto == null) {
            return "redirect:/community";
        }

        if (dto.getUser_num() != loginNum) {
            return "redirect:/community/detail?board_id=" + board_id;
        }

        communityBoardDao.deleteArticle(board_id);
        return "redirect:/community";
    }

    // 수정 화면
    @GetMapping("/update")
    public String updateForm(@RequestParam("board_id") int board_id,
                             HttpSession session,
                             Model model) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            return "redirect:/login";
        }

        CommunityDto dto = communityBoardDao.getArticle(board_id);

        if (dto == null) {
            return "redirect:/community";
        }

        if (dto.getUser_num() != loginNum) {
            return "redirect:/community/detail?board_id=" + board_id;
        }

        model.addAttribute("dto", dto);
        model.addAttribute("currentPage", "community");
        return "community/update";
    }

    // 수정 처리
    @PostMapping("/update")
    public String updateProc(CommunityDto dto, HttpSession session) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            return "redirect:/login";
        }

        CommunityDto origin = communityBoardDao.getArticle(dto.getBoard_id());

        if (origin == null) {
            return "redirect:/community";
        }

        if (origin.getUser_num() != loginNum) {
            return "redirect:/community/detail?board_id=" + dto.getBoard_id();
        }

        if (dto.getTagNames() != null) {
            dto.setTagNames(dto.getTagNames().trim());
        }

        communityBoardDao.updateArticle(dto);
        return "redirect:/community/detail?board_id=" + dto.getBoard_id();
    }

    // 카테고리 파라미터를 한글명으로 변환
    private String convertCategoryParamToName(String category) {
        if ("free".equals(category)) {
            return "자유게시판";
        } else if ("popular".equals(category)) {
            return "인기글";
        } else if ("beginner".equals(category)) {
            return "초보질문";
        } else if ("semiconductor".equals(category)) {
            return "반도체·AI";
        } else if ("battery".equals(category)) {
            return "2차전지";
        } else if ("bio".equals(category)) {
            return "제약·바이오";
        } else if ("finance".equals(category)) {
            return "금융·밸류업";
        } else if ("defense".equals(category)) {
            return "방산·우주항공";
        } else if ("platform".equals(category)) {
            return "IT·플랫폼";
        } else if ("entertainment".equals(category)) {
            return "엔터·미디어";
        } else if ("mobility".equals(category)) {
            return "자동차·모빌리티";
        }
        return category;
    }

    // 카테고리 한글명을 파라미터 값으로 변환
    private String convertCategoryNameToParam(String categoryName) {
        if ("자유게시판".equals(categoryName)) {
            return "free";
        } else if ("인기글".equals(categoryName)) {
            return "popular";
        } else if ("초보질문".equals(categoryName)) {
            return "beginner";
        } else if ("반도체·AI".equals(categoryName)) {
            return "semiconductor";
        } else if ("2차전지".equals(categoryName)) {
            return "battery";
        } else if ("제약·바이오".equals(categoryName)) {
            return "bio";
        } else if ("금융·밸류업".equals(categoryName)) {
            return "finance";
        } else if ("방산·우주항공".equals(categoryName)) {
            return "defense";
        } else if ("IT·플랫폼".equals(categoryName)) {
            return "platform";
        } else if ("엔터·미디어".equals(categoryName)) {
            return "entertainment";
        } else if ("자동차·모빌리티".equals(categoryName)) {
            return "mobility";
        }
        return "";
    }
}
