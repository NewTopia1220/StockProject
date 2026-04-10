package com.Midterm.stock.scheduler;

import com.Midterm.stock.dto.StockResponseDto;
import com.Midterm.stock.entity.StockAlert;
import com.Midterm.stock.entity.WatchList;
import com.Midterm.stock.repository.StockAlertRepository;
import com.Midterm.stock.repository.WatchListRepository;
import com.Midterm.stock.service.WatchListService;
import com.Midterm.stock.service.stock.StockPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 관심종목 가격 알림 스케줄러
 * - 장 중(09:00~15:30) 평일 5분마다 실행
 * - 전일 대비 ±3% 이상 변동 시 사용자별 알림 생성
 * - 자정에 7일 지난 알림 자동 삭제
 */
@Component
@RequiredArgsConstructor
public class AlertScheduler {

    private final WatchListRepository watchListRepository;
    private final StockAlertRepository stockAlertRepository;
    private final StockPriceService stockPriceService;
    private final WatchListService watchListService;

    private static final double ALERT_THRESHOLD = 0.5;

    /**
     * 5분마다 관심종목 등락률 체크 (평일 09:00~15:55)
     */
//    @Scheduled(fixedDelay = 5000) // 확인용
    @Scheduled(cron = "0 */5 9-15 * * MON-FRI")
    @Transactional
    public void checkPriceAlerts() {
        // 15:30 이후면 장 마감으로 스킵
        if (LocalTime.now().isAfter(LocalTime.of(15, 30))) return;
        System.out.println("--- 스케줄러 작동 시작 ---"); // 작동 여부 확인용 로그  - 확인용
        // [키가 잘 들어왔나 확인용 로그]
        System.out.println("체크용 키값: " + System.getenv("KIS_APP_KEY"));
        System.out.println("체크용 시크릿: " + System.getenv("KIS_APP_SECRET"));
        System.out.println("체크용 KIS_BASE_URL: " + System.getenv("KIS_BASE_URL"));
        System.out.println("체크용 EXIM_API_KEY: " + System.getenv("EXIM_API_KEY"));

        List<Object[]> distinctStocks = watchListRepository.findDistinctStocks();
        if (distinctStocks.isEmpty()) return;

        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();

        for (Object[] row : distinctStocks) {
            String code = (String) row[0];
            String name = (String) row[1];

            System.out.println("조회 중인 종목: " + name + "(" + code + ")"); // 확인용

            try {
                StockResponseDto price = stockPriceService.getCurrentPrice(code);
                if (price == null || price.getChangeRate() == null) continue;

                double rate = parseRate(price.getChangeRate());
                // 강제로 API가 성공한 척 속입니다.
//                double rate = 5.0; // 5% 상승했다고 가짜 데이터 주입 - 확인용
                if (Math.abs(rate) < ALERT_THRESHOLD) continue;

                String alertType = rate > 0 ? "상승" : "하락";

                // 해당 종목 관심 등록 사용자 전체
                List<WatchList> watchers = watchListRepository.findByStockCode(code);
                for (WatchList watcher : watchers) {
                    // -------------------------------민경---------------------------
                    // [민경 수정]
                    // 1. 서비스나 리포지토리를 통해 사용자의 알림 수신 여부를 가져옵니다.
                    boolean isEnabled = watchListService.isNotifyEnabled(watcher.getUserNum());

                    // 2. 만약 꺼져 있다면, 이 사용자는 알림 생성을 스킵(continue)합니다.
                    System.out.println("유저 " + watcher.getUserNum() + "의 알림 설정 상태: " + isEnabled);

                    if (!isEnabled) {
                        System.out.println("[알림 차단 확인] 유저: " + watcher.getUserNum() + " | 설정: OFF -> DB 저장을 스킵합니다.");
                        continue;
                    } else {
                        System.out.println("[알림 전송 대상] 유저: " + watcher.getUserNum() + " | 설정: ON -> 로직 진행");
                    }

                    // ----------------------------------------------------------

                    boolean exists = stockAlertRepository.existsTodayAlert(
                            watcher.getUserNum(), code, alertType, startOfDay);
                    if (!exists) {
                        StockAlert alert = new StockAlert();
                        alert.setUserNum(watcher.getUserNum());
                        alert.setStockCode(code);
                        alert.setStockName(name);
                        alert.setChangeRate(String.format("%.2f", Math.abs(rate)));
                        alert.setAlertType(alertType);
                        alert.setPrice(price.getCurrentPrice());
//                        alert.setPrice("5000");   // 확인용
                        alert.setAlertRead(false);
                        stockAlertRepository.save(alert);
                    }
                }

                // KIS API 부하 방지
                Thread.sleep(200);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.out.println("알림 체크 오류 [" + code + "]: " + e.getMessage());
            }
        }
    }

    /**
     * 매일 자정 7일 지난 알림 삭제
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanOldAlerts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        stockAlertRepository.deleteOlderThan(cutoff);
        System.out.println("7일 지난 알림 삭제 완료");
    }

    private double parseRate(String rate) {
        try {
            return Double.parseDouble(rate.replace(",", "").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
