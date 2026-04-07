package com.Midterm.stock.scheduler;

import com.Midterm.stock.repository.StockRepository;
import com.Midterm.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockScheduler {

    private final StockService stockService;
    private final StockRepository stockRepository;

    // 서버 완전히 시작된 후 실행 (ContextRefreshedEvent 대신 ApplicationReadyEvent 사용)
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (stockRepository.count() == 0) {
            System.out.println("서버 시작 - 종목 목록 초기 로드");
            stockService.refreshStockListToDB();
        } else {
            System.out.println("서버 시작 - DB 종목 있음: " + stockRepository.count() + "개");
        }
    }

    // 매일 새벽 6시 자동 갱신
    @Scheduled(cron = "0 0 6 * * MON-FRI")
    public void refreshStockList() {
        System.out.println("종목 목록 자동 갱신 시작...");
        stockService.refreshStockListToDB();
    }
}