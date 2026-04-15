package com.Midterm.stock.service;

import com.Midterm.stock.entity.StockAlert;
import com.Midterm.stock.entity.WatchList;
import com.Midterm.stock.repository.StockAlertRepository;
import com.Midterm.stock.repository.UserDao;
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

    private static final String ALERT_TYPE_COMMENT = "\uB313\uAE00";
    private static final String ALERT_TYPE_RISE = "\uC0C1\uC2B9";
    private static final String COMMENT_TITLE = "\uB0B4 \uAC8C\uC2DC\uAE00\uC5D0 \uC0C8 \uB313\uAE00";
    private static final String COMMENT_MESSAGE_SUFFIX = "\uAC8C\uC2DC\uAE00\uC5D0 \uB313\uAE00\uC744 \uB0A8\uACBC\uC2B5\uB2C8\uB2E4.";

    private final WatchListRepository watchListRepository;
    private final StockAlertRepository stockAlertRepository;
    private final UserDao userDao;

    @Transactional
    public boolean addWatch(Integer userNum, String stockCode, String stockName) {
        if (watchListRepository.existsByUserNumAndStockCode(userNum, stockCode)) {
            return false;
        }

        WatchList watch = new WatchList();
        watch.setUserNum(userNum);
        watch.setStockCode(stockCode);
        watch.setStockName(stockName);
        watchListRepository.save(watch);
        return true;
    }

    @Transactional
    public void removeWatch(Integer userNum, String stockCode) {
        watchListRepository.deleteByUserNumAndStockCode(userNum, stockCode);
    }

    public boolean isWatching(Integer userNum, String stockCode) {
        return watchListRepository.existsByUserNumAndStockCode(userNum, stockCode);
    }

    public List<WatchList> getWatchList(Integer userNum) {
        return watchListRepository.findByUserNumOrderByCreatedAtDesc(userNum);
    }

    public List<StockAlert> getAlerts(Integer userNum) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        return stockAlertRepository.findByUserNumAndCreatedAtAfterOrderByCreatedAtDesc(userNum, since);
    }

    public long getUnreadCount(Integer userNum) {
        return stockAlertRepository.countByUserNumAndAlertReadFalse(userNum);
    }

    @Transactional
    public void markAllRead(Integer userNum) {
        stockAlertRepository.markAllRead(userNum);
    }

    public Map<String, Object> buildAlertPanel(Integer userNum) {
        List<StockAlert> alerts = getAlerts(userNum);
        long unreadCount = getUnreadCount(userNum);

        List<Map<String, Object>> items = alerts.stream()
            .map(this::toAlertItem)
            .collect(Collectors.toList());

        // 알림창 기능
        Map<String, Boolean> notifySettings = new HashMap<>();
        notifySettings.put("stock", isNotifyEnabled(userNum));
        notifySettings.put("comment", isCommentNotifyEnabled(userNum));

        Map<String, Object> result = new HashMap<>();
        result.put("unreadCount", unreadCount);
        result.put("alerts", items);
        result.put("notifySettings", notifySettings);
        return result;
    }

    public boolean isNotifyEnabled(Integer userNum) {
        Integer status = userDao.findNotifyStockStatusByNum(userNum);
        return status != null && status == 1;
    }

    public boolean isCommentNotifyEnabled(Integer userNum) {
        Integer status = userDao.findNotifyCommentStatusByNum(userNum);
        return status != null && status == 1;
    }

    @Transactional
    public void updateNotifySetting(Integer userNum, String type, int status) {
        userDao.updateNotifySetting(userNum, type, status);
    }

    private Map<String, Object> toAlertItem(StockAlert alert) {
        Map<String, Object> item = new HashMap<>();
        boolean isCommunityComment = ALERT_TYPE_COMMENT.equals(alert.getAlertType());

        item.put("id", alert.getId());
        item.put("stockCode", alert.getStockCode());
        item.put("stockName", alert.getStockName());
        item.put("alertType", alert.getAlertType());
        item.put("changeRate", alert.getChangeRate());
        item.put("price", alert.getPrice());
        item.put("read", alert.isAlertRead());
        item.put("createdAt", alert.getCreatedAt().toString());

        if (isCommunityComment) {
            item.put("link", "/community/detail?board_id=" + alert.getStockCode());
            item.put("title", COMMENT_TITLE);
            item.put("message", alert.getChangeRate() + "\uB2D8\uC774 '" + alert.getStockName() + "' " + COMMENT_MESSAGE_SUFFIX);
            return item;
        }

        item.put("link", "/market/" + alert.getStockCode());
        item.put("title", alert.getStockName());
        item.put("message", buildStockAlertMessage(alert));
        return item;
    }

    private String buildStockAlertMessage(StockAlert alert) {
        String prefix = ALERT_TYPE_RISE.equals(alert.getAlertType()) ? "\u25B2 " : "\u25BC ";
        return prefix
            + alert.getChangeRate()
            + "% "
            + alert.getAlertType()
            + " - "
            + NumberFormatHelper.formatPrice(alert.getPrice())
            + "\uC6D0";
    }

    static class NumberFormatHelper {
        static String formatPrice(String price) {
            try {
                return String.format("%,d", Long.parseLong(price));
            } catch (Exception exception) {
                return price == null ? "0" : price;
            }
        }
    }
}
