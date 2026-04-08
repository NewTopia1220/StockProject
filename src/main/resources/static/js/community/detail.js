document.addEventListener("DOMContentLoaded", function () {
    const likeButton = document.getElementById("likeButton");
    const likeCount = document.getElementById("likeCount");
    const bottomLikeCount = document.getElementById("bottomLikeCount");
    const commentTextarea = document.querySelector(".commentInputArea textarea");
    const commentCounter = document.querySelector(".commentWriteBottom span");

    if (!likeButton) return;

    if (commentTextarea && commentCounter) {
        const updateCommentLength = () => {
            commentCounter.textContent = `${commentTextarea.value.length}/1000`;
        };

        commentTextarea.addEventListener("input", updateCommentLength);
        updateCommentLength();
    }

    likeButton.addEventListener("click", async function () {
        const boardId = likeButton.dataset.boardId;

        try {
            const response = await fetch(`/community/like?board_id=${boardId}`, {
                method: "POST"
            });

            const data = await response.json();

            if (!data.success) {
                alert(data.message || "좋아요 처리에 실패했습니다.");
                return;
            }

            likeCount.textContent = data.likeCount;
            bottomLikeCount.textContent = `좋아요 ${data.likeCount}`;

            if (data.liked) {
                likeButton.classList.add("active");
            } else {
                likeButton.classList.remove("active");
            }
        } catch (error) {
            console.error(error);
            alert("좋아요 처리 중 오류가 발생했습니다.");
        }
    });
});
