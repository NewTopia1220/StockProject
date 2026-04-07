document.addEventListener('DOMContentLoaded', function () {
    initMainChart();
    animateGauges();
    initDarkMode();
});

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

/* ── 다크모드 ── */
function initDarkMode() {
    var btn  = document.getElementById('darkModeBtn');
    var body = document.body;

    // 저장된 설정 복원
    if (localStorage.getItem('darkMode') === 'on') {
        body.classList.add('dark');
        btn.textContent = '☀️';
    }

    btn.addEventListener('click', function () {
        body.classList.toggle('dark');
        var isDark = body.classList.contains('dark');
        btn.textContent = isDark ? '☀️' : '🌙';
        localStorage.setItem('darkMode', isDark ? 'on' : 'off');
    });
}

/* ── 차트 ── */
function initMainChart() {
    var canvas = document.getElementById('mainChart');
    if (!canvas) return;
    var ctx = canvas.getContext('2d');
    var isDark = document.body.classList.contains('dark');
    var lineColor = isDark ? '#6366f1' : '#0E0F37';

    var gradient = ctx.createLinearGradient(0, 0, 0, 350);
    gradient.addColorStop(0, isDark ? 'rgba(99,102,241,0.2)' : 'rgba(14,15,55,0.1)');
    gradient.addColorStop(1, 'rgba(0,0,0,0)');

    new Chart(ctx, {
        type: 'line',
        data: {
            labels: ['09:00','10:00','11:00','12:00','13:00','14:00','15:00'],
            datasets: [{
                data: [2610,2618,2612,2625,2615,2628,2618.40],
                borderColor: lineColor,
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
                    backgroundColor: isDark ? 'rgba(99,102,241,0.9)' : 'rgba(14,15,55,0.9)',
                    padding: 12,
                    titleFont: { size: 13, weight: 'bold' },
                    bodyFont: { size: 13 },
                    displayColors: false,
                    callbacks: {
                        label: function (ctx) {
                            var label = ' 지수: ' + ctx.parsed.y.toLocaleString();
                            if (ctx.dataIndex > 0) {
                                var prev = ctx.dataset.data[ctx.dataIndex - 1];
                                var diff = ctx.parsed.y - prev;
                                var pct  = ((diff / prev) * 100).toFixed(2);
                                label += ' (' + (diff >= 0 ? '▲' : '▼') + Math.abs(pct) + '%)';
                            }
                            return label;
                        }
                    }
                }
            },
            scales: {
                x: { grid: { display: false }, ticks: { color: isDark ? '#94a3b8' : '#6b7280' } },
                y: {
                    grid: { color: isDark ? '#2d3142' : '#f3f4f6' },
                    ticks: { callback: function (v) { return v.toLocaleString(); }, color: isDark ? '#94a3b8' : '#6b7280' }
                }
            }
        }
    });
}
