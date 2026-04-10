package com.Midterm.stock.controller;

import com.Midterm.stock.dto.CommunityDto;
import com.Midterm.stock.dto.StockResponseDto;
import com.Midterm.stock.repository.CommunityDao;
import com.Midterm.stock.service.stock.StockPriceService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/community")
public class CommunityController {

    private static final Map<String, ThemeStockSeed> THEME_STOCKS = new LinkedHashMap<>();

    static {
        THEME_STOCKS.put("반도체·AI", new ThemeStockSeed("005930", "삼성전자"));
        THEME_STOCKS.put("2차전지", new ThemeStockSeed("373220", "LG에너지솔루션"));
        THEME_STOCKS.put("제약·바이오", new ThemeStockSeed("207940", "삼성바이오로직스"));
        THEME_STOCKS.put("금융·밸류업", new ThemeStockSeed("105560", "KB금융"));
        THEME_STOCKS.put("방산·우주항공", new ThemeStockSeed("012450", "한화에어로스페이스"));
        THEME_STOCKS.put("IT·플랫폼", new ThemeStockSeed("035420", "NAVER"));
        THEME_STOCKS.put("엔터·미디어", new ThemeStockSeed("352820", "하이브"));
        THEME_STOCKS.put("자동차·모빌리티", new ThemeStockSeed("005380", "현대차"));
    }

    @Autowired
    private CommunityDao communityDao;

    @Autowired
    private StockPriceService stockPriceService;

    @GetMapping({"", "/"})
    public String communityList(@RequestParam(value = "page", defaultValue = "1") int page,
                                @RequestParam(value = "category", required = false) String category,
                                @RequestParam(value = "keyword", required = false) String keyword,
                                Model model,
                                HttpSession session) {
        String loginUser = (String) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        int pageSize = 5;
        int start = (page - 1) * pageSize + 1;
        int end = page * pageSize;
        String normalizedCategory = normalize(category);
        String normalizedKeyword = normalize(keyword);

        ArrayList<CommunityDto> lists = communityDao.getArticles(start, end, normalizedCategory, normalizedKeyword);
        ArrayList<Map<String, Object>> popularCategories = communityDao.getPopularThemeCategories();
        List<Map<String, Object>> popularPriceItems = buildPopularPriceItems(popularCategories);

        model.addAttribute("lists", lists);
        model.addAttribute("popularCategories", popularCategories);
        model.addAttribute("popularPriceItems", popularPriceItems);
        model.addAttribute("selectedCategory", normalizedCategory);
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("currentPage", "community");
        model.addAttribute("page", page);

        return "community/list";
    }

    @GetMapping("/insert")
    public String insertForm(HttpSession session, Model model) {
        CommunityDto dto = new CommunityDto();

        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            return "redirect:/login";
        }

        dto.setUser_num(loginNum);
        model.addAttribute("currentPage", "community");
        model.addAttribute("dto", dto);
        return "community/insert";
    }

    @PostMapping("/insert")
    public String insertProc(CommunityDto dto, HttpSession session) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            return "redirect:/login";
        }

        dto.setUser_num(loginNum);

        int result = communityDao.insertArticle(dto);
        if (result > 0) {
            return "redirect:/community";
        }
        return "community/insert";
    }

    @GetMapping("/detail")
    public String detailProc(@RequestParam("board_id") int boardId,
                             HttpSession session,
                             Model model) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        String loginUser = (String) session.getAttribute("loginUser");

        if (loginUser == null || loginNum == null) {
            return "redirect:/login";
        }

        communityDao.updateViewcount(boardId);
        CommunityDto dto = communityDao.getArticle(boardId);

        model.addAttribute("dto", dto);
        model.addAttribute("currentPage", "community");
        return "community/detail";
    }

    private List<Map<String, Object>> buildPopularPriceItems(List<Map<String, Object>> popularCategories) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (popularCategories == null || popularCategories.isEmpty()) {
            return items;
        }

        for (Map<String, Object> categoryRow : popularCategories) {
            String category = stringValue(categoryRow.get("category"));
            ThemeStockSeed seed = THEME_STOCKS.get(category);
            if (seed == null) {
                continue;
            }

            StockResponseDto quote = stockPriceService.getCurrentPrice(seed.stockCode);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", category);
            item.put("stockName", resolveStockName(seed, quote));
            item.put("stockCode", seed.stockCode);
            item.put("currentPriceText", formatPriceText(quote.getCurrentPrice()));
            item.put("changeRateText", formatRateText(quote.getChangeRate()));
            item.put("changeClass", resolveChangeClass(quote.getChangeRate()));
            item.put("postCount", categoryRow.get("postCount"));
            items.add(item);

            if (items.size() >= 4) {
                break;
            }
        }

        return items;
    }

    private String resolveStockName(ThemeStockSeed seed, StockResponseDto quote) {
        String quoteName = quote == null ? null : normalize(quote.getStockName());
        if (quoteName != null && !quoteName.equals(seed.stockCode)) {
            return quoteName;
        }
        return seed.stockName;
    }

    private String formatPriceText(String rawPrice) {
        double price = parseNumber(rawPrice);
        if (price <= 0) {
            return "-";
        }
        return String.format(Locale.KOREA, "%,.0f원", price);
    }

    private String formatRateText(String rawRate) {
        double rate = parseNumber(rawRate);
        if (!Double.isFinite(rate)) {
            return "-";
        }
        String prefix = rate > 0 ? "+" : "";
        return prefix + String.format(Locale.US, "%.2f%%", rate);
    }

    private String resolveChangeClass(String rawRate) {
        double rate = parseNumber(rawRate);
        if (!Double.isFinite(rate) || rate == 0) {
            return "priceNeutral";
        }
        return rate > 0 ? "priceUp" : "priceDown";
    }

    private double parseNumber(String value) {
        if (value == null || value.isBlank()) {
            return Double.NaN;
        }

        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static final class ThemeStockSeed {
        private final String stockCode;
        private final String stockName;

        private ThemeStockSeed(String stockCode, String stockName) {
            this.stockCode = stockCode;
            this.stockName = stockName;
        }
    }
}
