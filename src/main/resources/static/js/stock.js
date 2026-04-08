
// 지수/종목 폴링 : 5초
// 등락률 순위 : 30초
// 차트 업데이트 : 10초(장중만)
// 환율  : 60초
// 시간별/분별 장외 : 자동 일별 전환 + 안내



// ════════════════════════════════════════════════════════
//  전역 상태 변수
// ════════════════════════════════════════════════════════

/** Chart.js 인스턴스 (단일 차트 재사용) */
let mainChart = null;

/** 현재 표시 중인 종목코드 (기본: 삼성전자) */
let currentCode = '005930';

/** 현재 차트 탭 (daily | time | minute) */
let currentTab = 'daily';

/** 검색 디바운싱용 타이머 ID */
let searchTimer = null;

// ════════════════════════════════════════════════════════
//  초기화 (DOM 로드 완료 후 실행)
// ════════════════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', function () {

    animateGauges();

    // 차트 탭 전환 이벤트
    document.querySelectorAll('.chartTab').forEach(btn => {
        btn.addEventListener('click', async () => {
            document.querySelectorAll('.chartTab').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentTab = btn.dataset.tab;

            // 장 외 시간에 시간별/분별 클릭 시 안내
            if ((currentTab === 'time' || currentTab === 'minute') && !isMarketOpen()) {
                alert('시간별/분별 차트는 장 운영시간(09:00~15:30)에만 제공됩니다.');
                currentTab = 'daily';
                document.querySelectorAll('.chartTab').forEach(b => {
                    b.classList.toggle('active', b.dataset.tab === 'daily');
                });
            }

            await updateMainChart();
        });

        startPolling();
    });

    // 종목 검색 입력 이벤트 (300ms 디바운싱)
    document.getElementById('stockSearch')?.addEventListener('input', async (e) => {
        const keyword = e.target.value.trim();
        const dropdown = document.getElementById('searchDropdown');
        clearTimeout(searchTimer);

        if (keyword.length < 1) {
            dropdown.style.display = 'none';
            return;
        }

        searchTimer = setTimeout(async () => {
            try {
                const res = await fetch(`/api/stock/search?keyword=${encodeURIComponent(keyword)}`);
                const items = await res.json();

                if (!items || items.length === 0) {
                    dropdown.style.display = 'none';
                    return;
                }

                // 검색 결과 드롭다운 렌더링
                dropdown.innerHTML = items.slice(0, 8).map(item => `
                    <div class="searchItem"
                         data-code="${item.code}"
                         data-name="${item.name}">
                        <div>
                            <span class="item-name">${item.name}</span>
                            <span class="item-market">${item.market || ''}</span>
                        </div>
                        <span class="item-code">${item.code}</span>
                    </div>
                `).join('');

                // 드롭다운 항목 클릭: 종목 변경 + 차트/정보 업데이트
                dropdown.querySelectorAll('.searchItem').forEach(el => {
                    el.addEventListener('click', async () => {
                        currentCode = el.dataset.code;
                        const name = el.dataset.name;
                        document.getElementById('stockSearch').value = `${name} (${el.dataset.code})`;
                        dropdown.style.display = 'none';
                        await updateStockInfo(name);
                        await updateMainChart();
                    });
                });

                dropdown.style.display = 'block';
            } catch (e) {
                console.log('검색 오류:', e);
            }
        }, 300);
    });

    // 검색창 외부 클릭 시 드롭다운 닫기
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.searchWrapper')) {
            document.getElementById('searchDropdown').style.display = 'none';
        }
    });

    // Enter 키로 종목코드 직접 입력
    document.getElementById('stockSearch')?.addEventListener('keypress', async (e) => {
        if (e.key === 'Enter') {
            currentCode = e.target.value.trim();
            document.getElementById('searchDropdown').style.display = 'none';
            await updateMainChart();
        }
    });

    // 통화 변경 시 환율 즉시 업데이트
    document.getElementById('currencySelect')?.addEventListener('change', () => {
        updateExchange();
    });

    // 폴링 시작
    startPolling();
});

// ════════════════════════════════════════════════════════
//  차트 관련
// ════════════════════════════════════════════════════════

/**
 * 현재 탭/종목에 맞는 차트 데이터 fetch
 * - 장 외 시간에 시간별/분별 선택 시 일별로 자동 전환
 */
async function fetchMainChartData() {
    let tab = currentTab;

    // 장 외 시간이면 시간별/분별 → 일별로 자동 전환
    if ((tab === 'time' || tab === 'minute') && !isMarketOpen()) {
        console.log('장 외 시간 - 일별 차트로 대체');
        tab = 'daily';
        currentTab = 'daily';
        document.querySelectorAll('.chartTab').forEach(b => {
            b.classList.toggle('active', b.dataset.tab === 'daily');
        });
    }

    const endpoints = {
        daily:  `/api/stock/${currentCode}/chart`,
        time:   `/api/stock/${currentCode}/time`,
        minute: `/api/stock/${currentCode}/minute`
    };

    try {
        const res = await fetch(endpoints[tab]);
        const data = await res.json();

        // 데이터 비어있으면 일별로 fallback
        if (!data.labels || data.labels.length === 0) {
            console.log('데이터 없음 - 일별로 fallback');
            // 탭도 일별로 복귀
            currentTab = 'daily';
            document.querySelectorAll('.chartTab').forEach(b => {
                b.classList.toggle('active', b.dataset.tab === 'daily');
            });
            const fallback = await fetch(`/api/stock/${currentCode}/chart`);
            return await fallback.json();
        }
        return data;
    } catch (e) {
        console.log('차트 데이터 오류:', e);
        return { labels: [], closePrices: [], volumes: [] };
    }
}

/**
 * 차트 초기화 (첫 로드 또는 종목 변경 시)
 * - 기존 Chart 인스턴스가 있으면 destroy 후 재생성
 */
async function initMainChart() {
    const data = await fetchMainChartData();

    if (mainChart) {
        mainChart.destroy();
    }

    mainChart = new Chart(document.getElementById('mainChart'), {
        type: 'line',
        data: {
            labels: data.labels,
            datasets: [{
                label: currentTab === 'daily' ? '종가' : '가격',
                data: data.closePrices,
                borderColor: '#0E0F37',
                backgroundColor: 'rgba(14, 15, 55, 0.1)',
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
            plugins: {
                legend: { display: false },
                title: {
                    display: true,
                    text: getChartTitle()
                }
            },
            scales: {
                x: { grid: { display: false } },
                y: {
                    grid: { color: '#f3f4f6' },
                    ticks: { callback: v => Number(v).toLocaleString() }


/* ── 게이지 애니메이션 ── */
function animateGauges() {
    var d = window.STOCK_DATA || { typeProb: 0, noiseProb: 0, sentimentScore: 50 };

    var confBar  = document.getElementById('confBar');
    var noiseBar = document.getElementById('noiseBar');
    var sentBar  = document.getElementById('sentBar');

    if (confBar) {
        var v = parseFloat(confBar.getAttribute('data-value') || d.typeProb) || 0;
        setTimeout(function () { confBar.style.width = Math.min(v, 100) + '%'; }, 100);
    }
    if (noiseBar) {
        var v2 = parseFloat(noiseBar.getAttribute('data-value') || d.noiseProb) || 0;
        setTimeout(function () { noiseBar.style.width = Math.min(v2, 100) + '%'; }, 200);
    }
    if (sentBar) {
        var sv = parseFloat(sentBar.getAttribute('data-value') || d.sentimentScore) || 50;
        var color;
        if (sv >= 60)      color = 'linear-gradient(90deg,#34d399,#10b981)';
        else if (sv <= 40) color = 'linear-gradient(90deg,#f87171,#ef4444)';
        else               color = 'linear-gradient(90deg,#fbbf24,#f59e0b)';
        sentBar.style.background = color;
        setTimeout(function () { sentBar.style.width = Math.min(sv, 100) + '%'; }, 300);
    }
}


/**
 * 차트 데이터 업데이트 (폴링 or 탭/종목 변경 시)
 */
async function updateMainChart() {
    const data = await fetchMainChartData();
    if (!mainChart) return;

    mainChart.data.labels = data.labels;
    mainChart.data.datasets[0].data = data.closePrices;
    mainChart.options.plugins.title.text = getChartTitle();
    mainChart.update();
}

/**
 * 현재 종목/탭에 맞는 차트 제목 반환
 */
function getChartTitle() {
    const tabLabel = currentTab === 'daily' ? '일별' : currentTab === 'time' ? '시간별' : '분별';
    return `${currentCode} - ${tabLabel}`;
}

// ════════════════════════════════════════════════════════
//  종목 정보 패널
// ════════════════════════════════════════════════════════

/**
 * 차트 상단 종목 정보 패널 업데이트
 * - 현재가, 시가, 고가, 저가, 거래량, 등락 표시
 *
 * @param stockName 종목명 (검색 시 넘어온 값, 없으면 기존 값 유지)
 */
async function updateStockInfo(stockName = '') {
    try {
        const res = await fetch(`/api/stock/${currentCode}`);
        const data = await res.json();

        // 종목명: 검색 시 전달된 이름 우선, 없으면 기존 표시 유지
        document.getElementById('chartStockName').textContent =
            stockName || document.getElementById('chartStockName').textContent || currentCode;
        document.getElementById('chartStockCode').textContent = currentCode;

        const price = Number(data.currentPrice);
        const open  = Number(data.openPrice);
        const high  = Number(data.highPrice);
        const low   = Number(data.lowPrice);
        const vol   = Number(data.volume);

        document.getElementById('chartCurrentPrice').textContent = `${price.toLocaleString()} KRW`;

        // 등락 = 현재가 - 시가 기준
        const change = price - open;
        const changeRate = open !== 0 ? ((change / open) * 100).toFixed(2) : '0.00';
        const sign = change >= 0 ? '+' : '';
        const arrow = change >= 0 ? '▲' : '▼';
        const changeEl = document.getElementById('chartPriceChange');
        changeEl.textContent = `${sign}${change.toLocaleString()} (${sign}${changeRate}%) ${arrow} 오늘`;
        changeEl.className = change >= 0 ? 'up' : 'down';

        document.getElementById('chartOpenPrice').textContent = open.toLocaleString();
        document.getElementById('chartHighPrice').textContent = high.toLocaleString();
        document.getElementById('chartLowPrice').textContent = low.toLocaleString();
        document.getElementById('chartVolume').textContent = vol.toLocaleString();

    } catch (e) {
        console.log('종목 정보 오류:', e);
    }
}

// ════════════════════════════════════════════════════════
//  지수 / 환율 업데이트
// ════════════════════════════════════════════════════════

/**
 * 코스피/코스닥 지수 업데이트
 */
async function updateTicker() {
    try {
        const [kospi, kosdaq] = await Promise.all([
            fetch('/api/kospi').then(r => r.json()),
            fetch('/api/kosdaq').then(r => r.json())
        ]);

        document.getElementById('kospiPrice').textContent = Number(kospi.currentPrice).toLocaleString();
        document.getElementById('kospiRate').textContent =
            (kospi.changeRate > 0 ? '▲ ' : '▼ ') + kospi.changeRate + '%';

        document.getElementById('kosdaqPrice').textContent = Number(kosdaq.currentPrice).toLocaleString();
        document.getElementById('kosdaqRate').textContent =
            (kosdaq.changeRate > 0 ? '▲ ' : '▼ ') + kosdaq.changeRate + '%';
    } catch (e) {
        console.log('ticker 오류:', e);
    }
}

/**
 * 환율 업데이트
 * - 선택된 통화(currencySelect)로 조회
 * - 등락에 따라 up/down 클래스 적용
 */
async function updateExchange() {
    try {
        const currency = document.getElementById('currencySelect')?.value || 'USD';
        const exchange = await fetch(`/api/exchange?currency=${currency}`).then(r => r.json());

        document.getElementById('exchangePrice').textContent =
            Number(exchange.currentPrice).toLocaleString() + '원';

        const change = parseFloat(exchange.priceChange);
        const rateEl = document.getElementById('exchangeRate');
        if (change > 0) {
            rateEl.textContent = '▲ ' + exchange.changeRate + '%';
            rateEl.className = 'up';
        } else if (change < 0) {
            rateEl.textContent = '▼ ' + Math.abs(exchange.changeRate) + '%';
            rateEl.className = 'down';
        } else {
            rateEl.textContent = '0.00%';
            rateEl.className = '';
        }
    } catch (e) {
        console.log('환율 오류:', e);
    }
}

// ════════════════════════════════════════════════════════
//  등락률 상위 종목 (티커 + 종목 리스트)
// ════════════════════════════════════════════════════════

/**
 * 등락률 상위 종목 조회 후 티커와 종목 리스트 업데이트
 * - 티커: 무한 스크롤을 위해 동일 내용 2개 렌더링
 * - 종목 리스트: 상위 6개 표시
 */
async function updateTopStocks() {
    try {
        const res = await fetch('/api/stock/top-fluctuation');
        const stocks = await res.json();
        if (!stocks || stocks.length === 0) return;

        // 티커 아이템 HTML 생성
        const tickerHtml = stocks.map(s => {
            const rate = parseFloat(s.changeRate);
            const cls = rate >= 0 ? 'up' : 'down';
            const arrow = rate >= 0 ? '▲' : '▼';
            return `<div class="t-item">
                <span>${s.stockName}</span>
                <strong class="${cls}">
                    ${Number(s.currentPrice).toLocaleString()} ${arrow}${Math.abs(rate).toFixed(2)}%
                </strong>
            </div>`;
        }).join('');

        // 무한 스크롤용으로 동일 내용 2개 렌더링
        document.getElementById('tickerContent1').innerHTML = tickerHtml;
        document.getElementById('tickerContent2').innerHTML = tickerHtml;

        // 종목 리스트 (상위 6개)
        const listHtml = stocks.slice(0, 6).map(s => {
            const rate = parseFloat(s.changeRate);
            const cls = rate >= 0 ? 'up' : 'down';
            const arrow = rate >= 0 ? '▲' : '▼';
            return `<div class="stockItem">
                <div class="name">
                    <h3>${s.stockName}</h3>
                </div>
                <div class="priceInfo">
                    <strong>${Number(s.currentPrice).toLocaleString()}원</strong>
                    <span class="${cls}">${arrow} ${Math.abs(rate).toFixed(2)}%</span>
                </div>
            </div>`;
        }).join('');

        document.getElementById('stockListContainer').innerHTML = listHtml;
    } catch (e) {
        console.log('등락률 순위 오류:', e);
    }
}

// ════════════════════════════════════════════════════════
//  AI 분석 (추후 ML 모델 연동 예정)
// ════════════════════════════════════════════════════════

/**
 * AI 분석 게이지 바 업데이트
 * @param data { confidence, noise, sentiment, status, desc }
 */
function updateAIAnalysis(data) {
    document.getElementById('confBar').style.width = data.confidence + '%';
    document.getElementById('confVal').innerText = data.confidence + '%';
    document.getElementById('noiseBar').style.width = data.noise + '%';
    document.getElementById('noiseVal').innerText = data.noise + '%';
    document.getElementById('sentBar').style.width = data.sentiment + '%';
    document.getElementById('aiStatus').innerText = data.status;
    document.getElementById('aiDesc').innerText = data.desc;
}

// ════════════════════════════════════════════════════════
//  유틸리티
// ════════════════════════════════════════════════════════

/**
 * 현재 장 운영 시간 여부 확인
 * - 평일(월~금) 09:00 ~ 15:30 만 true
 */
function isMarketOpen() {
    const now = new Date();
    const day = now.getDay(); // 0=일, 6=토
    if (day === 0 || day === 6) return false;
    const time = now.getHours() * 100 + now.getMinutes();
    return time >= 900 && time <= 1530;
}

// ════════════════════════════════════════════════════════
//  폴링 (주기적 데이터 갱신)
// ════════════════════════════════════════════════════════

/**
 * 폴링 시작
 * - 초기 로드: 차트, 종목정보, 지수, 환율, 등락률 순위 한 번 실행
 * - 지수/종목정보/등락률: 3초마다 갱신
 * - 환율: 30초마다 갱신 (자주 변하지 않으므로 별도 인터벌)
 * - 차트: 장 중에만 갱신
 */
async function startPolling() {
    // 초기 로드
    await initMainChart();
    await updateStockInfo();
    await updateTicker();
    await updateExchange();
    await updateTopStocks();

    // 지수/종목정보: 5초마다 (3초는 API 부하 큼)
    setInterval(async () => {
        await updateTicker();
        await updateStockInfo();
    }, 5000);

    // 등락률 순위: 30초마다 (자주 안 바뀜)
    setInterval(async () => {
        await updateTopStocks();
    }, 30000);

    // 차트: 장 중에만 10초마다
    setInterval(async () => {
        if (isMarketOpen()) {
            await updateMainChart();
        }
    }, 10000);

    // 환율: 60초마다
    setInterval(async () => {
        await updateExchange();
    }, 60000);
}