


function initAlertPanel() {
    const bellBtn    = document.getElementById('bellBtn');
    const alertPanel = document.getElementById('alertPanel');
    if (!bellBtn || !alertPanel) return;

    bellBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const isOpen = alertPanel.classList.contains('open');
        alertPanel.classList.toggle('open');
        if (!isOpen) {
            const activeTab = alertPanel.querySelector('.alertTab.active')?.dataset.tab || 'history';
            if (activeTab === 'history') {
                await loadAlertPanel();
                await fetch('/api/alerts/read', { method: 'POST' });
                document.getElementById('bellBadge')?.classList.remove('show');
            } else {
                await loadWatchlistPanel();
            }
        }
    });

    document.addEventListener('click', (e) => {
        if (!e.target.closest('.bellWrapper')) alertPanel.classList.remove('open');
    });

    alertPanel.querySelectorAll('.alertTab').forEach(tab => {
        tab.addEventListener('click', async () => {
            alertPanel.querySelectorAll('.alertTab').forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            const type = tab.dataset.tab;
            document.getElementById('alertTabHistory').style.display   = type === 'history'   ? '' : 'none';
            document.getElementById('alertTabWatchlist').style.display = type === 'watchlist' ? '' : 'none';
            if (type === 'history') {
                await loadAlertPanel();
                await fetch('/api/alerts/read', { method: 'POST' });
                document.getElementById('bellBadge')?.classList.remove('show');
            } else {
                await loadWatchlistPanel();
            }
        });
    });

    document.getElementById('alertReadAllBtn')?.addEventListener('click', async () => {
        await fetch('/api/alerts/read', { method: 'POST' });
        document.querySelectorAll('.alertItem').forEach(el => el.classList.remove('unread'));
        document.getElementById('bellBadge')?.classList.remove('show');
    });

    refreshBadge();
    setInterval(refreshBadge, 60000);
}

async function refreshBadge() {
    try {
        const res   = await fetch('/api/alerts');
        const data  = await res.json();
        const badge = document.getElementById('bellBadge');
        if (!badge) return;
        if (data.unreadCount > 0) {
            badge.textContent = data.unreadCount > 99 ? '99+' : data.unreadCount;
            badge.classList.add('show');
        } else {
            badge.classList.remove('show');
        }
    } catch (e) {}
}

async function loadAlertPanel() {
    // 알림 목록을 뿌릴 컨테이너
    const list = document.getElementById('alertList');
    if (!list) return;

    try {
        // 서버에서 알림 목록 데이터
        const res = await fetch('/api/alerts');
        // 응답 json으로 변환
        const data = await res.json();
        // 배열 없으면 빈 배열
        const alerts = data.alerts || [];

        if (alerts.length === 0) {
            list.innerHTML = '<div class="alertEmpty">알림이 없습니다</div>';
            return;
        }

        list.innerHTML = alerts.map(a => {
            // 커뮤니티 댓글 알림일 경우
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

            // 주식 알림 표시 로직
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
        // 알림을 불러오다 실패하면 오류 문구를 보여줍니다.
        list.innerHTML = '<div class="alertEmpty">불러오기 실패</div>';
    }
}

async function loadWatchlistPanel() {
    const list = document.getElementById('watchlistPanelList');
    if (!list) return;
    try {
        const res   = await fetch('/api/watchlist');
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

    // market.js watchingSet 연동 (market 페이지에서만 존재)
    if (typeof watchingSet !== 'undefined') watchingSet.delete(code);

    btn.closest('.watchlistPanelItem').remove();

    // 리스트 하트 버튼 갱신
    document.querySelectorAll(`.heartBtn[data-code="${code}"]`).forEach(h => {
        h.classList.remove('watching');
        h.textContent = '♡';
    });

    // 패널 관심종목 버튼 갱신 (market 페이지)
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
        if (diff < 1)    return '방금 전';
        if (diff < 60)   return diff + '분 전';
        if (diff < 1440) return Math.floor(diff / 60) + '시간 전';
        return Math.floor(diff / 1440) + '일 전';
    } catch (e) { return ''; }
}

document.addEventListener('DOMContentLoaded', () => {
    initAlertPanel();
});