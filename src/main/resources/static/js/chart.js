let priceChart = null;
let volumeChart = null;

// 차트 최초 생성
async function initCharts() {
    const data = await fetchChartData();
    const kospiData = await fetchKospiChartData();

    if (!data) return;

    // 종가 시계열 차트
    // priceChart = new Chart(document.getElementById('priceChart'), {
    //     type: 'line',
    //     data: {
    //         labels: data.labels,
    //         datasets: [{
    //             label: '종가',
    //             data: data.closePrices,
    //             borderColor: 'rgb(75, 192, 192)',
    //             tension: 0.1,
    //             fill: false
    //         }]
    //     },
    //     options: {
    //         responsive: true,
    //         plugins: { title: { display: true, text: '일별 종가' } }
    //     }
    // });

    // 거래량 차트
    // volumeChart = new Chart(document.getElementById('volumeChart'), {
    //     //     type: 'bar',
    //     //     data: {
    //     //         labels: data.labels,
    //     //         datasets: [{
    //     //             label: '거래량',
    //     //             data: data.volumes,
    //     //             backgroundColor: 'rgba(153, 102, 255, 0.5)'
    //     //         }]
    //     //     },
    //     //     options: {
    //     //         responsive: true,
    //     //         plugins: { title: { display: true, text: '일별 거래량' } }
    //     //     }
    //     // });


    // 종가 + 거래량 합친 차트
    combinedChart = new Chart(document.getElementById('combinedChart'), {
        data: {
            labels: data.labels,
            datasets: [
                {
                    type: 'line',
                    label: '종가',
                    data: data.closePrices,
                    borderColor: 'rgb(75, 192, 192)',
                    backgroundColor: 'rgba(75, 192, 192, 0.1)',
                    tension: 0.1,
                    yAxisID: 'yPrice',  // 왼쪽 y축
                    fill: true
                },
                {
                    type: 'bar',
                    label: '거래량',
                    data: data.volumes,
                    backgroundColor: 'rgba(153, 102, 255, 0.4)',
                    yAxisID: 'yVolume'  // 오른쪽 y축
                }
            ]
        },
        options: {
            responsive: true,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                title: { display: true, text: '종가 / 거래량' }
            },
            scales: {
                yPrice: {
                    type: 'linear',
                    position: 'left',
                    title: { display: true, text: '종가 (원)' }
                },
                yVolume: {
                    type: 'linear',
                    position: 'right',
                    title: { display: true, text: '거래량' },
                    grid: { drawOnChartArea: false } // 오른쪽 축 격자선 제거
                }
            }
        }
    });

        // 코스피 차트
    kospiChart = new Chart(document.getElementById('kospiChart'), {
        data: {
            labels: kospiData.labels,
            datasets: [
                {
                    type: 'line',
                    label: 'KOSPI',
                    data: kospiData.closePrices,
                    borderColor: 'rgb(255, 159, 64)',
                    backgroundColor: 'rgba(255, 159, 64, 0.1)',
                    tension: 0.1,
                    yAxisID: 'yPrice',
                    fill: true
                },
                {
                    type: 'bar',
                    label: '거래량',
                    data: kospiData.volumes,
                    backgroundColor: 'rgba(255, 99, 132, 0.4)',
                    yAxisID: 'yVolume'
                }
            ]
        },
        options: {
            responsive: true,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                title: { display: true, text: 'KOSPI 지수 / 거래량' }
            },
            scales: {
                yPrice: {
                    type: 'linear',
                    position: 'left',
                    title: { display: true, text: 'KOSPI 지수' }
                },
                yVolume: {
                    type: 'linear',
                    position: 'right',
                    title: { display: true, text: '거래량' },
                    grid: { drawOnChartArea: false }
                }
            }
        }
    });

}

// 종목 현재가 정보 업데이트
async function updateCurrentPrice() {
    const res = await fetch(`/api/stock/${stockCode}`);
    const data = await res.json();

    document.getElementById('currentPrice').textContent = Number(data.currentPrice).toLocaleString() + '원';
    document.getElementById('openPrice').textContent = Number(data.openPrice).toLocaleString() + '원';
    document.getElementById('highPrice').textContent = Number(data.highPrice).toLocaleString() + '원';
    document.getElementById('lowPrice').textContent = Number(data.lowPrice).toLocaleString() + '원';
    document.getElementById('volume').textContent = Number(data.volume).toLocaleString();
}

// 코스피 업데이트 함수
async function updateKospi() {
    const res = await fetch('/api/kospi');
    const data = await res.json();

    document.getElementById('kospiPrice').textContent =
        Number(data.currentPrice).toLocaleString();
    document.getElementById('kospiChange').textContent =
        (data.priceChange > 0 ? '+' : '') + Number(data.priceChange).toLocaleString();
    document.getElementById('kospiRate').textContent =
        (data.changeRate > 0 ? '+' : '') + data.changeRate;
}


// 차트 데이터 fetch
async function fetchChartData() {
    const res = await fetch(`/api/stock/${stockCode}/chart`);
    return await res.json();
}

// 코스피 차트 데이터 fetch
async function fetchKospiChartData() {
    const res = await fetch('/api/kospi/chart');
    return await res.json();
}

// 차트 데이터 업데이트 (차트 재생성 없이 데이터만 교체)
async function updateCharts() {
    const data = await fetchChartData();
    const kospiData = await fetchKospiChartData();

    if (!data || !priceChart || !volumeChart) return;

    // priceChart.data.labels = data.labels;
    // priceChart.data.datasets[0].data = data.closePrices;
    // priceChart.update();
    //
    // volumeChart.data.labels = data.labels;
    // volumeChart.data.datasets[0].data = data.volumes;

    // 종목 + 거래량 차트
    combinedChart.data.labels = data.labels;
    combinedChart.data.datasets[0].data = data.closePrices;
    combinedChart.data.datasets[1].data = data.volumes;

    // 코스피 차트
    if (data && combinedChart) {
        combinedChart.data.labels = data.labels;
        combinedChart.data.datasets[0].data = data.closePrices;
        combinedChart.data.datasets[1].data = data.volumes;
        combinedChart.update();
    }

    volumeChart.update();
}







// 장 운영시간 체크 (평일 09:00 ~ 15:30)
function isMarketOpen() {
    const now = new Date();
    const day = now.getDay(); // 0=일, 6=토
    if (day === 0 || day === 6) return false;

    const hour = now.getHours();
    const minute = now.getMinutes();
    const time = hour * 100 + minute;
    return time >= 900 && time <= 1530;
}

// 폴링 시작
async function startPolling() {
    await initCharts();
    await updateCurrentPrice();

    setInterval(async () => {
        if (isMarketOpen()) {
            console.log('장 운영중 - 데이터 갱신');
            await updateCurrentPrice();
            await updateCharts();
            await updateKospi(); // 코스피
        } else {
            console.log('장 마감 - 갱신 스킵');
        }
    }, 5000); // 5초마다
}

startPolling();