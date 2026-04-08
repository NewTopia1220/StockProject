package com.Midterm.stock.controller;

import com.Midterm.stock.service.stock.StockPriceService;
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
    private StockPriceService stockPriceService;

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

    /**
     * 주식 메인 페이지
     * GET /stock?code=005930
     * - 페이지 첫 로드 시 서버사이드 렌더링으로 초기 데이터 포함
     * - 이후 데이터는 stock.js에서 폴링으로 업데이트
     * - 비로그인 시 /login 리다이렉트
     *
     * @param code 초기 표시 종목코드 (기본값: 005930 삼성전자)
     */
    @GetMapping("/stock")
    public String stockPage(@RequestParam(defaultValue = "005930") String code,
                            HttpSession session,
                            Model model) {
        String loginUser = (String) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        // 초기 렌더링용 데이터 (JS 폴링 전 빈 화면 방지)
        model.addAttribute("stockInfo", stockPriceService.getCurrentPrice(code));
        model.addAttribute("chartData", stockPriceService.getDailyPrice(code));
        model.addAttribute("kospiInfo", stockPriceService.getKospiIndex());
        model.addAttribute("kosdaqInfo", stockPriceService.getKosdaqIndex());
        model.addAttribute("stockCode", code);

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