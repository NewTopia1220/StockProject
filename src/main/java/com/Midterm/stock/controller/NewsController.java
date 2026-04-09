package com.Midterm.stock.controller;

import com.Midterm.stock.dto.AiPredictionDto;
import com.Midterm.stock.dto.NewsDto;
import com.Midterm.stock.repository.NewsDao;
import com.Midterm.stock.service.stock.StockAiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/news")
public class NewsController {

    @Autowired
    private NewsDao newsDao;

    @Autowired
    private StockAiService stockAiService;

    @GetMapping({"", "/"})
    public String newsList(
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            HttpSession session, Model model) {

        if (session.getAttribute("loginUser") == null) return "redirect:/login";
        Integer userNum = (Integer) session.getAttribute("loginNum");
        int uid = userNum != null ? userNum : 0;

        int pageSize = 7;
        int start = (page - 1) * pageSize + 1;
        int end   = page * pageSize;

        List<NewsDto> newsList = newsDao.getNewsList(sector, keyword, start, end, uid);
        enrichPredictionSignals(newsList);
        int totalCount         = newsDao.getNewsCount(sector, keyword);
        int totalPages         = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));

        LinkedHashMap<String, List<String>> sectorMap = newsDao.getSidebarSectorMap();

        // 사이드바 AI 분석 (현재 필터 기준)
        Map<String, Object> sidebarAnalysis = newsDao.getSidebarAnalysis(sector, keyword);
        int saTotal  = (int)    sidebarAnalysis.getOrDefault("totalCount",      0);
        double saType = (double) sidebarAnalysis.getOrDefault("avgTypeProb",    0.0);
        double saNoise= (double) sidebarAnalysis.getOrDefault("avgClickbaitProb",0.0);
        int saPos    = (int)    sidebarAnalysis.getOrDefault("positiveCount",   0);
        int saNeg    = (int)    sidebarAnalysis.getOrDefault("negativeCount",   0);
        int saPosRatio = saTotal > 0 ? (int)Math.round((double)saPos/saTotal*100) : 50;

        model.addAttribute("newsList",     newsList);
        model.addAttribute("sectorMap",    sectorMap);
        model.addAttribute("totalCount",   totalCount);
        model.addAttribute("totalPages",   totalPages);
        model.addAttribute("currentPage",  page);
        model.addAttribute("sector",       sector);
        model.addAttribute("keyword",      keyword);

        // 사이드바 분석 원형 게이지용
        model.addAttribute("saTotal",    saTotal);
        model.addAttribute("saType",     String.format("%.1f", saType));
        model.addAttribute("saNoise",    String.format("%.1f", saNoise));
        model.addAttribute("saPosRatio", saPosRatio);
        model.addAttribute("saNegRatio", saTotal>0 ? (int)Math.round((double)saNeg/saTotal*100) : 50);
        model.addAttribute("currentMenu","news");

        return "news/list";
    }

    private void enrichPredictionSignals(List<NewsDto> newsList) {
        if (newsList == null || newsList.isEmpty()) {
            return;
        }

        List<String> links = new ArrayList<>();
        for (NewsDto dto : newsList) {
            if (dto.getLink() != null && !dto.getLink().isBlank()) {
                links.add(dto.getLink());
            }
        }

        Map<String, Map<String, Object>> signalMap = newsDao.getNewsSignalMap(links);
        for (NewsDto dto : newsList) {
            Map<String, Object> signal = signalMap.get(dto.getLink());
            if (signal == null) {
                continue;
            }

            Object stockCode = signal.get("stockCode");
            if (stockCode != null) {
                dto.setStockCode(stockCode.toString());
            }

            Object impact30m = signal.get("impact30m");
            if (impact30m instanceof Number number) {
                dto.setStockImpactPercent(number.doubleValue());
                dto.setStockImpactSource("기사 영향 모델");
            }
        }

        Map<String, AiPredictionDto> stockPredictions = new HashMap<>();
        for (NewsDto dto : newsList) {
            String stockCode = dto.getStockCode();
            if (stockCode == null || stockCode.isBlank() || stockPredictions.containsKey(stockCode)) {
                continue;
            }

            try {
                stockPredictions.put(stockCode, stockAiService.predict(stockCode));
            } catch (Exception ignored) {
                stockPredictions.put(stockCode, null);
            }
        }

        for (NewsDto dto : newsList) {
            AiPredictionDto prediction = stockPredictions.get(dto.getStockCode());
            if (prediction == null || !prediction.isValid()) {
                continue;
            }

            dto.setRiseProbability(prediction.getProbability());
            dto.setRisePrediction(prediction.getPrediction());
            dto.setRiseConfidence(prediction.getConfidence());
        }
    }

    @PostMapping("/like")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleLike(
            @RequestParam("link") String link, HttpSession session) {
        Integer userNum = (Integer) session.getAttribute("loginNum");
        if (userNum == null) return ResponseEntity.status(401).body(Map.of("error","로그인이 필요합니다"));
        int newCount = newsDao.toggleLike(link, userNum);
        Map<String, Object> info = newsDao.getLikeInfo(link, userNum);
        Map<String, Object> resp = new HashMap<>();
        resp.put("count", newCount);
        resp.put("liked", info.get("liked"));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/comments")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getComments(
            @RequestParam("link") String link, HttpSession session) {
        if (session.getAttribute("loginUser") == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(newsDao.getComments(link));
    }

    @PostMapping("/comments")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addComment(
            @RequestParam("link") String link,
            @RequestParam("content") String content,
            HttpSession session) {
        Integer userNum = (Integer) session.getAttribute("loginNum");
        if (userNum == null) return ResponseEntity.status(401).body(Map.of("error","로그인이 필요합니다"));
        if (content == null || content.trim().isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error","내용을 입력해주세요"));
        int result = newsDao.insertComment(link, userNum, content.trim());
        if (result > 0) return ResponseEntity.ok(Map.of("success", true, "comments", newsDao.getComments(link)));
        return ResponseEntity.internalServerError().body(Map.of("error","등록 실패"));
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable int commentId, HttpSession session) {
        Integer userNum = (Integer) session.getAttribute("loginNum");
        if (userNum == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of("success", newsDao.deleteComment(commentId, userNum) > 0));
    }
}
