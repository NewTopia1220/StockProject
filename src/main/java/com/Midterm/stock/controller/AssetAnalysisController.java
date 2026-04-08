package com.Midterm.stock.controller;

import com.Midterm.stock.dto.AssetAnalysisDto;
import com.Midterm.stock.service.AssetAnalysisService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AssetAnalysisController {

    @Autowired
    private AssetAnalysisService assetAnalysisService;

    @GetMapping("/asset")
    public String assetForm(HttpSession session, Model model) {
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null) {
            return "redirect:/login";
        }

        if (!model.containsAttribute("assetDto")) {
            AssetAnalysisDto dto = new AssetAnalysisDto();
            dto.setCurrentAsset(0);
            dto.setMonthlyIncome(0);
            dto.setMonthlyExpense(0);
            dto.setGoalAmount(0);
            dto.setGoalMonths(12);
            dto.setExpectedReturn(0.0);
            dto.setAge(20);
            dto.setJobType("student");
            dto.setRiskPreference("low");
            model.addAttribute("assetDto", dto);
        }

        model.addAttribute("historyList", assetAnalysisService.getHistory());
        return "asset";
    }

    @PostMapping("/asset/analyze")
    public String assetAnalyze(@ModelAttribute("assetDto") AssetAnalysisDto assetDto,
                               HttpSession session,
                               Model model) {
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null) {
            return "redirect:/login";
        }

        try {
            // 예상 수익률을 화면에서 받지 않는다면 기본 0으로 고정
            assetDto.setExpectedReturn(0.0);

            AssetAnalysisDto resultDto = assetAnalysisService.analyzeAndSave(assetDto);

            model.addAttribute("assetDto", resultDto);
            model.addAttribute("historyList", assetAnalysisService.getHistory());
            return "asset";

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("assetDto", assetDto);
            model.addAttribute("historyList", assetAnalysisService.getHistory());
            model.addAttribute("errorMessage", "자산 분석 중 오류가 발생했습니다. " + e.getMessage());
            return "asset";
        }
    }
}