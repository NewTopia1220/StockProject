package com.Midterm.stock.controller;

import com.Midterm.stock.dto.CommunityDto;
import com.Midterm.stock.repository.CommunityDao;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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
    @GetMapping("/writeForm")
    public String writeForm(HttpSession session, Model model){
        CommunityDto dto = new CommunityDto();
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null){
            return "redirect:/login";
        }

        model.addAttribute("currentPage", "community");
        model.addAttribute("dto", dto);
        return "community/writeForm";
    }
}
