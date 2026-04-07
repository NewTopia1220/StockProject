package com.Midterm.stock.controller;

import com.Midterm.stock.dto.UserDto;
import com.Midterm.stock.repository.NewsDao;
import com.Midterm.stock.repository.UserDao;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

@Controller
public class PageController {

    // 고정 8섹터 순서 정의 (DB 섹터명과 매핑)
    private static final List<String[]> FIXED_SECTORS = Arrays.asList(
        new String[]{"IT/반도체",      "반도체·AI"},
        new String[]{"2차전지",         "2차전지"},
        new String[]{"제약/바이오",          "제약/바이오"},
        new String[]{"자동차/모빌리티", "자동차·모빌리티"},
        new String[]{"IT/플랫폼",       "IT·플랫폼"},
        new String[]{"금융/밸류업",     "금융·밸류업"},
        new String[]{"방산/우주항공",   "방산·우주항공"},
        new String[]{"엔터/미디어",     "엔터·미디어"}
    );

    // 섹터 아이콘
    private static final Map<String, String> SECTOR_ICON;
    static {
        SECTOR_ICON = new HashMap<>();
        SECTOR_ICON.put("IT/반도체",      "💻");
        SECTOR_ICON.put("2차전지",        "🔋");
        SECTOR_ICON.put("바이오",         "💊");
        SECTOR_ICON.put("자동차/모빌리티","🚗");
        SECTOR_ICON.put("IT/플랫폼",      "🌐");
        SECTOR_ICON.put("금융/밸류업",    "💰");
        SECTOR_ICON.put("방산/우주항공",  "🚀");
        SECTOR_ICON.put("엔터/미디어",    "🎬");
    }

    @Autowired private UserDao userDao;
    @Autowired private NewsDao newsDao;

    @GetMapping("/login")    public String loginPage()    { return "login"; }
    @GetMapping("/register") public String registerPage() { return "register"; }
    @GetMapping("/findEmail")public String findEmailPage(){ return "findEmail"; }
    @GetMapping("findPassword") public String findPasswordPage(){ return "findPassword"; }

    @GetMapping("/stock")
    public String stockPage(HttpSession session, Model model) {
        if (session.getAttribute("loginUser") == null) return "redirect:/login";

        // AI 종합 분석
        Map<String, Object> analysis = newsDao.getTodayAnalysis();
        int totalCount   = (int)    analysis.getOrDefault("totalCount",       0);
        double typeProb  = (double) analysis.getOrDefault("avgTypeProb",      0.0);
        double noiseProb = (double) analysis.getOrDefault("avgClickbaitProb", 0.0);
        int posCount     = (int)    analysis.getOrDefault("positiveCount",    0);
        int negCount     = (int)    analysis.getOrDefault("negativeCount",    0);

        double sentimentScore = 50.0;
        String sentimentLabel = "중립", statusBadge = "중립";
        String analysisDesc = "시장은 중립적인 흐름입니다. 종목별 선택적 접근이 유효합니다.";

        if (totalCount > 0) {
            sentimentScore = Math.round((double)posCount / totalCount * 100.0 * 10) / 10.0;
            double negScore = Math.round((double)negCount / totalCount * 100.0 * 10) / 10.0;
            if (sentimentScore >= 60) {
                sentimentLabel = "긍정"; statusBadge = "강세";
                analysisDesc = "현재 시장은 전반적으로 긍정적입니다. 호재성 뉴스가 우세하여 투자 심리가 활발합니다.";
            } else if (negScore >= 60) {
                sentimentLabel = "부정"; statusBadge = "약세";
                analysisDesc = "현재 시장은 부정적인 흐름입니다. 악재성 뉴스가 많아 신중한 접근이 필요합니다.";
            } else if (noiseProb >= 50) {
                statusBadge = "주의";
                analysisDesc = "정보 노이즈(낚시성 기사)가 높게 감지됩니다. 투자 정보를 신중히 선별하세요.";
            }
        }

        // DB 섹터레벨 집계
        LinkedHashMap<String, Map<String, Object>> dbSectorMap = newsDao.getSectorLevelMap();

        // 고정 8섹터 카드 생성
        List<Map<String, Object>> sectorCards = new ArrayList<>();
        for (String[] s : FIXED_SECTORS) {
            String dbKey  = s[0]; // DB 섹터명
            String dispKey = s[1]; // 화면 표시명

            Map<String, Object> dbData = findSectorData(dbSectorMap, dbKey);
            int articleCount = dbData != null ? (int)dbData.get("articleCount") : 0;
            int pos = dbData != null ? (int)dbData.get("positiveCount") : 0;
            int neg = dbData != null ? (int)dbData.get("negativeCount") : 0;
            int neu = dbData != null ? (int)dbData.get("neutralCount")  : 0;
            double atp = dbData != null ? (double)dbData.get("avgTypeProb") : 0.0;

            String dominant = articleCount == 0 ? "없음"
                : (pos>=neg && pos>=neu) ? "호재"
                : (neg>=pos && neg>=neu) ? "악재" : "중립";
            int posRatio = articleCount > 0 ? (int)Math.round((double)pos/articleCount*100) : 0;

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("sectorKey",    dbKey);
            card.put("sectorName",   dispKey);
            card.put("icon",         SECTOR_ICON.getOrDefault(dbKey, "📈"));
            card.put("articleCount", articleCount);
            card.put("positiveCount",pos);
            card.put("negativeCount",neg);
            card.put("neutralCount", neu);
            card.put("dominant",     dominant);
            card.put("posRatio",     posRatio);
            card.put("avgTypeProb",  String.format("%.1f", atp));
            sectorCards.add(card);
        }

        List<Map<String, Object>> tickerCompanies = newsDao.getTodayTickerCompanies();

        model.addAttribute("totalCount",      totalCount);
        model.addAttribute("typeProb",        String.format("%.1f", typeProb));
        model.addAttribute("noiseProb",       String.format("%.1f", noiseProb));
        model.addAttribute("sentimentScore",  sentimentScore);
        model.addAttribute("sentimentLabel",  sentimentLabel);
        model.addAttribute("statusBadge",     statusBadge);
        model.addAttribute("analysisDesc",    analysisDesc);
        model.addAttribute("tickerCompanies", tickerCompanies);
        model.addAttribute("sectorCards",     sectorCards);
        model.addAttribute("currentPage",     "stock");
        return "stock";
    }

    private Map<String, Object> findSectorData(
            LinkedHashMap<String, Map<String, Object>> dbMap, String key) {
        if (dbMap.containsKey(key)) return dbMap.get(key);
        // 부분 매칭 (DB 섹터명이 약간 다를 수 있음)
        String kn = key.replace("/","").replace(" ","").toLowerCase();
        for (Map.Entry<String, Map<String, Object>> e : dbMap.entrySet()) {
            String dn = e.getKey().replace("/","").replace(" ","").toLowerCase();
            if (dn.contains(kn) || kn.contains(dn)) return e.getValue();
        }
        return null;
    }

    @GetMapping("/mypage")
    public String mypage(HttpSession session, Model model) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        if (loginNum == null) return "redirect:/login";
        UserDto user = userDao.getUserInfo(loginNum);
        if (user == null) return "redirect:/login";
        model.addAttribute("user", user);
        model.addAttribute("currentPage", "mypage");
        return "mypage";
    }
}
