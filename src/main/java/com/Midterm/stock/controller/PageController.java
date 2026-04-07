package com.Midterm.stock.controller;

import com.Midterm.stock.dto.UserDto;
import com.Midterm.stock.repository.NewsDao;
import com.Midterm.stock.repository.UserDao;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 화면 이동 Controller
@Controller
public class PageController {

    @Autowired
    private UserDao userDao;

    @Autowired
    private NewsDao newsDao;

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

    // 메인 화면 (stock)
    @GetMapping("/stock")
    public String stockPage(HttpSession session, Model model) {
        String loginUser = (String) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        // AI 뉴스 종합 분석 데이터
        Map<String, Object> analysis = newsDao.getTodayAnalysis();

        int totalCount    = (int) analysis.getOrDefault("totalCount", 0);
        double typeProb   = (double) analysis.getOrDefault("avgTypeProb", 0.0);
        double noiseProb  = (double) analysis.getOrDefault("avgClickbaitProb", 0.0);
        int posCount      = (int) analysis.getOrDefault("positiveCount", 0);
        int negCount      = (int) analysis.getOrDefault("negativeCount", 0);

        // 시장 감성 0~100 (긍정 비율)
        double sentimentScore = 50.0;
        String sentimentLabel = "중립";
        String statusBadge    = "중립";
        String analysisDesc   = "시장은 중립적인 흐름을 보이고 있습니다. 종목별 선택적 접근이 유효합니다.";

        if (totalCount > 0) {
            sentimentScore = Math.round((double) posCount / totalCount * 100.0 * 10) / 10.0;
            double negScore = Math.round((double) negCount / totalCount * 100.0 * 10) / 10.0;

            if (sentimentScore >= 60) {
                sentimentLabel = "긍정";
                statusBadge    = "강세";
                analysisDesc   = "현재 시장은 전반적으로 긍정적인 분위기입니다. 호재성 뉴스가 우세하여 투자 심리가 활발합니다.";
            } else if (negScore >= 60) {
                sentimentLabel = "부정";
                statusBadge    = "약세";
                analysisDesc   = "현재 시장은 부정적인 흐름을 보이고 있습니다. 악재성 뉴스가 많아 신중한 접근이 필요합니다.";
            } else if (noiseProb >= 50) {
                statusBadge  = "주의";
                analysisDesc = "정보 노이즈(낚시성 기사)가 높게 감지됩니다. 투자 정보를 신중하게 선별하세요.";
            }
        }

        // 전광판용 종목
        List<Map<String, Object>> tickerCompanies = newsDao.getTodayTickerCompanies();

        // 하단 섹터→종목 카드
        LinkedHashMap<String, List<Map<String, Object>>> sectorCompanyMap = newsDao.getSectorCompanyMap();

        model.addAttribute("totalCount",     totalCount);
        model.addAttribute("typeProb",       String.format("%.1f", typeProb));
        model.addAttribute("noiseProb",      String.format("%.1f", noiseProb));
        model.addAttribute("sentimentScore", sentimentScore);
        model.addAttribute("sentimentLabel", sentimentLabel);
        model.addAttribute("statusBadge",    statusBadge);
        model.addAttribute("analysisDesc",   analysisDesc);
        model.addAttribute("tickerCompanies",  tickerCompanies);
        model.addAttribute("sectorCompanyMap", sectorCompanyMap);
        model.addAttribute("currentPage",    "stock");

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
