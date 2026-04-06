package com.Midterm.stock.controller;

import com.Midterm.stock.dto.AssetDto;
import com.Midterm.stock.dto.UserDto;
import com.Midterm.stock.repository.UserDao;
import com.Midterm.stock.service.AssetPlannerAnalysisService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// 화면 이동 Controller
@Controller
public class PageController {

    @Autowired
    private AssetPlannerAnalysisService assetAnalysisService;

    @Autowired
    private UserDao userDao;

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

    // 이메일 찾기 화면
    @GetMapping("/findEmail")
    public String findEmailPage() {
        return "findEmail";
    }

    // 비밀번호 찾기 화면
    @GetMapping("findPassword")
    public String findPasswordPage() {
        return "findPassword";
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

    // 마이페이지 화면
    @GetMapping("/mypage")
    public String mypage(HttpSession session, Model model) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");

        if (loginNum == null) {
            return "redirect:/login";
        }

        UserDto user = userDao.getUserInfo(loginNum);

        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        model.addAttribute("currentPage", "mypage");

        return "mypage";
    }
}