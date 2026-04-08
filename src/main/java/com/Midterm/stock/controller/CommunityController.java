package com.Midterm.stock.controller;

import com.Midterm.stock.dto.CommunityDto;
import com.Midterm.stock.dto.CommunityCommentDto;
import com.Midterm.stock.repository.CommunityDao;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// getArticles(): 목록 p => 상세 => 글쓰기 / 수정 / 처리
@Controller
@RequestMapping("/community")
public class CommunityController {

    @Autowired
    private CommunityDao communityDao;

    // 게시글 목록
    @GetMapping({"", "/"})
    public String communityList(@RequestParam(value="page", defaultValue="1") int page,
                                @RequestParam(value="category", required = false) String category,
                                @RequestParam(value="keyword", required = false) String keyword,
                                Model model,
                                HttpSession session){
        String loginUser = (String) session.getAttribute("loginUser");

        // 로그인 X -> 로그인 화면으로
        if (loginUser == null){
            return "redirect:/login";
        }

        if ("free".equals(category)) {
            category = "자유게시판";
        } else if ("topic".equals(category)) {
            category = "종목토론";
        } else if ("beginner".equals(category)) {
            category = "초보질문";
        } else if ("semiconductor".equals(category)) {
            category = "반도체·AI";
        } else if ("battery".equals(category)) {
            category = "2차전지";
        } else if ("bio".equals(category)) {
            category = "제약·바이오";
        } else if ("finance".equals(category)) {
            category = "금융·밸류업";
        } else if ("defense".equals(category)) {
            category = "방산·우주항공";
        } else if ("platform".equals(category)) {
            category = "IT·플랫폼";
        } else if ("entertainment".equals(category)) {
            category = "엔터·미디어";
        } else if ("mobility".equals(category)) {
            category = "자동차·모빌리티";
        }

        int pageSize = 4;
        int start = (page - 1) * pageSize + 1;
        int end = page * pageSize;

        ArrayList<CommunityDto> lists;
        int totalCount;

        boolean hasCategory = category != null && !category.isBlank() && !category.equals("전체");
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasCategory && hasKeyword) {
            lists = communityDao.getArticlesByCategoryAndKeyword(category, keyword, start, end);
            totalCount = communityDao.getArticleCountByCategoryAndKeyword(category, keyword);
        } else if (hasCategory) {
            lists = communityDao.getArticlesByCategory(category, start, end);
            totalCount = communityDao.getArticleCountByCategory(category);
        } else if (hasKeyword) {
            lists = communityDao.searchArticles(keyword, start, end);
            totalCount = communityDao.getArticleCountByKeyword(keyword);
        } else {
            lists = communityDao.getArticles(start, end);
            totalCount = communityDao.getArticleCount();
        }

        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        model.addAttribute("lists", lists);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("currentPage", "community");
        model.addAttribute("keyword", keyword);
        model.addAttribute("page", page);
        model.addAttribute("currentPageNum", page);
        model.addAttribute("totalPages", totalPages);

        return "community/list";
    }

    // 글쓰기 화면
    @GetMapping("/insert")
    public String insertForm(HttpSession session, Model model){
        CommunityDto dto = new CommunityDto();

        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null){
            return "redirect:/login";
        }

        dto.setUser_num(loginNum);  // 실제 저장용 작성자 번호

        model.addAttribute("currentPage", "community");
        model.addAttribute("dto", dto);
        return "community/insert";
    }

    // 글 작성
    @PostMapping("/insert")
    public String insertProc(CommunityDto dto, HttpSession session){
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null){
            return "redirect:/login";
        }

        dto.setUser_num(loginNum);  // 실제 저장용 작성자 번호

        int result = communityDao.insertArticle(dto);

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
                             Model model){
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null){
            return "redirect:/login";
        }

        communityDao.updateViewcount(board_id);  // 조회수 증가

        CommunityDto dto = communityDao.getArticle(board_id);
        if (dto == null) {
            return "redirect:/community";
        }

        boolean likedByMe = communityDao.existsLike(board_id, loginNum);
        ArrayList<CommunityCommentDto> comments = communityDao.getCommentsByBoardId(board_id);

        model.addAttribute("dto", dto);
        model.addAttribute("likedByMe", likedByMe);
        model.addAttribute("comments", comments);
        model.addAttribute("commentCount", comments.size());
        model.addAttribute("currentPage", "community");

        return "community/detail";
    }

    @PostMapping("/comment/insert")
    public String insertComment(@RequestParam("board_id") int board_id,
                                @RequestParam("content") String content,
                                HttpSession session) {

        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            return "redirect:/login";
        }

        // content가 null => 빈 문자열 처리, 아니면 앞뒤 공백 제거
        String trimmedContent = content == null ? "" : content.trim();
        // 공백 제거 후에도 내용이 비어있지 않을 때만 저장 
        if (!trimmedContent.isEmpty()) {
            communityDao.insertComment(board_id, loginNum, trimmedContent);
        }

        return "redirect:/community/detail?board_id=" + board_id;
    }

    // 좋아요 
    @PostMapping("/like")
    @ResponseBody
    public Map<String, Object> likeArticle(@RequestParam("board_id") int board_id,
                                           HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        // 로그인 정보 X
        if (loginUser == null || loginNum == null) {
            result.put("success", false);  // 처리 실패 
            result.put("message", "로그인이 필요합니다.");
            return result;
        }

        // 좋아요가 이미 눌렸는지 확인 
        boolean liked = communityDao.existsLike(board_id, loginNum);
        
        if (liked) {  // 이미 눌림 
            communityDao.deleteLike(board_id, loginNum);  // deleteLike
            communityDao.decreaseLikeCount(board_id);     // decrease
            result.put("liked", false);
        } else { // 아직 안 눌림 
            communityDao.insertLike(board_id, loginNum);  // insert
            communityDao.increaseLikeCount(board_id);     // increase
            result.put("liked", true);
        }

        // 최종 개수 다시 조회
        int likeCount = communityDao.getLikeCount(board_id);

        result.put("success", true);
        result.put("likeCount", likeCount);
        return result;
    }
}
