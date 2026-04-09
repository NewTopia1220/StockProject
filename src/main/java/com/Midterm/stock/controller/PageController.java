package com.Midterm.stock.controller;

import com.Midterm.stock.dto.AiPredictionDto;
import com.Midterm.stock.dto.UserDto;
import com.Midterm.stock.repository.NewsDao;
import com.Midterm.stock.repository.UserDao;
import com.Midterm.stock.service.stock.ExchangeService;
import com.Midterm.stock.service.stock.StockAiService;
import com.Midterm.stock.service.stock.StockPriceService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PageController {

    @Autowired
    private StockPriceService stockPriceService;

    @Autowired
    private ExchangeService exchangeService;

    @Autowired
    private StockAiService stockAiService;

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
                            HttpSession session, Model model) {
        String loginUser = (String) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        // 초기 렌더링용 데이터 (JS 폴링 전 빈 화면 방지)
        model.addAttribute("stockInfo", stockPriceService.getCurrentPrice(code));
        model.addAttribute("chartData", stockPriceService.getDailyPrice(code));
        model.addAttribute("kospiInfo", stockPriceService.getKospiIndex());
        model.addAttribute("kosdaqInfo", stockPriceService.getKosdaqIndex());
        model.addAttribute("exchangeInfo", exchangeService.getExchangeRate("USD"));
        model.addAttribute("stockCode", code);


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
            double acp = dbData != null ? (double)dbData.get("avgClickbaitProb") : 0.0;

            String dominant = articleCount == 0 ? "없음"
                : (pos>=neg && pos>=neu) ? "호재"
                : (neg>=pos && neg>=neu) ? "악재" : "중립";
            int posRatio = articleCount > 0 ? (int)Math.round((double)pos/articleCount*100) : 0;
            // 메인 화면의 상승 점수는 기사 방향성, 기사 수, AI 신뢰도, 낚시성,
            // 중립 기사 비중을 함께 반영한 참고용 휴리스틱 점수입니다.
            int trendScore = calculateSectorTrendScore(articleCount, pos, neg, neu, atp, acp);
            String trendDirection = resolveTrendDirection(trendScore);
            String trendLabel = resolveTrendLabel(trendScore);

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
            card.put("avgClickbaitProb", String.format("%.1f", acp));
            card.put("trendScore",   trendScore);
            card.put("trendDirection", trendDirection);
            card.put("trendLabel",   trendLabel);
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

        // ── AI 예측 추가 ────────────────────────────────────────
        try {
            AiPredictionDto aiResult = stockAiService.predict(code);
            model.addAttribute("aiPrediction", aiResult);
        } catch (Exception e) {
            log.warn("[PageController] AI 예측 실패 (페이지 렌더링은 계속): {}", e.getMessage());
            model.addAttribute("aiPrediction", null);
        }

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

    /**
     * 섹터 뉴스 흐름을 0~100 점수로 환산합니다.
     * 50을 기준으로 높으면 상승 우세, 낮으면 하락 우세로 해석합니다.
     */
    private int calculateSectorTrendScore(int articleCount, int pos, int neg, int neu,
                                          double avgTypeProb, double avgClickbaitProb) {
        if (articleCount <= 0) {
            // 기사 자체가 없으면 방향성을 주지 않고 중립 50점으로 둡니다.
            return 50;
        }

        // 호재와 악재의 차이입니다. 호재가 많을수록 양수, 악재가 많을수록 음수가 됩니다.
        double sentimentBias = (double) (pos - neg) / articleCount;

        // 기사 수가 적을 때 점수가 과하게 튀지 않도록 완만하게 보정합니다.
        double articleSupport = Math.min(1.0, Math.log1p(articleCount) / Math.log(12));

        // 기사 품질 보정입니다.
        // AI 신뢰도는 높을수록 좋고, 낚시성 확률은 낮을수록 좋게 반영합니다.
        double reliability = ((avgTypeProb / 100.0) * 0.7)
                + ((1.0 - (avgClickbaitProb / 100.0)) * 0.3);

        // 중립 기사가 많으면 방향성이 약하다고 보고 최종 점수를 조금 깎습니다.
        double neutralPenalty = 1.0 - (((double) neu / articleCount) * 0.35);

        // 50점을 기준점으로 두고 각 보정치를 곱해 상승/하락 방향으로 이동시킵니다.
        double score = 50.0 + (38.0 * sentimentBias * articleSupport * reliability * neutralPenalty);
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }

    private String resolveTrendDirection(int trendScore) {
        // UI 색상용 방향값입니다.
        if (trendScore >= 58) return "up";
        if (trendScore <= 42) return "down";
        return "neutral";
    }

    // UI에 보여줄 텍스트 라벨입니다.
    private String resolveTrendLabel(int trendScore) {
        if (trendScore >= 72) return "강한 상승";
        if (trendScore >= 58) return "상승 우세";
        if (trendScore <= 28) return "강한 하락";
        if (trendScore <= 42) return "하락 우세";
        return "중립";
    }

    // 마이페이지
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
