function buildHalfDoughnutChart(canvas, achievementRate) {
    const remainingRate = Math.max(0, 100 - achievementRate);

    return new Chart(canvas.getContext('2d'), {
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
}

function drawAssetTrend() {
    const canvas = document.getElementById('assetTrendChart');
    if (!canvas || typeof Chart === 'undefined') {
        return;
    }

    const labels = canvas.dataset.labels ? canvas.dataset.labels.split(',') : [];
    const values = canvas.dataset.values ? canvas.dataset.values.split(',').map(Number) : [];

    new Chart(canvas.getContext('2d'), {
        type: 'line',
        data: {
            labels,
            datasets: [{
                data: values,
                borderColor: '#15164D',
                backgroundColor: 'rgba(0, 102, 255, 0.1)',
                fill: true,
                tension: 0.4,
                pointRadius: 5
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: false,
                    grid: { color: '#f3f4f6' },
                    ticks: {
                        callback: (value) => `${(Number(value) / 10000).toLocaleString()} man`
                    }
                },
                x: { grid: { display: false } }
            },
            plugins: {
                legend: { display: false }
            }
        }
    });
}

function animateCards() {
    const cards = document.querySelectorAll('.card');
    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (!entry.isIntersecting) {
                return;
            }

            entry.target.style.opacity = '1';
            entry.target.style.transform = 'translateY(0)';
        });
    }, { threshold: 0.1 });

    cards.forEach((card) => {
        card.style.opacity = '0';
        card.style.transform = 'translateY(20px)';
        card.style.transition = 'opacity 0.5s, transform 0.5s';
        observer.observe(card);
    });
}

function initUserAvatar() {
    const userNameElement = document.querySelector('.user-name');
    const userAvatarElement = document.querySelector('.user-avatar');
    if (!userNameElement || !userAvatarElement) {
        return;
    }

    const fullName = userNameElement.textContent.trim();
    userAvatarElement.textContent = fullName.charAt(0) || '';
}

function openModal() {
    const modal = document.getElementById('transactionModal');
    if (modal) {
        modal.style.display = 'flex';
    }
}

function closeModal() {
    const modal = document.getElementById('transactionModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

function bindTransactionForm() {
    const transactionForm = document.getElementById('transactionForm');
    if (!transactionForm) {
        return;
    }

    transactionForm.addEventListener('submit', async (event) => {
        event.preventDefault();

        const payload = {
            vendor: document.getElementById('vendor')?.value || '',
            transaction_date: document.getElementById('transaction_date')?.value || '',
            amount: parseFloat(document.getElementById('amount')?.value || '0'),
            user_id: parseInt(document.getElementById('userId')?.value || '0', 10)
        };

        try {
            const response = await fetch('http://127.0.0.1:8001/classify_transaction', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await response.json();

            if (data.result && data.result.saved) {
                alert(`Category saved: ${data.result.category}`);
                closeModal();
                location.reload();
                return;
            }

            alert('Unable to save the spending history.');
        } catch (error) {
            console.error('Error:', error);
            alert('Unable to reach the spending classification server.');
        }
    });
}

function initMonthlyChart() {
    const canvas = document.getElementById('monthlyChart');
    if (!canvas || typeof Chart === 'undefined' || !canvas.dataset.trend) {
        return;
    }

    const trendData = JSON.parse(canvas.dataset.trend);
    const selectedMonth = Number(canvas.dataset.selectedMonth);
    const labels = trendData.map((item) => `${item.month}M`);
    const values = trendData.map((item) => item.total);
    const backgroundColor = trendData.map((item) => item.month === selectedMonth ? '#3182F6' : '#d2d2d2');

    new Chart(canvas, {
        type: 'bar',
        data: {
            labels,
            datasets: [{
                data: values,
                backgroundColor,
                borderRadius: 12,
                borderSkipped: false
            }]
        },
        options: {
            plugins: {
                legend: { display: false },
                tooltip: {
                    padding: 10,
                    bodyFont: { size: 16, weight: 'bold' },
                    titleFont: { size: 18, weight: 'bold' },
                    callbacks: {
                        label: (context) => `${Number(context.raw).toLocaleString()} KRW`
                    }
                }
            },
            scales: {
                x: { ticks: { color: '#aaa' }, grid: { display: false } },
                y: { display: false }
            }
        }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    const savingsGauge = document.getElementById('savingsGauge');
    if (savingsGauge && typeof Chart !== 'undefined') {
        const achievementRate = parseFloat(savingsGauge.dataset.rate || '0');
        buildHalfDoughnutChart(savingsGauge, achievementRate);
    }

    drawAssetTrend();
    initUserAvatar();
    bindTransactionForm();
    initMonthlyChart();
});

window.addEventListener('load', animateCards);
