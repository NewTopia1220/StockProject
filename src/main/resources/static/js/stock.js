let mainChart = null;
const DEFAULT_CODE = '005930';
let currentCode = DEFAULT_CODE;
let currentTab = 'daily';
let searchTimer = null;

document.addEventListener('DOMContentLoaded', function() {
    // 섹터 버튼 클릭 이벤트
    const sectorBtns = document.querySelectorAll('.sectorList button');
    sectorBtns.forEach(btn => {
        btn.addEventListener('click', function() {
            sectorBtns.forEach(b => b.classList.remove('active'));
            this.classList.add('active');
        });
    });

    // AI 분석 더미데이터
    updateAIAnalysis({
        confidence: 88,
        noise: 12,
        sentiment: 75,
        status: '안정',
        desc: '현재 시장 트렌드는 매우 안정적이며, AI 신뢰도가 높게 유지되고 있습니다.'
    });

    // 탭 전환
    document.querySelectorAll('.chartTab').forEach(btn => {
        btn.addEventListener('click', async () => {
            document.querySelectorAll('.chartTab').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentTab = btn.dataset.tab;
            await updateMainChart();
        });
    });

    // 검색
    document.getElementById('stockSearch')?.addEventListener('input', async (e) => {
        const keyword = e.target.value.trim();
        const dropdown = document.getElementById('searchDropdown');

        // 타이머로 입력 중 과도한 API 호출 방지 (300ms 디바운싱)
        clearTimeout(searchTimer);

        if (keyword.length < 1) {
            dropdown.style.display = 'none';
            return;
        }

        searchTimer = setTimeout(async () => {
            try {
                const res = await fetch(`/api/stock/search?keyword=${encodeURIComponent(keyword)}`);
                const items = await res.json(); // ← data.output 대신 바로 items로

                if (!items || items.length === 0) {
                    dropdown.style.display = 'none';
                    return;
                }

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

                dropdown.querySelectorAll('.searchItem').forEach(el => {
                    el.addEventListener('click', async () => {
                        currentCode = el.dataset.code;
                        const name = el.dataset.name;
                        document.getElementById('stockSearch').value =
                            name + ' (' + el.dataset.code + ')';
                        dropdown.style.display = 'none';

                        // 종목명을 updateStockInfo에 전달
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

// 외부 클릭시 드롭다운 닫기
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.searchWrapper')) {
            document.getElementById('searchDropdown').style.display = 'none';
        }
    });

// Enter 키 직접 코드 입력도 유지
    document.getElementById('stockSearch')?.addEventListener('keypress', async (e) => {
        if (e.key === 'Enter') {
            const val = e.target.value.trim();
            currentCode = val;
            document.getElementById('searchDropdown').style.display = 'none';
            await updateMainChart();
        }
    });


    // 폴링 시작
    startPolling();
});

// 차트 데이터 fetch
async function fetchMainChartData() {
    // 시간별/분별은 장 중에만 가능
    let tab = currentTab;
    if ((tab === 'time' || tab === 'minute') && !isMarketOpen()) {
        console.log('장 외 시간 - 일별 차트로 대체');
        tab = 'daily';
        // 탭 UI도 일별로 변경
        document.querySelectorAll('.chartTab').forEach(b => {
            b.classList.toggle('active', b.dataset.tab === 'daily');
        });
        currentTab = 'daily';
    }

    const endpoints = {
        daily: `/api/stock/${currentCode}/chart`,
        time: `/api/stock/${currentCode}/time`,
        minute: `/api/stock/${currentCode}/minute`
    };

    try {
        const res = await fetch(endpoints[tab]);
        const data = await res.json();

        // 빈 데이터면 일별로 fallback
        if (!data.labels || data.labels.length === 0) {
            const fallback = await fetch(`/api/stock/${currentCode}/chart`);
            return await fallback.json();
        }
        return data;
    } catch (e) {
        console.log('차트 데이터 오류:', e);
        return { labels: [], closePrices: [], volumes: [] };
    }
}

// 차트 초기화
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
                    text: `${currentCode} - ${currentTab === 'daily' ? '일별' : currentTab === 'time' ? '시간별' : '분별'}`
                }
            },
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

// 차트 업데이트
async function updateMainChart() {
    const data = await fetchMainChartData();
    if (!mainChart) return;

    mainChart.data.labels = data.labels;
    mainChart.data.datasets[0].data = data.closePrices;
    mainChart.options.plugins.title.text =
        `${currentCode} - ${currentTab === 'daily' ? '일별' : currentTab === 'time' ? '시간별' : '분별'}`;
    mainChart.update();
}

// ticker + 환율 업데이트
async function updateTicker() {
    try {
        const [kospi, kosdaq, exchange] = await Promise.all([
            fetch('/api/kospi').then(r => r.json()),
            fetch('/api/kosdaq').then(r => r.json()),
            fetch('/api/exchange').then(r => r.json())
        ]);

        // 코스피
        document.getElementById('kospiPrice').textContent =
            Number(kospi.currentPrice).toLocaleString();
        document.getElementById('kospiRate').textContent =
            (kospi.changeRate > 0 ? '▲ ' : '▼ ') + kospi.changeRate + '%';

        // 코스닥
        document.getElementById('kosdaqPrice').textContent =
            Number(kosdaq.currentPrice).toLocaleString();
        document.getElementById('kosdaqRate').textContent =
            (kosdaq.changeRate > 0 ? '▲ ' : '▼ ') + kosdaq.changeRate + '%';

    } catch (e) {
        console.log('ticker 오류:', e);
    }
}

// 환율 함수
async function updateExchange() {
    try {
        const currency = document.getElementById('currencySelect')?.value || 'USD';
        const exchange = await fetch(`/api/exchange?currency=${currency}`)
            .then(r => r.json());

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

// 통화 변경시 즉시 업데이트
document.getElementById('currencySelect')?.addEventListener('change', () => {
    updateExchange();
});


// 종목 정보 패널 업데이트
async function updateStockInfo(stockName = '') {
    try {
        const res = await fetch(`/api/stock/${currentCode}`);
        const data = await res.json();

        // 종목명 - 검색시 넘어온 이름 우선, 없으면 코드 표시
        document.getElementById('chartStockName').textContent =
            stockName || document.getElementById('chartStockName').textContent || currentCode;
        document.getElementById('chartStockCode').textContent = currentCode;

        const price = Number(data.currentPrice);
        const open  = Number(data.openPrice);
        const high  = Number(data.highPrice);
        const low   = Number(data.lowPrice);
        const vol   = Number(data.volume);

        // 현재가
        document.getElementById('chartCurrentPrice').textContent =
            price.toLocaleString() + ' KRW';

        // 등락 (현재가 - 시가)
        const change = price - open;
        const changeRate = open !== 0 ? ((change / open) * 100).toFixed(2) : '0.00';
        const sign = change >= 0 ? '+' : '';
        const arrow = change >= 0 ? '▲' : '▼';
        const changeEl = document.getElementById('chartPriceChange');
        changeEl.textContent =
            `${sign}${change.toLocaleString()} (${sign}${changeRate}%) ${arrow} 오늘`;
        changeEl.className = change >= 0 ? 'up' : 'down';

        // 시가/고가/저가/거래량
        document.getElementById('chartOpenPrice').textContent =
            open.toLocaleString();
        document.getElementById('chartHighPrice').textContent =
            high.toLocaleString();
        document.getElementById('chartLowPrice').textContent =
            low.toLocaleString();
        document.getElementById('chartVolume').textContent =
            vol.toLocaleString();

    } catch (e) {
        console.log('종목 정보 오류:', e);
    }
}



// AI 분석 업데이트
function updateAIAnalysis(data) {
    document.getElementById('confBar').style.width = data.confidence + '%';
    document.getElementById('confVal').innerText = data.confidence + '%';
    document.getElementById('noiseBar').style.width = data.noise + '%';
    document.getElementById('noiseVal').innerText = data.noise + '%';
    document.getElementById('sentBar').style.width = data.sentiment + '%';
    document.getElementById('aiStatus').innerText = data.status;
    document.getElementById('aiDesc').innerText = data.desc;
}

// 장 운영시간 체크
function isMarketOpen() {
    const now = new Date();
    const day = now.getDay();
    if (day === 0 || day === 6) return false;
    const time = now.getHours() * 100 + now.getMinutes();
    return time >= 900 && time <= 1530;
}

// 등락률 상위 종목 업데이트
async function updateTopStocks() {
    try {
        const res = await fetch('/api/stock/top-fluctuation');
        const stocks = await res.json();

        if (!stocks || stocks.length === 0) return;

        // 티커 업데이트
        const tickerHtml = stocks.map(s => {
            const rate = parseFloat(s.changeRate);
            const cls = rate >= 0 ? 'up' : 'down';
            const arrow = rate >= 0 ? '▲' : '▼';
            return `<div class="t-item">
                <span>${s.stockName}</span>
                <strong class="${cls}">
                    ${Number(s.currentPrice).toLocaleString()} 
                    ${arrow}${Math.abs(rate).toFixed(2)}%
                </strong>
            </div>`;
        }).join('');

        // 티커 두 개 동일하게 (무한 스크롤용)
        document.getElementById('tickerContent1').innerHTML = tickerHtml;
        document.getElementById('tickerContent2').innerHTML = tickerHtml;

        // 종목 리스트 업데이트
        const listHtml = stocks.slice(0, 6).map(s => {
            const rate = parseFloat(s.changeRate);
            const cls = rate >= 0 ? 'up' : 'down';
            const arrow = rate >= 0 ? '▲' : '▼';
            return `<div class="stockItem" onclick="selectStock('${s.stockName}')">
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




// 폴링 시작
async function startPolling() {
    await initMainChart();
    await updateStockInfo();
    await updateTicker();       // 코스피/코스닥
    await updateExchange();     // 환율 (별도)
    await updateTopStocks();    // 등락률 (티커 + 종목 리스트)

    setInterval(async () => {
        await updateTicker(); // 항상 업데이트
        await updateStockInfo();
        await updateTopStocks();
        if (isMarketOpen()) {
            await updateMainChart();
        }
    }, 3000);

    // 환율: 30초마다
    setInterval(async () => {
        await updateExchange();
    }, 30000);
}