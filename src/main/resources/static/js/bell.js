function initAlertPanel() {
    const bellBtn = document.getElementById('bellBtn');
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
            document.getElementById('alertTabHistory').style.display = type === 'history' ? '' : 'none';
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
        const res = await fetch('/api/alerts');
        const data = await res.json();
        const badge = document.getElementById('bellBadge');
        if (!badge) return;

        if (data.unreadCount > 0) {
            badge.textContent = data.unreadCount > 99 ? '99+' : data.unreadCount;
            badge.classList.add('show');
        } else {
            badge.classList.remove('show');
        }
    } catch (e) {
    }
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
            const cls = a.alertType === '상승' ? 'up' : a.alertType === '하락' ? 'down' : 'up';
            const link = a.link || '#';
            const title = a.title || a.stockName || '알림';
            const message = a.message || '';

            return `
            <div class="alertItem ${!a.read ? 'unread' : ''}"
                 onclick="location.href='${link}'">
                <div class="alertDot ${cls}"></div>
                <div class="alertItemText">
                    <div class="alertItemName">${title}</div>
                    <div class="alertItemDesc">${message}</div>
                    <div class="alertItemTime">${formatAlertTime(a.createdAt)}</div>
                </div>
            </div>`;
        }).join('');
    } catch (e) {
        list.innerHTML = '<div class="alertEmpty">불러오기 실패</div>';
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
