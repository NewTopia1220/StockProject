package com.Midterm.stock.controller;

import com.Midterm.stock.dto.AssetAnalysisDto;
import com.Midterm.stock.service.StockService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 화면 이동 Controller
@Controller
public class PageController {

    @Autowired
    private StockService stockService;

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
    public String stockPage(@RequestParam(defaultValue = "005930") String code,
                            HttpSession session,
                            Model model) {
        String loginUser = (String) session.getAttribute("loginUser");

        // 페이지 로드시 ticker 데이터 포함
        model.addAttribute("stockInfo", stockService.getCurrentPrice(code));
        model.addAttribute("chartData", stockService.getDailyPrice(code));
        model.addAttribute("kospiInfo", stockService.getKospiIndex());
        model.addAttribute("kosdaqInfo", stockService.getKosdaqIndex());
        model.addAttribute("stockCode", code);

        if (loginUser == null) {
            return "redirect:/login";
        }

        return "stock";
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