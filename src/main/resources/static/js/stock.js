document.addEventListener('DOMContentLoaded', function() {
    /* 1. 차트 초기화 */
    initMainChart();

    /* 2. AI 분석 게이지 애니메이션 (서버 데이터 사용) */
    animateGauges();

    /* 3. 섹터 버튼 필터링 */
    initSectorFilter();
});

function animateGauges() {
    // 서버에서 주입된 데이터
    var data = window.STOCK_DATA || { typeProb: 0, noiseProb: 0, sentimentScore: 50 };

    var confBar  = document.getElementById('confBar');
    var noiseBar = document.getElementById('noiseBar');
    var sentBar  = document.getElementById('sentBar');

    if (confBar)  {
        var confVal = parseFloat(confBar.getAttribute('data-value') || data.typeProb) || 0;
        setTimeout(function() { confBar.style.width = Math.min(confVal, 100) + '%'; }, 100);
    }
    if (noiseBar) {
        var noiseVal = parseFloat(noiseBar.getAttribute('data-value') || data.noiseProb) || 0;
        setTimeout(function() { noiseBar.style.width = Math.min(noiseVal, 100) + '%'; }, 200);
    }
    if (sentBar)  {
        var sentVal = parseFloat(sentBar.getAttribute('data-value') || data.sentimentScore) || 50;
        // 감성바: 0=완전부정, 50=중립, 100=완전긍정
        var sentColor;
        if (sentVal >= 60)      sentColor = 'linear-gradient(90deg, #34d399, #10b981)';
        else if (sentVal <= 40) sentColor = 'linear-gradient(90deg, #f87171, #ef4444)';
        else                    sentColor = 'linear-gradient(90deg, #fbbf24, #f59e0b)';
        sentBar.style.background = sentColor;
        setTimeout(function() { sentBar.style.width = Math.min(sentVal, 100) + '%'; }, 300);
    }
}

function initSectorFilter() {
    var btns  = document.querySelectorAll('#sectorBtns button');
    var cards = document.querySelectorAll('#stockCardGridReal .stockCard');

    // 초기: 전체 표시
    cards.forEach(function(card) { card.style.display = 'flex'; });

    btns.forEach(function(btn) {
        btn.addEventListener('click', function() {
            btns.forEach(function(b) { b.classList.remove('active'); });
            this.classList.add('active');

            var sector = this.getAttribute('data-sector');
            cards.forEach(function(card) {
                if (sector === 'all' || card.getAttribute('data-sector') === sector) {
                    card.style.display = 'flex';
                } else {
                    card.style.display = 'none';
                }
            });
        });
    });
}

function initMainChart() {
    var canvas = document.getElementById('mainChart');
    if (!canvas) return;
    var ctx = canvas.getContext('2d');
    var primaryNavy = '#0E0F37';

    var gradient = ctx.createLinearGradient(0, 0, 0, 400);
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
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: 'rgba(14, 15, 55, 0.9)',
                    padding: 12,
                    titleFont: { size: 14, weight: 'bold' },
                    bodyFont: { size: 13 },
                    displayColors: false,
                    callbacks: {
                        label: function(context) {
                            var label = ' 지수: ' + context.parsed.y.toLocaleString();
                            if (context.dataIndex > 0) {
                                var prev = context.dataset.data[context.dataIndex - 1];
                                var diff = context.parsed.y - prev;
                                var percent = ((diff / prev) * 100).toFixed(2);
                                var sign = diff >= 0 ? '▲' : '▼';
                                label += ' (' + sign + Math.abs(percent) + '%)';
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
                    ticks: { callback: function(v) { return v.toLocaleString(); } }
                }
            }
        }
    });
}
