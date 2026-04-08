
/**
 * 저축 목표 게이지 차트 그리기 (반원형)
 */
document.addEventListener('DOMContentLoaded', function() {
    const canvas = document.getElementById('savingsGauge');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');

    // HTML의 th:data-rate 속성에서 값을 읽어옴
    const achievementRate = parseFloat(canvas.dataset.rate) || 0;
    const remainingRate = Math.max(0, 100 - achievementRate);

    // 실제 차트 생성 루틴
    new Chart(ctx, {
        type: 'doughnut',
        data: {
            datasets: [{
                data: [achievementRate, remainingRate],
                backgroundColor: ['#15164D', '#ececf0'],
                borderWidth: 0,
                circumference: 180,
                rotation: 270,
                cutout: '80%'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: { enabled: false }
            }
        }
    });
});

// 소비 추이
function drawAssetTrend() {
    const canvas = document.getElementById('assetTrendChart');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    const labels = canvas.dataset.labels ? canvas.dataset.labels.split(',') : [];
    const values = canvas.dataset.values ? canvas.dataset.values.split(',').map(Number) : [];

    new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels, // ["02월", "03월", "04월"]
            datasets: [{
                data: values, // [25000000, 28000000, 31000000]
                borderColor: '#15164D',
                backgroundColor: 'rgba(0, 102, 255, 0.1)',
                fill: true,
                tension: 0.4, // ❗ 곡선을 부드럽게 해서 데이터가 적어도 예쁘게 보임
                pointRadius: 5
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: false, // ❗ 자산은 0부터 시작하면 변화가 안 보이니 false
                    grid: { color: '#f3f4f6' },
                    ticks: {
                        callback: (v) => (v / 10000).toLocaleString() + '만'
                    }
                },
                x: { grid: { display: false } }
            },
            plugins: { legend: { display: false } }
        }
    });
}


/**
 * 숫자 포맷팅 유틸리티
 */
function formatNumber(number) {
    return number.toLocaleString('ko-KR');
}

/**
 * 카드 애니메이션
 */
function animateCards() {
    const cards = document.querySelectorAll('.card');

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.style.opacity = '1';
                entry.target.style.transform = 'translateY(0)';
            }
        });
    }, {
        threshold: 0.1
    });

    cards.forEach(card => {
        card.style.opacity = '0';
        card.style.transform = 'translateY(20px)';
        card.style.transition = 'opacity 0.5s, transform 0.5s';
        observer.observe(card);
    });
}



// 페이지 로드 후 애니메이션 실행
window.addEventListener('load', animateCards);


