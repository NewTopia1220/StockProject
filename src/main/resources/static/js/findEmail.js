function checkFindEmail() {
    const form = document.forms["findIdForm"];

    if (!form) {
        return false;
    }

    const name = form.userName.value.trim();
    const phone = form.userPhone.value.trim();

    if (name === "") {
        alert("이름을 입력해주세요.");
        form.userName.focus();
        return false;
    }

    if (phone === "") {
        alert("전화번호를 입력해주세요.");
        form.userPhone.focus();
        return false;
    }

    return true;
}