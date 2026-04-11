// function toggleEdit(type) {
//     const section = document.getElementById(`${type}-section`);
//     const displays = section.querySelectorAll('.display-mode');
//     const edits = section.querySelectorAll('.edit-mode');
//
//     displays.forEach(el => el.style.display = el.style.display === 'none' ? 'block' : 'none');
//     edits.forEach(el => el.style.display = el.style.display === 'none' ? 'block' : 'none');
// }
//
//
//
// function saveEdit(type) {
//     // 1. 아까 HTML에 숨겨둔 user-num 값을 가져옵니다.
//     const userNum = document.getElementById('user-num').value;
//     // 2. 입력창에 적힌 새 값을 가져옵니다.
//     const newValue = document.getElementById(`input-${type}`).value;
//
//     // 서버로 데이터 전송
//     fetch('/user/update-profile', {
//         method: 'POST',
//         headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
//         // ⭐️ 이메일 대신 num을 파라미터로 보냅니다.
//         body: `num=${userNum}&type=${type}&value=${newValue}`
//     })
//         .then(response => response.text())
//         .then(result => {
//             if (result === 'success') {
//                 location.reload(); // 성공 시 새로고침
//             } else {
//                 alert('변경에 실패했습니다.');
//             }
//         });
// }
//
//
//
//
// function toggleEdit(type) {
//     const section = document.getElementById(`${type}-section`);
//     if(!section) {
//         console.error(type + "-section을 찾을 수 없습니다.");
//         return;
//     }
//
//     const displays = section.querySelectorAll('.display-mode');
//     const edits = section.querySelectorAll('.edit-mode');
//
//     // ⭐️ display: none 이면 보이게, 아니면 숨기게
//     displays.forEach(el => {
//         el.style.display = (el.style.display === 'none') ? '' : 'none';
//     });
//
//     edits.forEach(el => {
//         el.style.display = (el.style.display === 'none') ? '' : 'none';
//     });
// }
//
//
//
// /**
//  * 카드 애니메이션
//  */
// function animateCards() {
//     const cards = document.querySelectorAll('.card');
//
//     const observer = new IntersectionObserver((entries) => {
//         entries.forEach(entry => {
//             if (entry.isIntersecting) {
//                 entry.target.style.opacity = '1';
//                 entry.target.style.transform = 'translateY(0)';
//             }
//         });
//     }, {
//         threshold: 0.1
//     });
//
//     cards.forEach(card => {
//         card.style.opacity = '0';
//         card.style.transform = 'translateY(20px)';
//         card.style.transition = 'opacity 0.5s, transform 0.5s';
//         observer.observe(card);
//     });
// }
//
// // 페이지 로드 후 애니메이션 실행
// window.addEventListener('load', animateCards);
//
// // 알림 설정 토글 이벤트
// document.querySelectorAll('.toggle-input').forEach(toggle => {
//     toggle.addEventListener('change', function() {
//         const type = this.dataset.type; // "stock"
//         const isEnabled = this.checked ? 1 : 0; // 체크되면 1, 아니면 0
//         const userNum = document.getElementById('user-num').value;
//
//         fetch('/api/user/settings/notification', {
//             method: 'POST',
//             headers: { 'Content-Type': 'application/json' },
//             body: JSON.stringify({
//                 userNum: userNum,
//                 status: isEnabled
//             })
//         })
//             .then(response => response.json())
//             .then(data => {
//                 if (!data.ok) {
//                     alert('설정 저장에 실패했습니다.');
//                     this.checked = !this.checked; // 실패 시 원래대로 복구
//                 }
//             })
//             .catch(err => {
//                 console.error('Error:', err);
//                 this.checked = !this.checked;
//             });
//     });
// });

/**
 * 프로필 수정 모드 전환
 */
function toggleEdit(type) {
    const section = document.getElementById(`${type}-section`);
    if(!section) {
        console.error(type + "-section을 찾을 수 없습니다.");
        return;
    }

    const displays = section.querySelectorAll('.display-mode');
    const edits = section.querySelectorAll('.edit-mode');

    // display 상태를 반전시켜 수정 모드 ON/OFF
    displays.forEach(el => {
        el.style.display = (el.style.display === 'none') ? '' : 'none';
    });
    edits.forEach(el => {
        el.style.display = (el.style.display === 'none') ? '' : 'none';
    });
}

/**
 * 프로필 정보 저장 (이름, 전화번호 등)
 */
function saveEdit(type) {
    const userNum = document.getElementById('user-num').value;
    const newValue = document.getElementById(`input-${type}`).value;

    fetch('/user/update-profile', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `num=${userNum}&type=${type}&value=${newValue}`
    })
        .then(response => response.text())
        .then(result => {
            if (result === 'success') {
                location.reload();
            } else {
                alert('변경에 실패했습니다.');
            }
        });
}

/**
 * 알림 설정 토글 이벤트 (주가 알림 등)
 */
// document.querySelectorAll('.toggle-input').forEach(toggle => {
//     toggle.addEventListener('change', function() {
//         const type = this.dataset.type; // HTML의 data-type="stock" 값
//         const isEnabled = this.checked ? 1 : 0; // 체크되면 1, 해제되면 0
//         const userNum = document.getElementById('user-num').value;
//
//         fetch('/api/user/settings/notification', {
//             method: 'POST',
//             headers: { 'Content-Type': 'application/json' },
//             body: JSON.stringify({
//                 userNum: userNum,
//                 status: isEnabled
//             })
//         })
//             .then(response => response.json())
//             .then(data => {
//                 if (!data.ok) {
//                     alert('설정 저장에 실패했습니다.');
//                     this.checked = !this.checked; // 실패 시 토글 복구
//                 }
//             })
//             .catch(err => {
//                 console.error('Error:', err);
//                 this.checked = !this.checked;
//             });
//     });
// });
// 모든 토글 스위치에 이벤트 리스너 등록
document.querySelectorAll('.toggle-input').forEach(toggle => {
    toggle.addEventListener('change', function() {
        const type = this.getAttribute('data-type'); // 'stock' 또는 'comment'
        const status = this.checked ? 1 : 0;

        fetch('/api/user/settings/notification', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                type: type,
                status: status
            })
        })
            .then(res => res.json())
            .then(data => {
                if (data.ok) {
                    console.log(`${type} 알림 설정 변경 완료: ${status}`);
                }
            });
    });
});



/**
 * 카드 등장 애니메이션 (Intersection Observer)
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
    }, { threshold: 0.1 });

    cards.forEach(card => {
        card.style.opacity = '0';
        card.style.transform = 'translateY(20px)';
        card.style.transition = 'opacity 0.5s, transform 0.5s';
        observer.observe(card);
    });
}

window.addEventListener('load', animateCards);