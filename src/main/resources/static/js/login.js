// 페이지 열릴 때 저장된 이메일 불러오기
window.addEventListener('DOMContentLoaded', function(){
    loadSavedEmail();

    // 체크 해제하는 순간 로그인 지우고 싶으면
    const loginForm = document.forms["loginForm"];
        if (loginForm && loginForm.saveId) {
            loginForm.saveId.addEventListener("change", function () {
                if (!this.checked) {
                    localStorage.removeItem("savedLoginEmail");
                }
            });
        }
});

function loadSaveEmail(){
    const loginForm = document.forms["loginForm"];
    if (!loginForm){
        return;
    }

    const savedEmail = localStorage.getItem("savedLoginEmail");

    if (savedEmail && savedEmail.trim() !== ""){
        loginForm.userEmail.value = savedEmail;

        if (loginForm.saveId) {
            loginForm.saveId.checked = true;
        }
    }
}

function checkLogin(){
    const loginForm = document.forms["loginForm"];

    if (!loginForm){
        return false;
    }

    if(loginForm.userEmail.value.trim() == ""){
        alert("이메일을 입력해주세요.");
        loginForm.userEmail.focus();
        return false;
    }

    if(loginForm.userPassword.value.trim() == ""){
        alert("비밀번호를 입력해주세요.");
        loginForm.userPassword.focus();
        return false;
    }

    const email = loginForm.userEmail.value.trim();

        if (loginForm.saveId && loginForm.saveId.checked) {
            localStorage.setItem("savedLoginEmail", email);
        } else {
            localStorage.removeItem("savedLoginEmail");
        }

    return true;
}