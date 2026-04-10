
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


// 사이드 바에 사용자 성이름 아이콘 가져오기
document.addEventListener('DOMContentLoaded', () => {
    const userNameEl = document.querySelector('.user-name');
    const userAvatarEl = document.querySelector('.user-avatar');

    if (userNameEl && userAvatarEl) {
        const fullName = userNameEl.textContent.trim();
        userAvatarEl.textContent = fullName.charAt(0) || '';
    }
});


// fast api
function openModal() {
    document.getElementById('transactionModal').style.display = 'flex';
}


// 페이지의 HTML 요소가 모두 로드된 후 실행되도록 보장합니다.
document.addEventListener('DOMContentLoaded', function() {

    const transactionForm = document.getElementById('transactionForm');

    // 폼이 존재하는지 확인 후 이벤트 리스너 등록
    if (transactionForm) {
        transactionForm.addEventListener('submit', async function(e) {
            e.preventDefault();
            console.log("지출 추가 전송 시작..."); // 실행 확인용 로그

            const vendor = document.getElementById('vendor').value;
            const amount = document.getElementById('amount').value;
            const transaction_date = document.getElementById('transaction_date').value;
            const user_id = document.getElementById('userId').value;
            console.log("전송할 유저 아이디:", user_id); // 👈 값이 잘 나오는지 콘솔에서 확인!

            const FASTAPI_URL = "http://127.0.0.1:8000/classify_transaction";

            const payload = {
                vendor: vendor,
                transaction_date: transaction_date,
                amount: parseFloat(amount),
                user_id: parseInt(user_id)
            };

            try {
                const response = await fetch(FASTAPI_URL, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });

                const data = await response.json();
                console.log("서버 응답:", data); // 응답 데이터 확인

                if (data.result && data.result.saved) {
                    alert(`분류 완료: ${data.result.category}\n지출 내역이 저장되었습니다.`);
                    closeModal();
                    location.reload();
                } else {
                    alert("저장 중 오류가 발생했습니다.");
                }
            } catch (error) {
                console.error("Error:", error);
                alert("서버와 통신할 수 없습니다. FastAPI 서버를 확인하세요.");
            }
        });
    } else {
        console.error("에러: transactionForm 요소를 찾을 수 없습니다.");
    }
});


function closeModal() {
    document.getElementById('transactionModal').style.display = 'none';
}





// 월별 지출 5개월 가져오기
document.addEventListener("DOMContentLoaded", function () {
    const canvas = document.getElementById("monthlyChart");

    const trendData = JSON.parse(canvas.dataset.trend);
    const selectedMonth = Number(canvas.dataset.selectedMonth);  // 여기서 숫자형으로 받음

    const labels = trendData.map(d => d.month + "월");
    const values = trendData.map(d => d.total);

    // const currentMonth = new Date().getMonth() + 1;
    const bgColors = trendData.map(d => d.month === selectedMonth ? "#3b82f6" : "#2f2f2f");

    new Chart(canvas, {
        type: "bar",
        data: {
            labels: labels,
            datasets: [{
                data: values,
                backgroundColor: bgColors,
                borderRadius: 12,
                borderSkipped: false
            }]
        },
        options: {
            plugins: {
                legend: { display: false },
                tooltip: {
                    padding: 10,             // 툴팁 박스 내부 여백 확대
                    bodyFont: {
                        size: 16,           // 본문 글자 크기 키우기
                        weight: 'bold'      // 글자 두껍게
                    },
                    titleFont: {
                        size: 18,           // 제목 글자 크기 (없으면 생략 가능)
                        weight: 'bold'
                    },
                    callbacks: {
                        label: function(ctx) {
                            return ctx.raw.toLocaleString() + "원";
                        }
                    }
                }
            },
            scales: {
                x: { ticks: { color: "#aaa" }, grid: { display: false } },
                y: { display: false }
            }
        }
    });
});



