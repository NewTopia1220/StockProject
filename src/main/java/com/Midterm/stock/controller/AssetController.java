package com.Midterm.stock.controller;

import com.Midterm.stock.dto.AssetDto;
import com.Midterm.stock.dto.AssetPlannerAnalysisDto;
import com.Midterm.stock.dto.UserDto;
import com.Midterm.stock.repository.AssetDao;
import com.Midterm.stock.repository.UserDao;
import com.Midterm.stock.service.AssetPlannerAnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MoneyPlan 메인 컨트롤러
 * 각 페이지로 라우팅하고 필요한 데이터를 Model에 담아 전달합니다.
 */
@Controller
public class AssetController {

    @Autowired
    private AssetDao assetDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private AssetPlannerAnalysisService assetPlannerAnalysisService;

    /**
     * 현재 월을 가져오는 메서드
     */
    private int getCurrentMonth() {
        return LocalDate.now().getMonthValue();
    }
    private int getCurrentYear() {
        return LocalDate.now().getYear();
    }

    /**
     * 대시보드 (홈) 페이지
     */
    @GetMapping({"/asset/dashboard"})
    public String dashboard(@RequestParam(value = "month", required = false) Integer month, @RequestParam(value = "year", required = false) Integer year, Model model, HttpSession session) throws JsonProcessingException {
        // 각 페이지 컨트롤러 메서드 시작 부분 예시
        Integer loginNum = (Integer) session.getAttribute("loginNum");

        if (loginNum == null) {
            return "redirect:/login";
        }

        UserDto user = userDao.getUserInfo(loginNum);
        model.addAttribute("userName", user.getName());
        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("user_id", user.getNum());

        // 현재 실제 날짜 기준 (2026년 4월)
         int currentRealMonth = getCurrentMonth();
//        int currentRealMonth = 3;
         int currentRealYear = getCurrentYear();

        // 파라미터가 없으면 현재 달(4월)로 설정
        if (month == null) {
            month = currentRealMonth;
        }
        if (year == null) {
            year = currentRealYear;
        }

        // 달 지정해서 데이터 불러오기
        List<AssetDto> transactions = assetDao.getRecentTransactionsByMonth(month, year, loginNum); // 메서드 추가 필요
        model.addAttribute("selectedMonth", month);
        model.addAttribute("selectedYear", year);
        model.addAttribute("currentRealMonth", currentRealMonth);
        model.addAttribute("currentRealYear", currentRealYear);
        model.addAttribute("recentTransactions", transactions);

        // 이번 달 지출 / 이전 달 지출
        // -----------------------민경 수정 추가 -----------------------------------------
        // 1. [민경 수정] 이전 데이터 점프 좌표 찾기
        int currentMonthSpending = assetDao.getMonthSpending(month, year, loginNum);
        Map<String, Integer> prevDate = assetDao.getNearestPrevDate(month, year, loginNum);
        int previousMonthSpending = 0; // 초기값

        if (prevDate != null) {
            int pMonth = prevDate.get("month");
            int pYear = prevDate.get("year");
            model.addAttribute("hasPrevData", true);
            model.addAttribute("prevMonth", pMonth);
            model.addAttribute("prevYear", pYear);
            // 이전 데이터가 있는 달의 지출액을 가져와야 증감률 비교가 됨
            previousMonthSpending = assetDao.getMonthSpending(pMonth, pYear, loginNum);
        } else {
            model.addAttribute("hasPrevData", false);
        }

        // 2. [민경 수정] 다음 데이터 점프 좌표 찾기
        Map<String, Integer> nextDate = assetDao.getNearestNextDate(month, year, loginNum);
        if (nextDate != null) {
            model.addAttribute("hasNextData", true);
            model.addAttribute("nextMonth", nextDate.get("month"));
            model.addAttribute("nextYear", nextDate.get("year"));
        } else {
            model.addAttribute("hasNextData", false);
        }

        model.addAttribute("currentMonthSpending", currentMonthSpending);
        model.addAttribute("previousMonthSpending", previousMonthSpending);

        //----------------------------------------------------------------------------

        //이번 달 지출과 지난달 지출을 비교해서 증감률을 계산 -> 색상(빨강/파랑)과 화살표 방향까지 바꿈
        double diffRate = 0;
        if (previousMonthSpending > 0) {
            // 증감률 계산: (이번달 - 지난달) / 지난달 * 100
            diffRate = ((double) (currentMonthSpending - previousMonthSpending) / previousMonthSpending) * 100;
        }
        model.addAttribute("diffRate", Math.abs(Math.round(diffRate * 10) / 10.0)); // 소수점 첫째자리까지 절대값으로
        model.addAttribute("isIncreased", currentMonthSpending >= previousMonthSpending); // 증가 여부 판단

        int currentMonthIncome = 0; // 초기값

        // 1. 저축 플래너 최신 이력 가져오기
        List<AssetPlannerAnalysisDto> history = assetDao.getAnalysisHistory(loginNum);
        AssetPlannerAnalysisDto latestAnalysis = new AssetPlannerAnalysisDto();

        if (history != null && !history.isEmpty()) {
            latestAnalysis = history.get(0); // 가장 최근 분석 데이터
            currentMonthIncome = (int) latestAnalysis.getMonthlyIncome();
        } else {
            // 데이터가 없을 경우 기본값 세팅
            latestAnalysis.setGoalAmount(0L);
            latestAnalysis.setCurrentAsset(0L);
            latestAnalysis.setRequiredMonthlySaving(0L);
        }
        model.addAttribute("latestAnalysis", latestAnalysis);

        // 2. 예산 대비 지출 (월 소득 대비 월 지출) 계산
        double spendingRate = 0;
        if (currentMonthIncome > 0) {
            spendingRate = ((double) currentMonthSpending / currentMonthIncome) * 100;
        }
        model.addAttribute("spendingRate", Math.round(spendingRate));
        model.addAttribute("currentMonthIncome", currentMonthIncome);
        model.addAttribute("currentMonthSpending", currentMonthSpending);

        // 3. 저축 목표 달성률 계산 (현재 자산 / 목표 금액)
        double goalAchievementRate = 0;
        if (latestAnalysis.getGoalAmount() > 0) {
            goalAchievementRate = ((double) latestAnalysis.getCurrentAsset() / latestAnalysis.getGoalAmount()) * 100;
        }
        model.addAttribute("goalAchievementRate", Math.round(goalAchievementRate * 10) / 10.0);


        // 월별 지출 가져오기 5개월
        List<Map<String, Object>> trendData = assetDao.getLast5MonthsSpending(year, month, loginNum);

        // 평균 계산
        int sum = trendData.stream().mapToInt(m -> (int) m.get("total")).sum();
        int avg = trendData.size() > 0 ? sum / trendData.size() : 0;

        // 최근 2달 증감
        int last = (int) trendData.get(trendData.size() - 1).get("total");
        int prev = (int) trendData.get(trendData.size() - 2).get("total");
        int diff = last - prev;
        boolean isUp = diff >= 0;

        ObjectMapper mapper = new ObjectMapper();
        String trendJson = mapper.writeValueAsString(trendData);
        model.addAttribute("trendJson", trendJson);

        model.addAttribute("trendData", trendData);
        model.addAttribute("avgSpending", avg);
        model.addAttribute("diffAmount", Math.abs(diff));
        model.addAttribute("isUp", isUp);

        return "asset/dashboard";

    }

    /**
     * 소비 분석 페이지
     */
    @GetMapping("/asset/analytics")
    public String analytics(@RequestParam(value = "month", required = false) Integer month, @RequestParam(value = "year", required = false) Integer year, Model model, HttpSession session) {
        // 각 페이지 컨트롤러 메서드 시작 부분 예시
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        if (loginNum == null) {
            return "redirect:/login";
        }


//        int currentRealMonth = 3;
        int currentRealMonth = getCurrentMonth();
        int currentRealYear = getCurrentYear();
        if (month == null)
            month = currentRealMonth;
        if (year == null) {
            year = currentRealYear;
        }

        System.out.println("현재 선택된 월: " + month);
        System.out.println("기준이 되는 현재 리얼 월: " + currentRealMonth);
        model.addAttribute("currentRealMonth", currentRealMonth);
        model.addAttribute("currentRealYear", currentRealYear);



        // DB에서 유저 정보 가져오기
        UserDto user = userDao.getUserInfo(loginNum);
        model.addAttribute("userName", user.getName());
        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("user_id", user.getNum());

        // 1. 필수 vs 비필수 데이터 연동
        Map<String, Integer> needWant = assetDao.getNeedWantSpending(month, year, loginNum);
        int need = needWant.getOrDefault("need", 0);
        int want = needWant.getOrDefault("want", 0);
        int total = need + want;

        System.out.println("조회 월: " + month);
        System.out.println("필수 금액: " + need);
        System.out.println("비필수 금액: " + want);
        System.out.println("계산된 퍼센트: " + (total > 0 ? (need * 100 / total) : 0));

        // 필수 vs 비필수 데이터
        model.addAttribute("needAmount", need);
        model.addAttribute("wantAmount", want);
        model.addAttribute("needPercentage", total > 0 ? (need * 100 / total) : 0);
        model.addAttribute("wantPercentage", total > 0 ? (want * 100 / total) : 0);

        // 2. 카테고리 데이터 연동
        List<Map<String, Object>> categories = assetDao.getCategorySpending(month, year, loginNum);
        // 색상 배열 (차트와 리스트에 순서대로 적용)
        String[] colors = {"#15164D", "#00C6B8", "#FFA726", "#AB47BC", "#FF4B4B", "#66BB6A", "#8D6E63"};
        for (int i = 0; i < categories.size(); i++) {
            categories.get(i).put("color", colors[i % colors.length]);
            // 퍼센트 계산 추가
            int val = (int) categories.get(i).get("value");
            categories.get(i).put("percentage", total > 0 ? String.format("%.1f", (val * 100.0 / total)) : "0");
        }

        model.addAttribute("categories", categories);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("selectedYear", year);

        // 3. 인사이트 데이터 생성 (선택된 달 vs 이전 달)
        Map<String, Integer> currentMap = assetDao.getCategoryMapByMonth(month, year, loginNum);
        Map<String, Integer> previousMap; // 선언만 해두고 아래 if문에서 채웁니다.

        // 1. 이전 데이터 점프 좌표 찾기
        Map<String, Integer> prevDate = assetDao.getNearestPrevDate(month, year, loginNum);
        if (prevDate != null) {
            int pMonth = prevDate.get("month");
            int pYear = prevDate.get("year");
            model.addAttribute("hasPrevData", true);
            model.addAttribute("prevMonth", pMonth);
            model.addAttribute("prevYear", pYear);

            // 인사이트 비교를 위해 이전 달 지출 맵을 '점프한 달' 기준으로 다시 가져옴
            previousMap = assetDao.getCategoryMapByMonth(pMonth, pYear, loginNum);
        } else {
            model.addAttribute("hasPrevData", false);
            previousMap = new HashMap<>(); // 이전 데이터 없으면 빈 맵
        }

        // 2. 다음 데이터 점프 좌표 찾기
        Map<String, Integer> nextDate = assetDao.getNearestNextDate(month, year, loginNum);
        if (nextDate != null) {
            model.addAttribute("hasNextData", true);
            model.addAttribute("nextMonth", nextDate.get("month"));
            model.addAttribute("nextYear", nextDate.get("year"));
        } else {
            model.addAttribute("hasNextData", false);
        }

        List<Map<String, Object>> insights = new ArrayList<>(); //

        //   ----------------------민경 수정-------------------------------------

        // 이번 달에 소비가 있는 카테고리들을 순회하며 비교
        for (String category : currentMap.keySet()) {
            int currentAmount = currentMap.get(category);
            int previousAmount = previousMap.getOrDefault(category, 0);

            if (previousAmount > 0) { // 지난달 데이터가 있을 때만 계산
//                double rate = ((double) (currentAmount - previousAmount) / previousAmount) * 100;
                double rate = (double)currentAmount / previousAmount ;

                Map<String, Object> insight = new HashMap<>();
//                boolean isIncreased = rate >= 0;
                boolean isIncreased = currentAmount > previousAmount;

                insight.put("title", category + " 지출 " + (isIncreased ? "증가" : "감소"));
//                insight.put("desc", "지난달보다 " + category + " 지출이 " + Math.abs(Math.round(rate)) + "% " + (isIncreased ? "증가" : "감소") + "했습니다.");
                insight.put("desc", "지난달보다 " + category + " 지출이 " + Math.round(rate * 10) / 10.0 + "배 " + (isIncreased ? "증가" : "감소") + "했습니다.");
                insight.put("isWarning", isIncreased); // 증가 = 경고(빨강), 감소 = 긍정(파랑)
                insight.put("absRate", Math.abs(rate)); // 정렬용

                insights.add(insight);
            }
        }

        // 증감 변동폭(절대값)이 큰 순서대로 정렬 후 상위 3개 추출
        insights.sort((a, b) -> Double.compare((double) b.get("absRate"), (double) a.get("absRate")));
        model.addAttribute("insights", insights.stream().limit(3).toList());


        // 가장 최근의 지출 10개 가져오기
        List<AssetDto> transactions = assetDao.getRecentTransactionsByMonth(month, year, loginNum); // 메서드 추가 필요
        model.addAttribute("selectedMonth", month);
        model.addAttribute("currentRealMonth", currentRealMonth);
        model.addAttribute("currentRealYear", currentRealYear);
        model.addAttribute("recentTransactions", transactions);

        return "asset/analytics";
    }

    /**
     * 저축 플래너 페이지 걍 초기버전 합쳐야됨
     */
    @GetMapping("/asset/savings-planner")
    public String showPlanner(@RequestParam(value="month", required=false) Integer month,
                              @RequestParam(value="year", required=false) Integer year,
                              Model model, HttpSession session) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        if (loginNum == null) {
            return "redirect:/login";
        }

        // 현재 실제 날짜 기준 (2026년 4월)
        int currentRealMonth = getCurrentMonth();
        int currentRealYear = getCurrentYear();

        // 파라미터가 없으면 현재 달(4월)로 설정
        if (month == null) {
            month = currentRealMonth;
        }
        if (year == null) {
            year = currentRealYear;
        }

        // DB에서 유저 정보 가져오기
        UserDto user = userDao.getUserInfo(loginNum);
        model.addAttribute("userName", user.getName());
        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("user_id", user.getNum());


        // 1. DB에서 전체 히스토리 가져오기
        List<AssetPlannerAnalysisDto> history = assetPlannerAnalysisService.getHistory(loginNum);
        AssetPlannerAnalysisDto lastData;

        if (history != null && !history.isEmpty()) {
            // 2. 가장 최근 데이터(첫 번째 아이템)를 기본값으로 설정
            lastData = history.get(0);
        } else {
            // 3. 만약 이력이 하나도 없다면 그때만 0으로 초기화된 빈 객체 생성
            lastData = new AssetPlannerAnalysisDto();
            lastData.setGoalAmount(0L);
            lastData.setGoalMonths(12);
            lastData.setAge(25); // 기본값 예시
        }

        model.addAttribute("assetDto", lastData);
        model.addAttribute("historyList", history);

        // 이번 달 실제 지출액도 함께 넘겨줍니다 (화면 출력용)
        model.addAttribute("currentMonthSpending", assetDao.getMonthSpending(month, year, loginNum));
        model.addAttribute("selectedMonth", month); // 뷰에서 쓰기 위해 추가
        model.addAttribute("selectedYear", year);   // 뷰에서 쓰기 위해 추가
        // model.addAttribute("currentMonthSpending", assetDao.getMonthSpending(getCurrentMonth()));   // 대신 실제 현재 월 지출액을 가져오도록 변경

        return "asset/savings-planner";
    }

    @PostMapping("/asset/savings-planner/analyze")
    public String assetAnalyze(@RequestParam(value="month", required=false) Integer month,
                               @RequestParam(value="year", required=false) Integer year,
                               @ModelAttribute("assetDto") AssetPlannerAnalysisDto assetDto,
                               HttpSession session,
                               Model model) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        if (loginNum == null) {
            return "redirect:/login";
        }

        // 현재 실제 날짜 기준 (2026년 4월)
        int currentRealMonth = getCurrentMonth();
//        int currentRealMonth = 3;
        int currentRealYear = getCurrentYear();

        // 파라미터가 없으면 현재 달(4월)로 설정
        if (month == null) {
            month = currentRealMonth;
        }
        if (year == null) {
            year = currentRealYear;
        }

        // DB에서 유저 정보 가져오기
        UserDto user = userDao.getUserInfo(loginNum);
        model.addAttribute("userName", user.getName());
        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("user_id", user.getNum());

        try {
            // 이번 달 지출 DB에서 가져오기
            int currentMonthValue = getCurrentMonth();
            int currentMonthSpending = assetDao.getMonthSpending(month, year, loginNum);
            assetDto.setMonthlyExpense((long) currentMonthSpending);

            // 서비스에서 FastAPI 호출 + DB 저장 + 결과 반환
            AssetPlannerAnalysisDto resultDto = assetPlannerAnalysisService.analyzeAndSave(assetDto, loginNum);

            model.addAttribute("selectedMonth", month); // 결과 페이지에서도 연/월 유지 위해 추가
            model.addAttribute("selectedYear", year);
            model.addAttribute("assetDto", resultDto);
            model.addAttribute("currentMonthSpending", currentMonthSpending);
            model.addAttribute("historyList", assetPlannerAnalysisService.getHistory(loginNum));

            return "asset/savings-planner";

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("assetDto", assetDto);
//            model.addAttribute("currentMonthSpending", assetDao.getMonthSpending(getCurrentMonth()));
            model.addAttribute("currentMonthSpending", assetDao.getMonthSpending(month, year, loginNum));
            model.addAttribute("historyList", assetPlannerAnalysisService.getHistory(loginNum));
            model.addAttribute("errorMessage", "자산 분석 중 오류가 발생했습니다. " + e.getMessage());
            return "asset/savings-planner";
        }
    }






}
