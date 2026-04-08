document.addEventListener("DOMContentLoaded", function() {
    const fileInput = document.getElementById("attachFile");
    const fileFake = document.getElementById("fileNameText");

    if (!fileInput || !fileFake) {
        return;
    }

    fileInput.addEventListener("change", function() {
        if (fileInput.files && fileInput.files.length > 0) {
            fileFake.textContent = fileInput.files[0].name;
            fileFake.style.color = "#4b5563";
        } else {
            fileFake.textContent = "파일을 선택해주세요";
            fileFake.style.color = "#c0c6d1";
        }
    });
});

// 유효성 검사
function check() {
    const communityWriteForm = document.forms["communityWriteForm"];

    if (communityWriteForm.category.value == "") {
        alert("카테고리 최소 하나는 선택해주세요");
        return false;
    }

    if (communityWriteForm.title.value == "") {
        alert("제목을 작성해주세요");
        return false;
    }

    if (communityWriteForm.content.value == "") {
        alert("내용을 작성해주세요");
        return false;
    }

    // 첨부파일 = 선택
    return true;
}

