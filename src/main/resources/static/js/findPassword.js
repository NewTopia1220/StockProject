document.addEventListener("DOMContentLoaded", function () {
    const verifyPanel = document.getElementById("verifyPanel"); // 본인 확인
    const resetPanel = document.getElementById("resetPanel");  // 재설정

    // 사용자 입력값
    const verifyEmail = document.getElementById("verifyEmail");
    const verifyName = document.getElementById("verifyName");
    const verifyPhone = document.getElementById("verifyPhone");

    // submit할 때 정보도 같이 넘겨야 함 -> hidden input으로
    const hiddenUserEmail = document.getElementById("hiddenUserEmail");
    const hiddenUserName = document.getElementById("hiddenUserName");
    const hiddenUserPhone = document.getElementById("hiddenUserPhone");

    const verifyMessage = document.getElementById("verifyMessage");
    const resetMessage = document.getElementById("resetMessage");

    // 확인된 이메일 출력
    const summaryEmail = document.getElementById("summaryEmail");

    const moveResetStepBtn = document.getElementById("moveResetStepBtn");
    const goPrevBtn = document.getElementById("goPrevBtn");

    // 전화번호 자동 하이픈 처리
    if (verifyPhone) {
        verifyPhone.addEventListener("input", function (event) {
            event.target.value = formatPhoneNumber(event.target.value);
        });
    }

    // 사용자 입력 중일 때 에러 메시지 지우기
    [verifyEmail, verifyName, verifyPhone].forEach(function (element) {
        if (!element) {
            return;
        }

        element.addEventListener("input", function () {
            if (verifyMessage) {
                verifyMessage.textContent = "";
            }
        });
    });

    // '다음' 버튼 클릭 시
    if (moveResetStepBtn) {
        moveResetStepBtn.addEventListener("click", function () {
            // 현재 입력값을 가져옴
            const emailValue = verifyEmail.value.trim();
            const nameValue = verifyName.value.trim();
            const phoneValue = verifyPhone.value.trim();
            const phoneOnly = phoneValue.replace(/[^0-9]/g, "");

            if (verifyMessage) {
                verifyMessage.textContent = "";
            }

            if (emailValue === "" || nameValue === "" || phoneValue === "") {
                if (verifyMessage) {
                    verifyMessage.textContent = "이메일, 이름, 휴대폰 번호를 모두 입력해주세요.";
                }
                return;
            }

            if (!isValidEmail(emailValue)) {
                if (verifyMessage) {
                    verifyMessage.textContent = "이메일 형식을 확인해주세요.";
                }
                return;
            }

            if (phoneOnly.length < 10) {
                if (verifyMessage) {
                    verifyMessage.textContent = "휴대폰 번호 형식을 확인해주세요.";
                }
                return;
            }

            // 1단계 값을 hidden input에 복사
            if (hiddenUserEmail) {
                hiddenUserEmail.value = emailValue;
            }

            if (hiddenUserName) {
                hiddenUserName.value = nameValue;
            }

            if (hiddenUserPhone) {
                hiddenUserPhone.value = phoneValue;
            }

            if (summaryEmail) {
                summaryEmail.textContent = emailValue;
            }

            if (verifyPanel) {
                verifyPanel.classList.add("hidden");
            }

            if (resetPanel) {
                resetPanel.classList.remove("hidden");
            }

            const newPasswordInput = document.getElementById("newPassword");
            if (newPasswordInput) {
                newPasswordInput.focus();
            }
        });
    }

    if (goPrevBtn) {
        goPrevBtn.addEventListener("click", function () {
            if (resetMessage) {
                resetMessage.textContent = "";
            }

            if (resetPanel) {
                resetPanel.classList.add("hidden");
            }

            if (verifyPanel) {
                verifyPanel.classList.remove("hidden");
            }
        });
    }
});

// 비밀번호 변경 버튼 누를 때
function validateResetPasswordForm() {
    const newPassword = document.getElementById("newPassword");
    const reNewPassword = document.getElementById("reNewPassword");
    const resetMessage = document.getElementById("resetMessage");

    if (!newPassword || !reNewPassword || !resetMessage) {
        return true;
    }

    const newPasswordValue = newPassword.value.trim();
    const reNewPasswordValue = reNewPassword.value.trim();

    resetMessage.textContent = "";

    if (newPasswordValue === "" || reNewPasswordValue === "") {
        resetMessage.textContent = "새 비밀번호를 모두 입력해주세요.";
        return false;
    }

    if (newPasswordValue.length < 5 || newPasswordValue > 8) {
        resetMessage.textContent = "비밀번호는 5 ~ 8글자로 입력해주세요.";
        return false;
    }

    if (newPasswordValue !== reNewPasswordValue) {
        resetMessage.textContent = "새 비밀번호가 서로 일치하지 않습니다.";
        return false;
    }

    return true;
}

// 번호 = 숫자만 남기고 최대 11자리 허용
function formatPhoneNumber(value) {
    // 하이픈 X
    const onlyNumber = value.replace(/[^0-9]/g, "").slice(0, 11);

    if (onlyNumber.length < 4) {
        return onlyNumber;
    }

    if (onlyNumber.length < 8) {
        return onlyNumber.replace(/(\d{3})(\d+)/, "$1-$2");
    }

    // 자동 포맷
    return onlyNumber.replace(/(\d{3})(\d{4})(\d+)/, "$1-$2-$3");
}

function isValidEmail(email) {
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailPattern.test(email);
}