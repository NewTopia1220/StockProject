// Savings Planner.js - 저축 플래너 계산 및 업데이트

// 재무 데이터
const MONTHLY_INCOME = 3500000;
const ESSENTIAL_EXPENSES = 1800000;
const AVERAGE_VARIABLE_EXPENSES = 900000;
const AVAILABLE_AMOUNT = MONTHLY_INCOME - ESSENTIAL_EXPENSES - AVERAGE_VARIABLE_EXPENSES;

document.addEventListener('DOMContentLoaded', function() {
    // 입력 필드 이벤트 리스너
    const targetAmountInput = document.getElementById('targetAmount');
    const targetPeriodInput = document.getElementById('targetPeriod');

    targetAmountInput.addEventListener('input', calculateSavings);
    targetPeriodInput.addEventListener('input', calculateSavings);

    // 초기 계산
    calculateSavings();
});

/**
 * 저축 계산 및 UI 업데이트
 */
function calculateSavings() {
    const targetAmount = parseInt(document.getElementById('targetAmount').value) || 0;
    const targetPeriod = parseInt(document.getElementById('targetPeriod').value) || 1;

    // 월 필요 저축액 계산
    const monthlyRequired = Math.ceil(targetAmount / targetPeriod);

    // Gap 계산
    const gap = monthlyRequired - AVAILABLE_AMOUNT;

    // UI 업데이트
    updateMonthlyRequired(monthlyRequired);
    updateGapAnalysis(monthlyRequired, gap);
    updateAlertBanner(gap);
}

/**
 * 월 필요 저축액 표시 업데이트
 */
function updateMonthlyRequired(amount) {
    const element = document.getElementById('monthlyRequired');
    if (element) {
        element.textContent = amount.toLocaleString('ko-KR');
    }

    const requiredElement = document.getElementById('requiredAmount');
    if (requiredElement) {
        requiredElement.textContent = amount.toLocaleString('ko-KR') + '원';
    }
}

/**
 * Gap Analysis 섹션 업데이트
 */
function updateGapAnalysis(monthlyRequired, gap) {
    const availableElement = document.getElementById('availableAmount');
    const gapItem = document.getElementById('gapItem');
    const gapValue = document.getElementById('gapValue');

    if (availableElement) {
        availableElement.textContent = AVAILABLE_AMOUNT.toLocaleString('ko-KR') + '원';
    }

    if (gapItem && gapValue) {
        if (gap > 0) {
            // 부족
            gapItem.classList.add('warning');
            gapItem.querySelector('.analysis-label').textContent = '부족 금액';
            gapValue.textContent = '-' + gap.toLocaleString('ko-KR') + '원';
            gapValue.classList.remove('primary');
            gapValue.classList.add('accent');
        } else {
            // 여유
            gapItem.classList.remove('warning');
            gapItem.querySelector('.analysis-label').textContent = '여유 금액';
            gapValue.textContent = '+' + Math.abs(gap).toLocaleString('ko-KR') + '원';
            gapValue.classList.remove('accent');
            gapValue.classList.add('primary');
        }
    }
}

/**
 * 경고 배너 업데이트
 */
function updateAlertBanner(gap) {
    const alertBanner = document.getElementById('alertBanner');
    const alertTitle = alertBanner.querySelector('.alert-title');
    const alertDescription = alertBanner.querySelector('.alert-description');
    const alertIcon = alertBanner.querySelector('.alert-icon');
    const gapAmountSpan = document.getElementById('gapAmount');

    if (gap > 0) {
        // 부족한 경우
        alertBanner.classList.remove('success');
        alertBanner.classList.add('warning');
        alertIcon.classList.remove('success');
        alertIcon.classList.add('warning');
        alertTitle.classList.remove('success');
        alertTitle.classList.add('warning');

        alertTitle.textContent = '저축 목표 달성 어려움';
        alertDescription.innerHTML = `현재 수입과 지출 패턴으로는 월 <span style="font-weight: 600;" id="gapAmount">${gap.toLocaleString('ko-KR')}원</span>이 부족합니다. 지출을 줄이거나 수입을 늘려보세요.`;

        // SVG 아이콘 변경 (경고)
        alertIcon.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/>
                <line x1="12" y1="9" x2="12" y2="13"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
        `;
    } else {
        // 충분한 경우
        alertBanner.classList.remove('warning');
        alertBanner.classList.add('success');
        alertIcon.classList.remove('warning');
        alertIcon.classList.add('success');
        alertTitle.classList.remove('warning');
        alertTitle.classList.add('success');

        alertTitle.textContent = '목표 달성 가능';
        alertDescription.innerHTML = `현재 재무 상태로 목표를 달성할 수 있습니다. 월 <span style="font-weight: 600;">${Math.abs(gap).toLocaleString('ko-KR')}원</span>의 여유가 있습니다.`;

        // SVG 아이콘 변경 (체크)
        alertIcon.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="20 6 9 17 4 12"/>
            </svg>
        `;
    }
}

/**
 * 숫자 포맷팅 유틸리티
 */
function formatNumber(number) {
    return number.toLocaleString('ko-KR');
}

/**
 * 입력값 유효성 검사
 */
function validateInputs() {
    const targetAmountInput = document.getElementById('targetAmount');
    const targetPeriodInput = document.getElementById('targetPeriod');

    if (targetAmountInput.value < 0) {
        targetAmountInput.value = 0;
    }

    if (targetPeriodInput.value < 1) {
        targetPeriodInput.value = 1;
    }
}

// 입력값 검증 이벤트
document.getElementById('targetAmount').addEventListener('blur', validateInputs);
document.getElementById('targetPeriod').addEventListener('blur', validateInputs);


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
