document.addEventListener('DOMContentLoaded', function() {
    /* 1. 차트 초기화 */
    initMainChart();

    /* 2. AI 분석 데이터 업데이트 (실시간 연동용) */
    const mockData = {
        confidence: 88,
        noise: 12,
        sentiment: 75,
        status: '안정',
        desc: '현재 시장 트렌드는 매우 안정적이며, AI 신뢰도가 높게 유지되고 있습니다.'
    };
    updateAIAnalysis(mockData);

    /* 3. 섹터 버튼 클릭 이벤트 */
    const sectorBtns = document.querySelectorAll('.sectorList button');
    sectorBtns.forEach(btn => {
        btn.addEventListener('click', function() {
            sectorBtns.forEach(b => b.classList.remove('active'));
            this.classList.add('active');
            const sector = this.dataset.sector;
            console.log("선택된 섹터: " + sector);
            // 여기서 해당 섹터의 주식 리스트를 fetch로 불러와 갱신할 수 있습니다.
        });
    });
});

function initMainChart() {
    const ctx = document.getElementById('mainChart').getContext('2d');
    const primaryNavy = '#0E0F37';

    const gradient = ctx.createLinearGradient(0, 0, 0, 400);
    gradient.addColorStop(0, 'rgba(14, 15, 55, 0.1)');
    gradient.addColorStop(1, 'rgba(14, 15, 55, 0)');

    new Chart(ctx, {
        type: 'line',
        data: {
            labels: ['09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00'],
            datasets: [{
                data: [2610, 2618, 2612, 2625, 2615, 2628, 2618.40],
                borderColor: primaryNavy,
                backgroundColor: gradient,
                fill: true,
                borderWidth: 3,
                tension: 0.4,
                pointRadius: 0,
                pointHoverRadius: 5
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false, // 커서를 선 근처에만 가져가도 툴팁이 뜸
            },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: 'rgba(14, 15, 55, 0.9)', // Stoxle 네이비 색상
                    padding: 12,
                    titleFont: { size: 14, weight: 'bold' },
                    bodyFont: { size: 13 },
                    displayColors: false, // 데이터 포인트 색상 박스 숨김
                    callbacks: {
                        // 툴팁에 표시될 라벨을 커스텀
                        label: function(context) {
                            let label = ` 지수: ${context.parsed.y.toLocaleString()}`;

                            // 이전 데이터와 비교해서 상승/하락 계산
                            if (context.dataIndex > 0) {
                                let prev = context.dataset.data[context.dataIndex - 1];
                                let diff = context.parsed.y - prev;
                                let percent = ((diff / prev) * 100).toFixed(2);
                                let sign = diff >= 0 ? '▲' : '▼';
                                label += ` (${sign}${Math.abs(percent)}%)`;
                            }
                            return label;
                        }
                    }
                }
            },
            scales: {
                x: { grid: { display: false } },
                y: {
                    grid: { color: '#f3f4f6' },
                    ticks: { callback: v => v.toLocaleString() }
                }
            }
        }
    });
}

function updateAIAnalysis(data) {
    // 게이지 바 업데이트
    document.getElementById('confBar').style.width = data.confidence + '%';
    document.getElementById('confVal').innerText = data.confidence + '%';

    document.getElementById('noiseBar').style.width = data.noise + '%';
    document.getElementById('noiseVal').innerText = data.noise + '%';

    document.getElementById('sentBar').style.width = data.sentiment + '%';

    // 상태 및 설명 업데이트
    document.getElementById('aiStatus').innerText = data.status;
    document.getElementById('aiDesc').innerText = data.desc;
}