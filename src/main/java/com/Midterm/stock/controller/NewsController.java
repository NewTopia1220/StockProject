package com.Midterm.stock.controller;

import com.Midterm.stock.dto.NewsDto;
import com.Midterm.stock.repository.NewsDao;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;

@Controller
@RequestMapping("/news")
public class NewsController {

    @Autowired
    private NewsDao newsDao;

    @GetMapping({"", "/"})
    public String newsList(
            @RequestParam(value = "sector",  required = false) String sector,
            @RequestParam(value = "company", required = false) String company,
            @RequestParam(value = "page",    defaultValue = "1") int page,
            HttpSession session,
            Model model) {

        String loginUser = (String) session.getAttribute("loginUser");
        if (loginUser == null) {
            return "redirect:/login";
        }

        int pageSize = 7;
        int start = (page - 1) * pageSize + 1;
        int end   = page * pageSize;

        List<NewsDto> newsList  = newsDao.getNewsList(sector, company, start, end);
        int totalCount          = newsDao.getNewsCount(sector, company);
        int totalPages          = (int) Math.ceil((double) totalCount / pageSize);
        if (totalPages == 0) totalPages = 1;

        // 사이드바 섹터→종목 맵
        LinkedHashMap<String, List<String>> sectorMap = newsDao.getSidebarSectorMap();

        model.addAttribute("newsList",     newsList);
        model.addAttribute("sectorMap",    sectorMap);
        model.addAttribute("totalCount",   totalCount);
        model.addAttribute("totalPages",   totalPages);
        model.addAttribute("currentPage",  page);
        model.addAttribute("sector",       sector);
        model.addAttribute("company",      company);
        model.addAttribute("currentMenu",  "stock");

        return "news/list";
    }
}
