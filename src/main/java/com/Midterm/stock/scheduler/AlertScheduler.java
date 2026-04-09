package com.Midterm.stock.scheduler;

import com.Midterm.stock.dto.StockResponseDto;
import com.Midterm.stock.entity.StockAlert;
import com.Midterm.stock.entity.WatchList;
import com.Midterm.stock.repository.StockAlertRepository;
import com.Midterm.stock.repository.WatchListRepository;
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

    private static final double ALERT_THRESHOLD = 3.0;

    /**
     * 5분마다 관심종목 등락률 체크 (평일 09:00~15:55)
     */
    @Scheduled(cron = "0 */5 9-15 * * MON-FRI")
    @Transactional
    public void checkPriceAlerts() {
        // 15:30 이후면 장 마감으로 스킵
        if (LocalTime.now().isAfter(LocalTime.of(15, 30))) return;

        List<Object[]> distinctStocks = watchListRepository.findDistinctStocks();
        if (distinctStocks.isEmpty()) return;

        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();

        for (Object[] row : distinctStocks) {
            String code = (String) row[0];
            String name = (String) row[1];

            try {
                StockResponseDto price = stockPriceService.getCurrentPrice(code);
                if (price == null || price.getChangeRate() == null) continue;

                double rate = parseRate(price.getChangeRate());
                if (Math.abs(rate) < ALERT_THRESHOLD) continue;

                String alertType = rate > 0 ? "상승" : "하락";

                // 해당 종목 관심 등록 사용자 전체
                List<WatchList> watchers = watchListRepository.findByStockCode(code);
                for (WatchList watcher : watchers) {
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
