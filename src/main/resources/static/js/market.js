// ════════════════════════════════════════════════════════
//  market.js — 종목 리스트 + 차트 패널 + 알림 공통
// ════════════════════════════════════════════════════════

// ── 전역 상태 ────────────────────────────────────────────

let currentType       = 'trade';  // 현재 필터 (trade | fluctuation)
let selectedCode      = '';       // 선택된 종목코드
let selectedName      = '';       // 선택된 종목명
let panelChart        = null;     // 패널 차트 인스턴스
let panelTab          = 'daily';  // 패널 차트 탭
let watchingSet       = new Set(); // 관심종목 코드 집합
let pricePollingTimer = null;     // 현재가 폴링 타이머
let allStocks         = [];       // 현재 로드된 전체 종목 (검색 필터용)

// ════════════════════════════════════════════════════════
//  종목 리스트 로딩
// ════════════════════════════════════════════════════════

async function loadMarketData(type) {
    currentType = type || 'trade';
    const body     = document.getElementById('stockListBody');
    const error    = document.getElementById('listError');
    const noResult = document.getElementById('listNoResult');
    if (!body) return;

    error.style.display    = 'none';
    if (noResult) noResult.style.display = 'none';
    renderSkeletons(body);

    const endpoint = currentType === 'fluctuation'
        ? '/api/stock/top-fluctuation-full'
        : '/api/stock/top-trade';

    try {
        const res    = await fetch(endpoint);
        const stocks = await res.json();

        if (!stocks || stocks.length === 0) {
            body.innerHTML = '';
            error.style.display = 'block';
            return;
        }

        // 클라이언트 측 정렬 (API 정렬 신뢰 불가)
        if (currentType === 'trade') {
            stocks.sort((a, b) => parseFloat(b.tradeAmount || 0) - parseFloat(a.tradeAmount || 0));
        } else {
            // 등락률순: 상승률 높은 순 → 하락률 높은 순
            stocks.sort((a, b) => parseFloat(b.changeRate || 0) - parseFloat(a.changeRate || 0));
        }

        allStocks = stocks;

        // 검색 중이면 검색 필터 적용
        const keyword = document.getElementById('stockSearchInput')?.value.trim();
        if (keyword) {
            filterStockList(keyword);
        } else {
            renderStockList(body, stocks);
        }

    } catch (e) {
        console.error('종목 로딩 실패:', e);
        body.innerHTML = '';
        error.style.display = 'block';
    }
}

function renderSkeletons(container) {
    container.innerHTML = Array.from({ length: 15 }, () => `
        <div class="stockRowSkeleton">
            <div class="skelBlock skelRank"></div>
            <div class="skelBlock skelName"></div>
            <div class="skelBlock skelPrice"></div>
            <div class="skelBlock skelRate"></div>
            <div class="skelBlock skelTrade"></div>
        </div>`).join('');
}

/**
 * 거래대금 억/조 단위 변환
 * KIS acml_tr_pbmn = 원 단위 (만원 아님)
 */
function formatTradeAmount(raw) {
    if (!raw || raw === '0' || raw === '') return '-';
    const won = parseFloat(raw);
    if (isNaN(won) || won === 0) return '-';
    if (won >= 1_000_000_000_000) return (won / 1_000_000_000_000).toFixed(1) + '조';
    if (won >= 100_000_000)       return Math.floor(won / 100_000_000) + '억';
    if (won >= 10_000)            return Math.floor(won / 10_000) + '만';
    return won.toLocaleString() + '원';
}

/**
 * 거래량 만/억 단위 변환 (등락률 탭용)
 */
function formatVolume(raw) {
    if (!raw || raw === '0' || raw === '') return '-';
    const v = parseFloat(raw);
    if (isNaN(v) || v === 0) return '-';
    if (v >= 100_000_000) return (v / 100_000_000).toFixed(1) + '억주';
    if (v >= 10_000)      return Math.floor(v / 10_000) + '만주';
    return v.toLocaleString() + '주';
}

function renderStockList(container, stocks) {
    if (!stocks || stocks.length === 0) {
        container.innerHTML = '';
        const noResult = document.getElementById('listNoResult');
        if (noResult) noResult.style.display = 'block';
        return;
    }
    const noResult = document.getElementById('listNoResult');
    if (noResult) noResult.style.display = 'none';

    // 컬럼 헤더 동적 변경
    const colTradeHeader = document.getElementById('colTradeHeader');
    if (colTradeHeader) {
        colTradeHeader.textContent = currentType === 'trade' ? '거래대금' : '거래량';
    }

    container.innerHTML = stocks.map((s, i) => {
        const code       = s.stockCode || '';
        const name       = s.stockName || '-';
        const price      = s.currentPrice ? Number(s.currentPrice).toLocaleString() + '원' : '-';
        const rate       = parseFloat(s.changeRate || '0');
        const cls        = rate > 0 ? 'up' : rate < 0 ? 'down' : 'flat';
        const arrow      = rate > 0 ? '▲' : rate < 0 ? '▼' : '-';
        // 거래대금순: tradeAmount, 등락률순/검색결과: volume 표시
        const lastCol    = currentType === 'trade'
            ? formatTradeAmount(s.tradeAmount)
            : formatVolume(s.volume);
        const isWatching = watchingSet.has(code);
        const rank       = i + 1;

        return `
        <div class="stockRow ${selectedCode === code ? 'selected' : ''}"
             data-code="${code}" data-name="${name}">
            <div class="colRank">
                <span class="rankNum">${s._fromDb ? '' : rank}</span>
                <button class="heartBtn ${isWatching ? 'watching' : ''}"
                        data-code="${code}" data-name="${name}"
                        title="${isWatching ? '관심종목 해제' : '관심종목 + 알림 등록'}">
                    ${isWatching ? '♥' : '♡'}
                </button>
            </div>
            <div class="colName">
                <div class="rowStockName">${name}</div>
                <div class="rowStockCode">${code}</div>
            </div>
            <div class="colPrice">${price}</div>
            <div class="colRate ${cls}">${rate !== 0 ? arrow + ' ' + Math.abs(rate).toFixed(2) + '%' : '-'}</div>
            <div class="colTrade">${lastCol}</div>
        </div>`;
    }).join('');

    // 행 클릭
    container.querySelectorAll('.stockRow').forEach(row => {
        row.addEventListener('click', (e) => {
            if (e.target.closest('.heartBtn')) return;
            const code = row.dataset.code;
            const name = row.dataset.name;
            if (code) selectStock(code, name, row);
        });
    });

    // 하트 클릭
    container.querySelectorAll('.heartBtn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleWatch(btn.dataset.code, btn.dataset.name, btn);
        });
    });
}

// ── 검색 드롭다운 ────────────────────────────────────────

let searchDebounce = null;

function hideSearchDropdown() {
    const dd = document.getElementById('searchDropdown');
    if (dd) dd.style.display = 'none';
}

function showSearchDropdown(items) {
    const dd = document.getElementById('searchDropdown');
    if (!dd) return;

    if (!items || items.length === 0) {
        dd.innerHTML = '<div class="searchDropdownEmpty">검색 결과가 없습니다</div>';
        dd.style.display = 'block';
        return;
    }

    dd.innerHTML = items.map(item => {
        const code   = item.code   || item.stockCode || '';
        const name   = item.name   || item.stockName || '';
        const market = item.market || '';
        return `
        <div class="searchDropdownItem" data-code="${code}" data-name="${name}">
            <div class="searchDropdownLeft">
                <span class="searchDropdownName">${name}</span>
                ${market ? `<span class="searchMarketBadge">${market}</span>` : ''}
            </div>
            <span class="searchDropdownCode">${code}</span>
        </div>`;
    }).join('');

    // 드롭다운 항목 클릭
    dd.querySelectorAll('.searchDropdownItem').forEach(el => {
        el.addEventListener('click', () => {
            const code = el.dataset.code;
            const name = el.dataset.name;
            document.getElementById('stockSearchInput').value = name;
            document.getElementById('searchClearBtn').style.display = '';
            hideSearchDropdown();
            // 종목 선택 → 차트 패널 표시
            const matchedRow = document.querySelector(`.stockRow[data-code="${code}"]`);
            selectStock(code, name, matchedRow);
        });
    });

    dd.style.display = 'block';
}

async function runSearch(keyword) {
    if (!keyword) {
        hideSearchDropdown();
        return;
    }

    const kw = keyword.toLowerCase();

    // 1차: 현재 20개 목록에서 검색
    const localHits = allStocks.filter(s =>
        (s.stockName || '').toLowerCase().includes(kw) ||
        (s.stockCode || '').includes(kw)
    );

    if (localHits.length >= 3) {
        showSearchDropdown(localHits.slice(0, 10));
        return;
    }

    // 2차: DB 전체 검색
    try {
        const res   = await fetch(`/api/stock/search?keyword=${encodeURIComponent(keyword)}`);
        const items = await res.json();
        // 로컬 결과 + DB 결과 합치되 중복 제거
        const merged = [...localHits];
        const localCodes = new Set(localHits.map(s => s.stockCode || s.code));
        (items || []).forEach(item => {
            if (!localCodes.has(item.code)) merged.push(item);
        });
        showSearchDropdown(merged.slice(0, 10));
    } catch (e) {
        if (localHits.length > 0) showSearchDropdown(localHits);
        else hideSearchDropdown();
    }
}

// ════════════════════════════════════════════════════════
//  종목 선택 → 차트 패널 표시
// ════════════════════════════════════════════════════════

async function selectStock(code, name, rowEl) {
    // 선택 하이라이트
    document.querySelectorAll('.stockRow').forEach(r => r.classList.remove('selected'));
    if (rowEl) rowEl.classList.add('selected');

    selectedCode = code;
    selectedName = name;

    // 패널 표시
    document.getElementById('chartPanelEmpty').style.display = 'none';
    document.getElementById('chartPanelContent').style.display = 'block';

    // 종목 정보 세팅
    document.getElementById('panelStockName').textContent = name;
    document.getElementById('panelStockCode').textContent = code;
    document.getElementById('panelDetailLink').href = `/market/${code}`;

    // 관심종목 버튼 상태
    const watchBtn = document.getElementById('panelWatchBtn');
    const isWatching = watchingSet.has(code);
    setPanelWatchBtn(watchBtn, isWatching);

    // 알림 안내 표시
    updateAlertNote(isWatching);

    // 패널 차트 탭 초기화
    panelTab = 'daily';
    document.querySelectorAll('.panelChartTab').forEach(b =>
        b.classList.toggle('active', b.dataset.tab === 'daily'));

    // 현재가 + 차트 로딩
    await updatePanelPrice();
    await initPanelChart();

    // 기존 폴링 정리 후 새로 시작
    if (pricePollingTimer) clearInterval(pricePollingTimer);
    pricePollingTimer = setInterval(updatePanelPrice, 5000);
}

// ── 패널 현재가 업데이트 ─────────────────────────────────

async function updatePanelPrice() {
    if (!selectedCode) return;
    try {
        const res  = await fetch(`/api/stock/${selectedCode}`);
        const data = await res.json();

        const price  = Number(data.currentPrice || 0);
        // 전일 대비 등락률/변동금액을 API에서 직접 사용 (시가 대비 계산 X)
        const change = Number(data.priceChange || 0);
        const rate   = parseFloat(data.changeRate || '0').toFixed(2);
        const sign   = change >= 0 ? '+' : '';
        const arrow  = change >= 0 ? '▲' : '▼';
        const cls    = change >= 0 ? 'up' : 'down';

        setEl('panelCurrentPrice', `${price.toLocaleString()}원`);
        const rateEl = document.getElementById('panelChangeRate');
        if (rateEl) {
            rateEl.textContent = `${sign}${change.toLocaleString()} (${sign}${rate}%) ${arrow}`;
            rateEl.className   = `panelChangeRate ${cls}`;
        }
        setEl('panelOpen', Number(data.openPrice  || 0).toLocaleString());
        setEl('panelHigh', Number(data.highPrice  || 0).toLocaleString());
        setEl('panelLow',  Number(data.lowPrice   || 0).toLocaleString());
        setEl('panelVol',  Number(data.volume     || 0).toLocaleString());
    } catch (e) {}
}

// ── 패널 차트 ────────────────────────────────────────────

async function initPanelChart() {
    const data = await fetchPanelChartData();
    if (panelChart) { panelChart.destroy(); panelChart = null; }

    const canvas = document.getElementById('panelChart');
    if (!canvas) return;

    panelChart = new Chart(canvas.getContext('2d'), {
        type: 'line',
        data: {
            labels: data.labels,
            datasets: [{
                data: (data.closePrices || []).map(Number),
                borderColor: '#0E0F37',
                backgroundColor: 'rgba(14,15,55,0.07)',
                borderWidth: 2,
                tension: 0.4,
                fill: true,
                pointRadius: 0,
                pointHoverRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: { legend: { display: false } },
            scales: {
                x: { grid: { display: false }, ticks: { font: { size: 10 } } },
                y: {
                    grid: { color: '#f3f4f6' },
                    ticks: { callback: v => Number(v).toLocaleString(), font: { size: 10 } }
                }
            }
        }
    });
}

async function updatePanelChart() {
    const data = await fetchPanelChartData();
    if (!panelChart) return;
    panelChart.data.labels = data.labels;
    panelChart.data.datasets[0].data = (data.closePrices || []).map(Number);
    panelChart.update();
}

async function fetchPanelChartData() {
    let tab = panelTab;
    if ((tab === 'time' || tab === 'minute') && !isMarketOpen()) tab = 'daily';

    const ep = {
        daily:  `/api/stock/${selectedCode}/chart`,
        time:   `/api/stock/${selectedCode}/time`,
        minute: `/api/stock/${selectedCode}/minute`
    };
    try {
        const res  = await fetch(ep[tab]);
        const data = await res.json();
        if (!data.labels || data.labels.length === 0) {
            const fb = await fetch(`/api/stock/${selectedCode}/chart`);
            return await fb.json();
        }
        return data;
    } catch (e) {
        return { labels: [], closePrices: [] };
    }
}

// ════════════════════════════════════════════════════════
//  관심종목 + 알림 토글
// ════════════════════════════════════════════════════════

async function toggleWatch(code, name, triggerEl) {
    if (!code) return;
    const isWatching = watchingSet.has(code);

    if (isWatching) {
        await fetch(`/api/watchlist/${code}`, { method: 'DELETE' });
        watchingSet.delete(code);
    } else {
        await fetch('/api/watchlist', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ stockCode: code, stockName: name })
        });
        watchingSet.add(code);
    }

    const nowWatching = watchingSet.has(code);

    // 리스트 내 하트 버튼 갱신
    document.querySelectorAll(`.heartBtn[data-code="${code}"]`).forEach(btn => {
        btn.classList.toggle('watching', nowWatching);
        btn.textContent = nowWatching ? '♥' : '♡';
        btn.title = nowWatching ? '관심종목 해제' : '관심종목 + 알림 등록';
    });

    // 패널 버튼도 갱신
    if (code === selectedCode) {
        const panelBtn = document.getElementById('panelWatchBtn');
        if (panelBtn) setPanelWatchBtn(panelBtn, nowWatching);
        updateAlertNote(nowWatching);
    }

    // 뱃지 갱신
    refreshBadge();
}

function setPanelWatchBtn(btn, watching) {
    btn.textContent = watching ? '♥' : '♡';
    btn.classList.toggle('watching', watching);
    btn.title = watching ? '관심종목 해제' : '관심종목 + 알림 등록';
}

function updateAlertNote(watching) {
    const note = document.getElementById('panelAlertNote');
    if (!note) return;
    if (watching) {
        note.textContent = '✓ 관심종목 등록됨 · ±3% 변동 시 알림을 받습니다';
        note.className = 'panelAlertNote watching-note';
    } else {
        note.textContent = '♡ 관심종목 등록 시 ±3% 변동 알림을 받습니다';
        note.className = 'panelAlertNote show';
    }
}

// ════════════════════════════════════════════════════════
//  헤더 알림 패널 → bell.js 에서 처리 (모든 페이지 공통)
// ════════════════════════════════════════════════════════
// initAlertPanel, refreshBadge, loadAlertPanel, loadWatchlistPanel,
// removeFromPanel, formatAlertTime 은 bell.js 에 정의됨

// ════════════════════════════════════════════════════════
//  종목 상세 페이지 (marketDetail.html)
// ════════════════════════════════════════════════════════

let detailChart = null;
let detailTab   = 'daily';

function initDetailPage(code) {
    document.querySelectorAll('.detailChartTab').forEach(btn => {
        btn.addEventListener('click', async () => {
            document.querySelectorAll('.detailChartTab')
                    .forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            detailTab = btn.dataset.tab;
            if ((detailTab === 'time' || detailTab === 'minute') && !isMarketOpen()) {
                alert('시간별/분별 차트는 장 운영시간(09:00~15:30)에만 제공됩니다.');
                detailTab = 'daily';
                document.querySelectorAll('.detailChartTab')
                        .forEach(b => b.classList.toggle('active', b.dataset.tab === 'daily'));
            }
            await updateDetailChart(code);
        });
    });

    initDetailChart(code);
    setInterval(() => updateDetailPrice(code), 5000);
    setInterval(() => { if (isMarketOpen()) updateDetailChart(code); }, 10000);
}

async function initDetailChart(code) {
    const data = await fetchDetailChartData(code);
    if (detailChart) { detailChart.destroy(); detailChart = null; }
    const canvas = document.getElementById('detailChart');
    if (!canvas) return;
    detailChart = new Chart(canvas.getContext('2d'), {
        type: 'line',
        data: {
            labels: data.labels,
            datasets: [{
                data: (data.closePrices || []).map(Number),
                borderColor: '#0E0F37',
                backgroundColor: 'rgba(14,15,55,0.07)',
                borderWidth: 2,
                tension: 0.4,
                fill: true,
                pointRadius: 0,
                pointHoverRadius: 5
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: { legend: { display: false } },
            scales: {
                x: { grid: { display: false } },
                y: {
                    grid: { color: '#f3f4f6' },
                    ticks: { callback: v => Number(v).toLocaleString() }
                }
            }
        }
    });
}

async function updateDetailChart(code) {
    const data = await fetchDetailChartData(code);
    if (!detailChart) return;
    detailChart.data.labels = data.labels;
    detailChart.data.datasets[0].data = (data.closePrices || []).map(Number);
    detailChart.update();
}

async function fetchDetailChartData(code) {
    let tab = detailTab;
    if ((tab === 'time' || tab === 'minute') && !isMarketOpen()) tab = 'daily';
    const ep = {
        daily:  `/api/stock/${code}/chart`,
        time:   `/api/stock/${code}/time`,
        minute: `/api/stock/${code}/minute`
    };
    try {
        const res  = await fetch(ep[tab]);
        const data = await res.json();
        if (!data.labels || data.labels.length === 0) {
            const fb = await fetch(`/api/stock/${code}/chart`);
            return await fb.json();
        }
        return data;
    } catch (e) {
        return { labels: [], closePrices: [] };
    }
}

async function updateDetailPrice(code) {
    try {
        const res  = await fetch(`/api/stock/${code}`);
        const data = await res.json();
        const price  = Number(data.currentPrice || 0);
        const change = Number(data.priceChange  || 0);
        const rate   = parseFloat(data.changeRate || '0').toFixed(2);
        const sign   = change >= 0 ? '+' : '';
        const arrow  = change >= 0 ? '▲' : '▼';
        setEl('detailCurrentPrice', `${price.toLocaleString()} KRW`);
        const cel = document.getElementById('detailPriceChange');
        if (cel) {
            cel.textContent = `${sign}${change.toLocaleString()} (${sign}${rate}%) ${arrow}`;
            cel.className   = 'detailPriceChange ' + (change >= 0 ? 'up' : 'down');
        }
        setEl('detailOpenPrice', Number(data.openPrice || 0).toLocaleString());
        setEl('detailHighPrice', Number(data.highPrice || 0).toLocaleString());
        setEl('detailLowPrice',  Number(data.lowPrice  || 0).toLocaleString());
        setEl('detailVolume',    Number(data.volume    || 0).toLocaleString());
    } catch (e) {}
}

function initWatchBtn(code, stockName) {
    const btn = document.getElementById('watchBtn');
    if (!btn) return;
    btn.addEventListener('click', async () => {
        const watching = btn.classList.contains('watching');
        if (watching) {
            await fetch(`/api/watchlist/${code}`, { method: 'DELETE' });
            btn.classList.remove('watching');
            btn.innerHTML = '☆ 관심종목 추가';
        } else {
            await fetch('/api/watchlist', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ stockCode: code, stockName })
            });
            btn.classList.add('watching');
            btn.innerHTML = '★ 관심종목 등록됨';
        }
    });
}

// ════════════════════════════════════════════════════════
//  공통 유틸
// ════════════════════════════════════════════════════════

function isMarketOpen() {
    const now = new Date();
    if (now.getDay() === 0 || now.getDay() === 6) return false;
    const t = now.getHours() * 100 + now.getMinutes();
    return t >= 900 && t <= 1530;
}

function setEl(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
}

// ════════════════════════════════════════════════════════
//  DOMContentLoaded
// ════════════════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', async () => {
    // 알림 패널은 bell.js 에서 자동 초기화됨

    // ── 종목 리스트 페이지 ──
    if (document.getElementById('stockListBody')) {

        // 관심종목 목록 먼저 로드 (하트 상태 표시용)
        try {
            const res  = await fetch('/api/watchlist');
            const list = await res.json();
            list.forEach(w => watchingSet.add(w.stockCode));
        } catch (e) {}

        // 필터 버튼 (리스트 내부)
        document.querySelectorAll('.listFilterBtn').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.listFilterBtn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                // 검색어 초기화
                const input = document.getElementById('stockSearchInput');
                const clearBtn = document.getElementById('searchClearBtn');
                if (input) input.value = '';
                if (clearBtn) clearBtn.style.display = 'none';
                loadMarketData(btn.dataset.type);
            });
        });

        // 검색 입력 → 드롭다운 자동완성
        const searchInput = document.getElementById('stockSearchInput');
        const clearBtn    = document.getElementById('searchClearBtn');

        searchInput?.addEventListener('input', () => {
            const kw = searchInput.value.trim();
            clearBtn.style.display = kw ? '' : 'none';
            clearTimeout(searchDebounce);
            if (kw.length === 0) { hideSearchDropdown(); return; }
            searchDebounce = setTimeout(() => runSearch(kw), 250);
        });

        // ESC 키로 드롭다운 닫기
        searchInput?.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                hideSearchDropdown();
                searchInput.blur();
            }
        });

        clearBtn?.addEventListener('click', () => {
            searchInput.value = '';
            clearBtn.style.display = 'none';
            hideSearchDropdown();
            searchInput.focus();
        });

        // 외부 클릭 시 드롭다운 닫기
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.listSearchBarWrapper')) {
                hideSearchDropdown();
            }
        });

        // 패널 차트 탭
        document.querySelectorAll('.panelChartTab').forEach(btn => {
            btn.addEventListener('click', async () => {
                if (!selectedCode) return;
                document.querySelectorAll('.panelChartTab')
                        .forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                panelTab = btn.dataset.tab;
                if ((panelTab === 'time' || panelTab === 'minute') && !isMarketOpen()) {
                    alert('시간별/분별 차트는 장 운영시간(09:00~15:30)에만 제공됩니다.');
                    panelTab = 'daily';
                    document.querySelectorAll('.panelChartTab')
                            .forEach(b => b.classList.toggle('active', b.dataset.tab === 'daily'));
                }
                await updatePanelChart();
            });
        });

        // 패널 관심종목 버튼
        document.getElementById('panelWatchBtn')?.addEventListener('click', () => {
            if (selectedCode) toggleWatch(selectedCode, selectedName, null);
        });

        // 데이터 로딩
        await loadMarketData('trade');

        // 30초마다 리스트 가격 갱신
        setInterval(() => loadMarketData(currentType), 30000);
    }

    // ── 종목 상세 페이지 ──
    const detailCanvas = document.getElementById('detailChart');
    if (detailCanvas) {
        const code = detailCanvas.dataset.code;
        const name = detailCanvas.dataset.name;
        initDetailPage(code);
        initWatchBtn(code, name);
        updateDetailPrice(code);
    }
});
