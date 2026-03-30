package com.Midterm.stock.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 화면 이동 Controller
@Controller
public class PageController {

    // 로그인 화면
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    // 회원가입 화면
    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    // 메인 화면
    @GetMapping("/stock")
    public String stockPage(HttpSession session) {
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null) {
            return "redirect:/login";
        }

        return "stock";
    }

    // 자산 관리 화면
    @GetMapping("/asset")
    public String assetPage(HttpSession session){
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null) {
            return "redirect:/login";
        }

        return "asset";
    }

    // 마이페이지
    @GetMapping("/mypage")
    public String mypage(HttpSession session){
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null) {
            return "redirect:/login";
        }

        return "mypage";
    }
}