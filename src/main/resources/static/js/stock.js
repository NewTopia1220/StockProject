// Highcharts 전역 로컬 시간 설정
if (typeof Highcharts !== 'undefined') {
    Highcharts.setOptions({ time: { useUTC: false } });
}

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
 * 차트 초기화 (첫 로드 또는 종목 변경 시) - Highcharts Stock
 */
async function initMainChart() {
    const data = await fetchMainChartData();
    if (mainChart) { mainChart.destroy(); mainChart = null; }

    const labels = data.labels || [];
    const hasOhlc = data.openPrices && data.openPrices.length > 0 && data.openPrices[0] !== '0';
    const ohlc = [], vol = [];

    for (let i = 0; i < labels.length; i++) {
        const ts = stockDateToTs(labels[i]);
        if (hasOhlc) {
            ohlc.push([ts, +data.openPrices[i], +data.highPrices[i], +data.lowPrices[i], +data.closePrices[i]]);
        } else {
            ohlc.push([ts, +(data.closePrices || [])[i] || 0]);
        }
        vol.push([ts, +(data.volumes || [])[i] || 0]);
    }

    const isIntraday = (currentTab === 'time' || currentTab === 'minute');
    const timeFmt    = isIntraday ? '%H:%M' : '%Y-%m-%d';

    // 시가 plotLine 값
    const openPriceVal = hasOhlc && ohlc.length > 0
        ? (isIntraday ? ohlc[0][1] : ohlc[ohlc.length - 1][1])
        : null;

    // 분별 라인 방향 색상
    const minuteLineColor = (() => {
        if (currentTab !== 'minute' || ohlc.length < 2) return '#0E0F37';
        const first = hasOhlc ? ohlc[0][4] : ohlc[0][1];
        const last  = hasOhlc ? ohlc[ohlc.length - 1][4] : ohlc[ohlc.length - 1][1];
        return last >= first ? '#ef4444' : '#3b82f6';
    })();

    try {
    mainChart = Highcharts.stockChart('mainChart', {
        time: {
            useUTC: false
        },
        chart: {
            backgroundColor: '#fff',
            style: { fontFamily: 'inherit' },
            animation: false,
            height: 340
        },
        credits: { enabled: false },
        rangeSelector: isIntraday ? { enabled: false } : {
            selected: 1,
            inputEnabled: false,
            buttons: [
                { type: 'month', count: 1, text: '1개월' },
                { type: 'month', count: 3, text: '3개월' },
                { type: 'all',              text: '전체'   }
            ],
            buttonTheme: {
                fill: '#f9fafb', stroke: '#e5e7eb', r: 6,
                style: { color: '#374151', fontWeight: '600', fontSize: '11px' },
                states: { select: { fill: '#0E0F37', style: { color: '#fff' } } }
            }
        },
        navigator: { enabled: !isIntraday },
        scrollbar: { enabled: false },
        title: { text: '' },
        tooltip: {
            split: false, shared: true,
            formatter: function () {
                const pts = this.points || [];
                let s = `<b>${Highcharts.dateFormat(timeFmt, this.x)}</b><br/>`;
                pts.forEach(p => {
                    if (p.series.type === 'candlestick') {
                        s += `시가 ${p.point.open?.toLocaleString()} · 고가 ${p.point.high?.toLocaleString()} · 저가 ${p.point.low?.toLocaleString()} · 종가 <b>${p.point.close?.toLocaleString()}</b>원<br/>`;
                    } else if (p.series.type === 'column') {
                        s += `거래량 ${p.y?.toLocaleString()}<br/>`;
                    } else {
                        s += `${p.y?.toLocaleString()}원<br/>`;
                    }
                });
                return s;
            }
        },
        xAxis: (() => {
            const base = { type: 'datetime', lineColor: '#e5e7eb', tickColor: '#e5e7eb' };
            if (currentTab === 'daily') {
                return { ...base, ordinal: true,
                    dateTimeLabelFormats: { day: '%m/%d', week: '%m/%d', month: '%y/%m' } };
            }
            if (currentTab === 'time') {
                const _n = new Date();
                const _at9    = Date.UTC(_n.getFullYear(), _n.getMonth(), _n.getDate(),  9,  0) - 9 * 3600000;
                const _at1530 = Date.UTC(_n.getFullYear(), _n.getMonth(), _n.getDate(), 15, 30) - 9 * 3600000;
                const _nowTs  = Date.UTC(_n.getFullYear(), _n.getMonth(), _n.getDate(), _n.getHours(), _n.getMinutes()) - 9 * 3600000;
                const _xMax   = _nowTs < _at1530 ? _nowTs : _at1530;
                return { ...base, ordinal: false,
                    tickInterval: 3600000,
                    min: _at9,
                    max: _xMax,
                    dateTimeLabelFormats: { millisecond: '%H:%M', second: '%H:%M', minute: '%H:%M', hour: '%H:%M' } };
            }
            return { ...base, ordinal: false,
                dateTimeLabelFormats: { millisecond: '%H:%M', second: '%H:%M', minute: '%H:%M', hour: '%H:%M' } };
        })(),
        yAxis: [{
            labels: {
                align: 'left',
                style: { color: '#374151', fontSize: '11px' },
                formatter: function() { return this.value.toLocaleString(); }
            },
            height: '72%',
            gridLineColor: '#f3f4f6',
            resize: { enabled: true },
            plotLines: []
        }, {
            labels: { align: 'left', style: { color: '#9ca3af', fontSize: '11px' } },
            top: '72%', height: '28%', offset: 0,
            gridLineColor: '#f9fafb'
        }],
        series: [
            hasOhlc ? {
                type: 'candlestick', name: currentCode,
                data: ohlc,
                color: '#3b82f6', upColor: '#ef4444',
                lineColor: '#3b82f6', upLineColor: '#ef4444',
                lineWidth: currentTab === 'minute' ? 2 : 1,
                pointWidth: currentTab === 'time' ? 8 : currentTab === 'minute' ? 4 : undefined,
                dataGrouping: { enabled: false }
            } : {
                type: 'line', name: currentCode,
                data: ohlc, color: minuteLineColor,
                lineWidth: currentTab === 'minute' ? 3 : 2,
                marker: { enabled: false },
                dataGrouping: { enabled: false }
            },
            {
                type: 'column', name: '거래량',
                data: vol, yAxis: 1,
                color: '#e5e7eb',
                dataGrouping: { enabled: false }
            }
        ]
    });
    } catch (err) {
        console.error('Highcharts 차트 생성 실패:', err);
    }
}

function stockDateToTs(s) {
    if (!s) return 0;

    // 1. 시간 형식 (예: "14:10") 처리
    if (s.includes(':')) {
        const [h, m] = s.split(':').map(Number);
        const now = new Date();
        // 현재 날짜의 '로컬' 시/분으로 설정 (Date.UTC 사용 금지)
        return new Date(now.getFullYear(), now.getMonth(), now.getDate(), h, m, 0, 0).getTime();
    }

    // 2. 일별 형식 (예: "20260409") 처리
    if (s.length === 8) {
        const y = +s.slice(0, 4);
        const m = +s.slice(4, 6) - 1; // 월은 0부터 시작
        const d = +s.slice(6, 8);
        // 해당 날짜의 00시 00분 '로컬' 타임스탬프 생성
        return new Date(y, m, d, 0, 0, 0, 0).getTime();
    }

    return 0;
}

/* ── 게이지 애니메이션 ── */
function animateGauges() {
    var d = window.STOCK_DATA || {typeProb: 0, noiseProb: 0, sentimentScore: 50};

    var confBar = document.getElementById('confBar');
    var noiseBar = document.getElementById('noiseBar');
    var sentBar = document.getElementById('sentBar');

    if (confBar) {
        var v = parseFloat(confBar.getAttribute('data-value') || d.typeProb) || 0;
        setTimeout(function () {
            confBar.style.width = Math.min(v, 100) + '%';
        }, 100);
    }
    if (noiseBar) {
        var v2 = parseFloat(noiseBar.getAttribute('data-value') || d.noiseProb) || 0;
        setTimeout(function () {
            noiseBar.style.width = Math.min(v2, 100) + '%';
        }, 200);
    }
    if (sentBar) {
        var sv = parseFloat(sentBar.getAttribute('data-value') || d.sentimentScore) || 50;
        var color;
        if (sv >= 60) color = 'linear-gradient(90deg,#34d399,#10b981)';
        else if (sv <= 40) color = 'linear-gradient(90deg,#f87171,#ef4444)';
        else color = 'linear-gradient(90deg,#fbbf24,#f59e0b)';
        sentBar.style.background = color;
        setTimeout(function () {
            sentBar.style.width = Math.min(sv, 100) + '%';
        }, 300);
    }
}



/**
 * 차트 데이터 업데이트 - 재초기화 방식 (Highcharts)
 */
async function updateMainChart() {
    await initMainChart();
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

        // 등락 = API priceChange / changeRate 기준 (전일 대비)
        const changeRate = parseFloat(data.changeRate) || 0;
        let   change     = parseFloat(data.priceChange);
        if (!change || isNaN(change)) {
            change = (price > 0 && changeRate !== 0)
                ? Math.round(price * (changeRate / 100) / (1 + changeRate / 100))
                : 0;
        }
        const dir        = changeRate !== 0 ? changeRate : change;
        const sign       = dir >= 0 ? '+' : '';
        const arrow      = dir >= 0 ? '▲' : '▼';
        const changeEl   = document.getElementById('chartPriceChange');
        changeEl.textContent = `${sign}${change.toLocaleString()} (${sign}${changeRate.toFixed(2)}%) ${arrow} 전일대비`;
        changeEl.className   = dir >= 0 ? 'up' : 'down';

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
        const getAllStocks = await res.json();
        const stocks = getAllStocks.slice(0, 15);
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