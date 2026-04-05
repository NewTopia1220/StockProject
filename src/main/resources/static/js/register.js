function check(){

	const registerForm = document.forms["registerForm"];

	// 이름
	if (registerForm.userName.value == ""){
		alert("이름 누락");
		registerForm.userName.focus();
		return false;
	}

	// 이메일
	if (registerForm.userEmail.value == ""){
		alert("이메일 누락");
		registerForm.userEmail.focus();
		return false;
	}

	// 비밀번호
	if (registerForm.userPassword.value == ""){
		alert("비밀번호 누락");
		registerForm.userPassword.focus();
		return false;
	}

	if (registerForm.userPassword.value.length < 5 || registerForm.userPassword.value.length > 8){
		alert("비밀번호는 5 ~ 8 글자 사이");
		registerForm.userPassword.select();
		return false;
	}

	if (isNaN(Number(registerForm.userPassword.value))){
		alert("비밀번호는 숫자만 입력");
		registerForm.userPassword.select();
		return false;
	}

	// 비밀번호 확인
	if (registerForm.reUserPassword.value == ""){
		alert("비밀번호 확인 누락");
		registerForm.reUserPassword.focus();
		return false;
	}

	if (registerForm.reUserPassword.value.length < 5 || registerForm.reUserPassword.value.length > 8){
		alert("비밀번호는 5 ~ 8 글자 사이");
		registerForm.reUserPassword.select();
		return false;
	}

	if (isNaN(Number(registerForm.reUserPassword.value))){
		alert("비밀번호는 숫자만 입력");
		registerForm.reUserPassword.select();
		return false;
	}

	// 비밀번호 일치 여부
	if (registerForm.userPassword.value != registerForm.reUserPassword.value){
		alert("비밀번호가 일치하지 않습니다.");
		registerForm.reUserPassword.select();
		return false;
	}

	// 전화번호 일치 여부
	if (registerForm.userPhone.value == ""){
	    alert("전화번호 누락");
	    registerForm.userPhone.focus();
	    return false;
	}

	return true;
}