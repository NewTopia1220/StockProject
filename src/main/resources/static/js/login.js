function checkLogin(){
    const loginForm = document.forms["loginForm"];

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

    return true;
}