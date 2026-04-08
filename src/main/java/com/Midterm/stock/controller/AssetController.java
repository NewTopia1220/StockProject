package com.Midterm.stock.controller;

import com.Midterm.stock.dto.AssetDto;
import com.Midterm.stock.dto.AssetPlannerAnalysisDto;
import com.Midterm.stock.dto.UserDto;
import com.Midterm.stock.repository.AssetDao;
import com.Midterm.stock.repository.UserDao;
import com.Midterm.stock.service.AssetPlannerAnalysisService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
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

    /**
     * 대시보드 (홈) 페이지
     */
    @GetMapping({"/asset/dashboard"})
    public String dashboard(@RequestParam(value = "month", required = false) Integer month, Model model, HttpSession session) {
        // 각 페이지 컨트롤러 메서드 시작 부분 예시
        Integer loginNum = (Integer) session.getAttribute("loginNum");

        if (loginNum == null) {
            return "redirect:/login";
        }

        UserDto user = userDao.getUserInfo(loginNum);
        model.addAttribute("userName", user.getName());
        model.addAttribute("userEmail", user.getEmail());

        // 현재 실제 날짜 기준 (2026년 4월)
        // int currentRealMonth = getCurrentMonth();
        int currentRealMonth = 3;

        // 파라미터가 없으면 현재 달(4월)로 설정
        if (month == null) {
            month = currentRealMonth;
        }

        // 달 지정해서 데이터 불러오기
        List<AssetDto> transactions = assetDao.getRecentTransactionsByMonth(month, loginNum); // 메서드 추가 필요
        model.addAttribute("selectedMonth", month);
        model.addAttribute("currentRealMonth", currentRealMonth);
        model.addAttribute("recentTransactions", transactions);

        // 이번 달 지출 / 전 달 지출
            // 이전 달 계산 (1월일 경우 12월로 가야 하는 로직은 필요에 따라 Dao에서 처리하거나 여기서 보정)
        int currentMonthSpending = assetDao.getMonthSpending(month, loginNum);  // 일단 넣어준거 수정해야됨
        int prevMonth = (month == 1) ? 12 : month - 1;
        int previousMonthSpending = assetDao.getMonthSpending(prevMonth, loginNum);  // 일단 넣어준거 수정해야됨

        model.addAttribute("currentMonthSpending", currentMonthSpending);
        model.addAttribute("previousMonthSpending", previousMonthSpending);
        model.addAttribute("prevMonth", prevMonth);

        // 이전 달과 다음 달의 데이터 존재 여부 확인
        List<AssetDto> prevTransactions = assetDao.getRecentTransactionsByMonth(prevMonth, loginNum);
        boolean hasPrevData = !prevTransactions.isEmpty();

        int nextMonth = (month == 12) ? 1 : month + 1;
        List<AssetDto> nextTransactions = assetDao.getRecentTransactionsByMonth(nextMonth, loginNum);
        boolean hasNextData = !nextTransactions.isEmpty();

        model.addAttribute("hasPrevData", hasPrevData);
        model.addAttribute("hasNextData", hasNextData);
        model.addAttribute("nextMonth", nextMonth);


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

//        // 전체 월별 자산 추이 (2월, 3월, 4월 등)
//        // 선택된 월 기준 최근 3개월 데이터 가져오기
//        Map<String, Long> assetTrend = assetDao.getAssetTrendData(currentRealMonth);
//
//        model.addAttribute("trendLabels", new ArrayList<>(assetTrend.keySet()));
//        model.addAttribute("trendValues", new ArrayList<>(assetTrend.values()));

        return "asset/dashboard";

    }

    /**
     * 소비 분석 페이지
     */
    @GetMapping("/asset/analytics")
    public String analytics(@RequestParam(value = "month", required = false) Integer month, Model model, HttpSession session) {
        // 각 페이지 컨트롤러 메서드 시작 부분 예시
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        if (loginNum == null) {
            return "redirect:/login";
        }


        int currentRealMonth = 3;
//        int currentRealMonth = getCurrentMonth();
        if (month == null)
            month = currentRealMonth;

        System.out.println("현재 선택된 월: " + month);
        System.out.println("기준이 되는 현재 리얼 월: " + currentRealMonth);
        model.addAttribute("currentRealMonth", currentRealMonth);



        // DB에서 유저 정보 가져오기
        UserDto user = userDao.getUserInfo(loginNum);
        model.addAttribute("userName", user.getName());
        model.addAttribute("userEmail", user.getEmail());

        // 1. 필수 vs 비필수 데이터 연동
        Map<String, Integer> needWant = assetDao.getNeedWantSpending(month, loginNum);
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
        List<Map<String, Object>> categories = assetDao.getCategorySpending(month, loginNum);
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

        // 3. 인사이트 데이터 생성 (선택된 달 vs 이전 달)
        int prevMonth = (month == 1) ? 12 : month - 1;
        Map<String, Integer> currentMap = assetDao.getCategoryMapByMonth(month, loginNum);
        Map<String, Integer> previousMap = assetDao.getCategoryMapByMonth(prevMonth, loginNum);
        model.addAttribute("prevMonth", prevMonth);

        List<Map<String, Object>> insights = new ArrayList<>();

        // 이전 달 데이터 존재 여부 확인
        boolean hasPrevData = assetDao.getRecentTransactionsByMonth(prevMonth, loginNum) != null
                && !assetDao.getRecentTransactionsByMonth(prevMonth, loginNum).isEmpty();
        model.addAttribute("hasPrevData", hasPrevData);

        // 다음 달 데이터 존재 여부 확인
        int nextMonth = (month == 12) ? 1 : month + 1;
        boolean hasNextData = assetDao.getRecentTransactionsByMonth(nextMonth, loginNum) != null
                && !assetDao.getRecentTransactionsByMonth(nextMonth, loginNum).isEmpty();
        model.addAttribute("hasNextData", hasNextData);
        model.addAttribute("nextMonth", nextMonth);


        // 이번 달에 소비가 있는 카테고리들을 순회하며 비교
        for (String category : currentMap.keySet()) {
            int currentAmount = currentMap.get(category);
            int previousAmount = previousMap.getOrDefault(category, 0);

            if (previousAmount > 0) { // 지난달 데이터가 있을 때만 계산
                double rate = ((double) (currentAmount - previousAmount) / previousAmount) * 100;

                Map<String, Object> insight = new HashMap<>();
                boolean isIncreased = rate >= 0;

                insight.put("title", category + " 지출 " + (isIncreased ? "증가" : "감소"));
                insight.put("desc", "지난달보다 " + category + " 지출이 " + Math.abs(Math.round(rate)) + "% " + (isIncreased ? "증가" : "감소") + "했습니다.");
                insight.put("isWarning", isIncreased); // 증가 = 경고(빨강), 감소 = 긍정(파랑)
                insight.put("absRate", Math.abs(rate)); // 정렬용

                insights.add(insight);
            }
        }

        // 증감 변동폭(절대값)이 큰 순서대로 정렬 후 상위 3개 추출
        insights.sort((a, b) -> Double.compare((double) b.get("absRate"), (double) a.get("absRate")));
        model.addAttribute("insights", insights.stream().limit(3).toList());


        // 가장 최근의 지출 10개 가져오기
        List<AssetDto> transactions = assetDao.getRecentTransactionsByMonth(month, loginNum); // 메서드 추가 필요
//        model.addAttribute("selectedMonth", month);
//        model.addAttribute("currentRealMonth", currentRealMonth);
        model.addAttribute("recentTransactions", transactions);

        return "asset/analytics";
    }

    /**
     * 저축 플래너 페이지 걍 초기버전 합쳐야됨
     */
    @GetMapping("/asset/savings-planner")
    public String showPlanner(Model model, HttpSession session) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        if (loginNum == null) {
            return "redirect:/login";
        }

        // DB에서 유저 정보 가져오기
        UserDto user = userDao.getUserInfo(loginNum);
        model.addAttribute("userName", user.getName());
        model.addAttribute("userEmail", user.getEmail());

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
        model.addAttribute("currentMonthSpending", assetDao.getMonthSpending(3, loginNum));
        // model.addAttribute("currentMonthSpending", assetDao.getMonthSpending(getCurrentMonth()));   // 대신 실제 현재 월 지출액을 가져오도록 변경

        return "asset/savings-planner";
    }

    @PostMapping("/asset/savings-planner/analyze")
    public String assetAnalyze(@ModelAttribute("assetDto") AssetPlannerAnalysisDto assetDto,
                               HttpSession session,
                               Model model) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        if (loginNum == null) {
            return "redirect:/login";
        }

        // DB에서 유저 정보 가져오기
        UserDto user = userDao.getUserInfo(loginNum);
        model.addAttribute("userName", user.getName());
        model.addAttribute("userEmail", user.getEmail());

        try {
            // 이번 달 지출 DB에서 가져오기
            //int currentMonthValue = getCurrentMonth();
            int currentMonthSpending = assetDao.getMonthSpending(3, loginNum);
            assetDto.setMonthlyExpense((long) currentMonthSpending);

            // 서비스에서 FastAPI 호출 + DB 저장 + 결과 반환
            AssetPlannerAnalysisDto resultDto = assetPlannerAnalysisService.analyzeAndSave(assetDto, loginNum);

            model.addAttribute("assetDto", resultDto);
            model.addAttribute("currentMonthSpending", currentMonthSpending);
            model.addAttribute("historyList", assetPlannerAnalysisService.getHistory(loginNum));

            return "asset/savings-planner";

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("assetDto", assetDto);
            //model.addAttribute("currentMonthSpending", assetDao.getMonthSpending(getCurrentMonth()));
            model.addAttribute("currentMonthSpending", assetDao.getMonthSpending(3, loginNum));
            model.addAttribute("historyList", assetPlannerAnalysisService.getHistory(loginNum));
            model.addAttribute("errorMessage", "자산 분석 중 오류가 발생했습니다. " + e.getMessage());
            return "asset/savings-planner";
        }
    }






}
