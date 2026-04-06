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
                const data = await res.json();

                // 응답 확인 후 드롭다운 표시
                const items = data.output || [];
                if (items.length === 0) {
                    dropdown.style.display = 'none';
                    return;
                }

                dropdown.innerHTML = items.slice(0, 8).map(item => `
                <div class="searchItem"
                     data-code="${item.code}"
                     style="padding: 10px 14px; cursor: pointer; border-bottom: 1px solid #f0f0f0;
                            display: flex; justify-content: space-between; align-items: center;">
                    <div>
                        <span style="font-weight: bold;">${item.name}</span>
                        <span style="font-size: 11px; color: #aaa; margin-left: 6px;">${item.market}</span>
                    </div>
                    <span style="color: #888; font-size: 13px;">${item.code}</span>
                </div>
            `).join('');

                // 드롭다운 항목 클릭
                dropdown.querySelectorAll('.searchItem').forEach(el => {
                    el.addEventListener('mouseenter', () => el.style.background = '#f5f5f5');
                    el.addEventListener('mouseleave', () => el.style.background = 'white');
                    el.addEventListener('click', () => {
                        currentCode = el.dataset.code;
                        document.getElementById('stockSearch').value = el.querySelector('span').textContent;
                        dropdown.style.display = 'none';
                        updateMainChart();
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
    const endpoints = {
        daily: `/api/stock/${currentCode}/chart`,
        time: `/api/stock/${currentCode}/time`,
        minute: `/api/stock/${currentCode}/minute`
    };
    const res = await fetch(endpoints[currentTab]);
    return await res.json();
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
            exchange.currentPrice + '원';
        document.getElementById('exchangeRate').textContent =
            exchange.changeRate + '%';
    } catch (e) {
        console.log('환율 오류:', e);
    }
}

// 통화 변경시 즉시 업데이트
document.getElementById('currencySelect')?.addEventListener('change', () => {
    updateExchange();
});


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

// 폴링 시작
async function startPolling() {
    await initMainChart();
    await updateTicker();       // 코스피/코스닥
    await updateExchange();     // 환율 (별도)

    setInterval(async () => {
        await updateTicker(); // 항상 업데이트
        if (isMarketOpen()) {
            await updateMainChart();
        }
    }, 5000);

    // 환율: 1분마다
    setInterval(async () => {
        await updateExchange();
    }, 60000);
}