package com.Midterm.stock.service;

import com.Midterm.stock.entity.StockAlert;
import com.Midterm.stock.entity.WatchList;
import com.Midterm.stock.repository.StockAlertRepository;
import com.Midterm.stock.repository.WatchListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WatchListService {

    private final WatchListRepository watchListRepository;
    private final StockAlertRepository stockAlertRepository;

    // ── 관심종목 ─────────────────────────────────────────────

    /** 관심종목 추가 */
    @Transactional
    public boolean addWatch(Integer userNum, String stockCode, String stockName) {
        if (watchListRepository.existsByUserNumAndStockCode(userNum, stockCode)) {
            return false; // 이미 등록됨
        }
        WatchList w = new WatchList();
        w.setUserNum(userNum);
        w.setStockCode(stockCode);
        w.setStockName(stockName);
        watchListRepository.save(w);
        return true;
    }

    /** 관심종목 제거 */
    @Transactional
    public void removeWatch(Integer userNum, String stockCode) {
        watchListRepository.deleteByUserNumAndStockCode(userNum, stockCode);
    }

    /** 관심종목 여부 확인 */
    public boolean isWatching(Integer userNum, String stockCode) {
        return watchListRepository.existsByUserNumAndStockCode(userNum, stockCode);
    }

    /** 사용자 관심종목 목록 */
    public List<WatchList> getWatchList(Integer userNum) {
        return watchListRepository.findByUserNumOrderByCreatedAtDesc(userNum);
    }

    // ── 알림 ─────────────────────────────────────────────────

    /** 사용자 알림 목록 (7일 이내) */
    public List<StockAlert> getAlerts(Integer userNum) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        return stockAlertRepository.findByUserNumAndCreatedAtAfterOrderByCreatedAtDesc(userNum, since);
    }

    /** 미읽음 알림 개수 */
    public long getUnreadCount(Integer userNum) {
        return stockAlertRepository.countByUserNumAndAlertReadFalse(userNum);
    }

    /** 전체 읽음 처리 */
    @Transactional
    public void markAllRead(Integer userNum) {
        stockAlertRepository.markAllRead(userNum);
    }

    /** 알림 패널용 응답 DTO 빌드 */
    public Map<String, Object> buildAlertPanel(Integer userNum) {
        List<StockAlert> alerts = getAlerts(userNum);
        long unread = getUnreadCount(userNum);

        List<Map<String, Object>> items = alerts.stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("stockCode", a.getStockCode());
            m.put("stockName", a.getStockName());
            m.put("alertType", a.getAlertType());
            m.put("changeRate", a.getChangeRate());
            m.put("price", a.getPrice());
            m.put("read", a.isAlertRead());
            m.put("createdAt", a.getCreatedAt().toString());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("unreadCount", unread);
        result.put("alerts", items);
        return result;
    }
}
