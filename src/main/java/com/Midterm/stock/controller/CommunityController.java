package com.Midterm.stock.controller;

import com.Midterm.stock.dto.CommunityDto;
import com.Midterm.stock.repository.CommunityDao;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;

// getArticles(): 목록 p => 상세 => 글쓰기 / 수정 / 처리
@Controller
@RequestMapping("/community")
public class CommunityController {

    @Autowired
    private CommunityDao communityDao;

    // 게시글 목록
    @GetMapping({"", "/"})
    public String communityList(@RequestParam(value="page", defaultValue="1") int page,
                                Model model,
                                HttpSession session){
        String loginUser = (String) session.getAttribute("loginUser");

        // 로그인 X -> 로그인 화면으로
        if (loginUser == null){
            return "redirect:/login";
        }

        int pageSize = 5;
        int start = (page - 1) * pageSize + 1;
        int end = page * pageSize;

        ArrayList<CommunityDto> lists = communityDao.getArticles(start, end);

        model.addAttribute("lists", lists);
        model.addAttribute("currentPage", "community");
        model.addAttribute("page", page);

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

        model.addAttribute("dto", dto);
        model.addAttribute("currentPage", "community");

        return "community/detail";
    }
}
