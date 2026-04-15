let badgeRefreshTimer = null;
let alertToastInitialized = false;
let latestSeenAlertId = null;
let latestAlertItems = [];
let pendingBadgeAlertIds = new Set();
// 알림창 기능
let alertTypePreferences = {
    stock: true,
    comment: true
};

const ALERT_POLL_INTERVAL = 5000;
const ALERT_NOTIFICATION_CLOSE_DELAY = 7000;
const ALERT_NOTIFICATION_TAG_PREFIX = 'stoxle-alert-';

function createAlertPollingTask(task) {
    let running = false;

    return async () => {
        if (running) {
            return;
        }

        running = true;
        try {
            await task();
        } finally {
            running = false;
        }
    };
}


function initAlertPanel() {
    const bellBtn = document.getElementById('bellBtn');
    const alertPanel = document.getElementById('alertPanel');
    if (!bellBtn || !alertPanel) return;

    initSystemNotificationControls();
    const refreshBadgeTask = createAlertPollingTask(refreshBadge);

    bellBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        e.stopPropagation();

        const isOpen = alertPanel.classList.contains('open');
        alertPanel.classList.toggle('open');

        if (!isOpen) {
            const activeTab = alertPanel.querySelector('.alertTab.active')?.dataset.tab || 'history';
            if (activeTab === 'history') {
                await loadAlertPanel();
                await fetch('/api/alerts/read', { method: 'POST' });
                acknowledgeVisibleAlerts();
            } else {
                await loadWatchlistPanel();
            }
        }
    });

    document.addEventListener('visibilitychange', () => {
        updateNotificationPermissionUi();
        if (!document.hidden) {
            refreshBadgeTask();
        }
    });

    window.addEventListener('focus', () => {
        updateNotificationPermissionUi();
        refreshBadgeTask();
    });

    document.addEventListener('click', (e) => {
        if (!e.target.closest('.inlineAlert') && !e.target.closest('.bellWrapper')) {
            alertPanel.classList.remove('open');
        }
    });

    alertPanel.addEventListener('click', (e) => {
        e.stopPropagation();
    });

    alertPanel.querySelectorAll('.alertTab').forEach(tab => {
        tab.addEventListener('click', async () => {
            alertPanel.querySelectorAll('.alertTab').forEach(t => t.classList.remove('active'));
            tab.classList.add('active');

            const type = tab.dataset.tab;
            document.getElementById('alertTabHistory').style.display = type === 'history' ? '' : 'none';
            document.getElementById('alertTabWatchlist').style.display = type === 'watchlist' ? '' : 'none';

            if (type === 'history') {
                await loadAlertPanel();
                await fetch('/api/alerts/read', { method: 'POST' });
                acknowledgeVisibleAlerts();
            } else {
                await loadWatchlistPanel();
            }
        });
    });

    document.getElementById('alertReadAllBtn')?.addEventListener('click', async () => {
        await fetch('/api/alerts/read', { method: 'POST' });
        document.querySelectorAll('.alertItem').forEach(el => el.classList.remove('unread'));
        acknowledgeVisibleAlerts();
    });

    refreshBadgeTask();

    if (badgeRefreshTimer) clearInterval(badgeRefreshTimer);
    badgeRefreshTimer = setInterval(refreshBadgeTask, ALERT_POLL_INTERVAL);
}

async function refreshBadge() {
    try {
        const res = await fetch('/api/alerts');
        const data = await res.json();
        const badge = document.getElementById('bellBadge');
        if (!badge) return;

        syncAlertTypePreferences(data.notifySettings);
        latestAlertItems = Array.isArray(data.alerts) ? data.alerts : [];
        handleAlertToasts(latestAlertItems);
        syncPendingBadgeAlerts(latestAlertItems);
        updateBellBadge(latestAlertItems);
    } catch (e) {}
}

function canUseBrowserNotifications() {
    return 'Notification' in window
        && (window.isSecureContext
            || location.hostname === 'localhost'
            || location.hostname === '127.0.0.1'
            || location.hostname === '[::1]');
}

function getBrowserNotificationState() {
    if (!canUseBrowserNotifications()) {
        return 'unsupported';
    }

    return Notification.permission;
}

function isBrowserNotificationGranted() {
    return getBrowserNotificationState() === 'granted';
}

function initSystemNotificationControls() {
    const permissionBtn = document.getElementById('alertPermissionBtn');
    if (permissionBtn) {
        permissionBtn.addEventListener('click', async (event) => {
            event.stopPropagation();
            const granted = await requestSystemNotificationPermission();
            if (granted) {
                showPermissionConfirmationNotification();
            }
        });
    }

    window.addEventListener('stoxle-notification-setting-change', (event) => {
        syncAlertTypePreferences(event.detail);
        updateBellBadge(latestAlertItems);
    });

    updateNotificationPermissionUi();
}

function syncAlertTypePreferences(settings) {
    if (!settings || typeof settings !== 'object') {
        return;
    }

    if (Object.prototype.hasOwnProperty.call(settings, 'stock')) {
        alertTypePreferences.stock = settings.stock === true || settings.stock === 1;
    }

    if (Object.prototype.hasOwnProperty.call(settings, 'comment')) {
        alertTypePreferences.comment = settings.comment === true || settings.comment === 1;
    }

    updateNotificationPermissionUi();
}

function isAlertTypeEnabled(alert) {
    const preferenceKey = alert?.alertType === '댓글' ? 'comment' : 'stock';
    return alertTypePreferences[preferenceKey] !== false;
}

function getVisibleUnreadCount(alerts) {
    if (!Array.isArray(alerts)) {
        return 0;
    }

    return alerts.filter(alert =>
        alert?.id != null
        && pendingBadgeAlertIds.has(Number(alert.id))
        && isAlertTypeEnabled(alert)
    ).length;
}

function updateBellBadge(alerts) {
    const badge = document.getElementById('bellBadge');
    if (!badge) {
        return;
    }

    const unreadCount = getVisibleUnreadCount(alerts);
    if (unreadCount > 0) {
        badge.textContent = unreadCount > 99 ? '99+' : unreadCount;
        badge.classList.add('show');
        return;
    }

    badge.classList.remove('show');
}

function syncPendingBadgeAlerts(alerts) {
    if (!Array.isArray(alerts)) {
        return;
    }

    const currentAlertsById = new Map();
    alerts.forEach(alert => {
        if (alert?.id == null) {
            return;
        }

        const numericId = Number(alert.id);
        if (!Number.isFinite(numericId)) {
            return;
        }

        currentAlertsById.set(numericId, alert);
        if (!alert.read) {
            pendingBadgeAlertIds.add(numericId);
        }
    });

    Array.from(pendingBadgeAlertIds).forEach(alertId => {
        const currentAlert = currentAlertsById.get(alertId);
        if (!currentAlert || currentAlert.read) {
            pendingBadgeAlertIds.delete(alertId);
        }
    });
}

function acknowledgeVisibleAlerts() {
    pendingBadgeAlertIds.clear();
    latestAlertItems = latestAlertItems.map(alert => ({
        ...alert,
        read: true
    }));
    updateBellBadge(latestAlertItems);
}

async function requestSystemNotificationPermission() {
    if (!canUseBrowserNotifications()) {
        updateNotificationPermissionUi();
        return false;
    }

    if (Notification.permission === 'granted') {
        updateNotificationPermissionUi();
        return true;
    }

    if (Notification.permission === 'denied') {
        updateNotificationPermissionUi();
        return false;
    }

    try {
        const permission = await Notification.requestPermission();
        updateNotificationPermissionUi();
        return permission === 'granted';
    } catch (e) {
        updateNotificationPermissionUi();
        return false;
    }
}

function updateNotificationPermissionUi() {
    const permissionBar = document.getElementById('alertPermissionBar');
    const permissionText = document.getElementById('alertPermissionText');
    const permissionBtn = document.getElementById('alertPermissionBtn');
    if (!permissionBar || !permissionText || !permissionBtn) {
        return;
    }

    const permission = getBrowserNotificationState();
    permissionBar.dataset.permission = permission;
    permissionBar.hidden = false;
    permissionBtn.hidden = false;
    permissionBtn.disabled = false;

    if (permission === 'granted') {
        permissionBar.hidden = true;
        return;
    }

    if (permission === 'denied') {
        permissionText.textContent = '브라우저 알림이 차단되어 있습니다. 주소창의 사이트 권한에서 알림을 허용해 주세요.';
        permissionBtn.textContent = '설정 확인';
        permissionBtn.disabled = true;
        return;
    }

    if (permission === 'unsupported') {
        permissionText.textContent = '브라우저 알림은 HTTPS 또는 localhost 환경에서만 사용할 수 있습니다.';
        permissionBtn.hidden = true;
        return;
    }

    permissionText.textContent = '브라우저 알림을 켜면 사이트를 열어둔 상태에서 PC 알림으로 받을 수 있습니다.';
    permissionBtn.textContent = '브라우저 알림 켜기';
}

function showPermissionConfirmationNotification() {
    showSystemNotification({
        id: 'permission-preview',
        title: '브라우저 알림 활성화',
        message: '이제 새 알림이 PC 알림으로 표시됩니다.',
        force: true
    });
}

function shouldShowSystemNotification(alert) {
    if (!isBrowserNotificationGranted()) {
        return false;
    }

    if (!alert?.force && !isAlertTypeEnabled(alert)) {
        return false;
    }

    return true;
}

function showSystemNotification(alert) {
    if (!shouldShowSystemNotification(alert)) {
        return;
    }

    const title = alert?.title || alert?.stockName || '새 알림';
    const body = alert?.message || '';
    const tagId = alert?.id != null ? String(alert.id) : Date.now().toString();

    try {
        const notification = new Notification(title, {
            body,
            tag: ALERT_NOTIFICATION_TAG_PREFIX + tagId
        });

        notification.onclick = () => {
            notification.close();
            window.focus();

            if (alert?.link) {
                const targetUrl = new URL(alert.link, window.location.origin).href;
                if (location.href !== targetUrl) {
                    location.href = targetUrl;
                }
            }
        };

        window.setTimeout(() => notification.close(), ALERT_NOTIFICATION_CLOSE_DELAY);
    } catch (e) {}
}

function handleAlertToasts(alerts) {
    if (!Array.isArray(alerts) || alerts.length === 0) {
        return;
    }

    const sortedAlerts = alerts
        .filter(alert => alert && alert.id != null)
        .slice()
        .sort((a, b) => Number(a.id) - Number(b.id));

    if (sortedAlerts.length === 0) {
        return;
    }

    if (!alertToastInitialized) {
        latestSeenAlertId = Number(sortedAlerts[sortedAlerts.length - 1].id);
        alertToastInitialized = true;
        return;
    }

    const newAlerts = sortedAlerts.filter(alert =>
        !alert.read && Number(alert.id) > Number(latestSeenAlertId || 0)
    );

    if (newAlerts.length > 0) {
        newAlerts.forEach(alert => {
            showSystemNotification(alert);
        });
        latestSeenAlertId = Number(newAlerts[newAlerts.length - 1].id);
        return;
    }

    latestSeenAlertId = Math.max(
        Number(latestSeenAlertId || 0),
        Number(sortedAlerts[sortedAlerts.length - 1].id)
    );
}

async function loadAlertPanel() {
    const list = document.getElementById('alertList');
    if (!list) return;

    try {
        const res = await fetch('/api/alerts');
        const data = await res.json();
        const alerts = data.alerts || [];

        if (alerts.length === 0) {
            list.innerHTML = '<div class="alertEmpty">알림이 없습니다</div>';
            return;
        }

        list.innerHTML = alerts.map(a => {
            if (a.alertType === '댓글') {
                return `
                <div class="alertItem ${!a.read ? 'unread' : ''}"
                     onclick="location.href='${a.link || `/community/detail?board_id=${a.stockCode}`}'">
                    <div class="alertDot up"></div>
                    <div class="alertItemText">
                        <div class="alertItemName">${a.title || '새 댓글 알림'}</div>
                        <div class="alertItemDesc">
                            ${a.message || `${a.changeRate}님이 댓글을 남겼습니다.`}
                        </div>
                        <div class="alertItemTime">${formatAlertTime(a.createdAt)}</div>
                    </div>
                </div>`;
            }

            const cls = a.alertType === '상승' ? 'up' : 'down';
            const sign = a.alertType === '상승' ? '▲' : '▼';

            return `
            <div class="alertItem ${!a.read ? 'unread' : ''}"
                 onclick="location.href='/market/${a.stockCode}'">
                <div class="alertDot ${cls}"></div>
                <div class="alertItemText">
                    <div class="alertItemName">${a.stockName}</div>
                    <div class="alertItemDesc">
                        ${sign} ${a.changeRate}% ${a.alertType} · ${Number(a.price).toLocaleString()}원
                    </div>
                    <div class="alertItemTime">${formatAlertTime(a.createdAt)}</div>
                </div>
            </div>`;
        }).join('');
    } catch (e) {
        list.innerHTML = '<div class="alertEmpty">불러오기 실패</div>';
    }
}

async function loadWatchlistPanel() {
    const list = document.getElementById('watchlistPanelList');
    if (!list) return;

    try {
        const res = await fetch('/api/watchlist');
        const items = await res.json();

        if (!items || items.length === 0) {
            list.innerHTML = '<div class="alertEmpty">등록된 관심종목이 없습니다<br><small style="color:#bbb;font-size:11px;margin-top:6px;display:block">종목 페이지에서 ♡ 버튼으로 추가하세요</small></div>';
            return;
        }

        list.innerHTML = items.map(w => `
            <div class="watchlistPanelItem" onclick="location.href='/market/${w.stockCode}'">
                <div class="watchlistItemInfo">
                    <div class="watchlistItemName">${w.stockName}</div>
                    <div class="watchlistItemCode">${w.stockCode} &nbsp;·&nbsp; ±3% 알림 설정됨</div>
                </div>
                <button class="watchlistRemoveBtn"
                        data-code="${w.stockCode}" data-name="${w.stockName}"
                        onclick="event.stopPropagation(); removeFromPanel(this)">
                    알림 해제
                </button>
            </div>`).join('');
    } catch (e) {
        list.innerHTML = '<div class="alertEmpty">불러오기 실패</div>';
    }
}

async function removeFromPanel(btn) {
    const code = btn.dataset.code;
    await fetch(`/api/watchlist/${code}`, { method: 'DELETE' });

    if (typeof watchingSet !== 'undefined') watchingSet.delete(code);

    btn.closest('.watchlistPanelItem').remove();

    document.querySelectorAll(`.heartBtn[data-code="${code}"]`).forEach(h => {
        h.classList.remove('watching');
        h.textContent = '♡';
    });

    if (typeof selectedCode !== 'undefined' && code === selectedCode) {
        const panelBtn = document.getElementById('panelWatchBtn');
        if (panelBtn && typeof setPanelWatchBtn === 'function') setPanelWatchBtn(panelBtn, false);
        if (typeof updateAlertNote === 'function') updateAlertNote(false);
    }

    const list = document.getElementById('watchlistPanelList');
    if (list && list.querySelectorAll('.watchlistPanelItem').length === 0) {
        list.innerHTML = '<div class="alertEmpty">등록된 관심종목이 없습니다<br><small style="color:#bbb;font-size:11px;margin-top:6px;display:block">종목 페이지에서 ♡ 버튼으로 추가하세요</small></div>';
    }
}

function formatAlertTime(isoStr) {
    try {
        const diff = Math.floor((new Date() - new Date(isoStr)) / 60000);
        if (diff < 1) return '방금 전';
        if (diff < 60) return diff + '분 전';
        if (diff < 1440) return Math.floor(diff / 60) + '시간 전';
        return Math.floor(diff / 1440) + '일 전';
    } catch (e) {
        return '';
    }
}

document.addEventListener('DOMContentLoaded', () => {
    initAlertPanel();
});
