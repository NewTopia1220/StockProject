document.addEventListener("DOMContentLoaded", function () {
    const newsKeywordInput = document.getElementById("newsKeyword");
    const newsSearchResult = document.getElementById("newsSearchResult");
    const selectedNewsBox = document.getElementById("selectedNewsBox");
    const newsLinkInput = document.getElementById("newsLink");

    let searchTimer = null;

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function renderEmptyMessage(message) {
        if (!newsSearchResult) {
            return;
        }
        newsSearchResult.innerHTML = '<div class="newsEmptyMessage">' + escapeHtml(message) + "</div>";
    }

    function clearSelectedNews() {
        if (selectedNewsBox) {
            selectedNewsBox.innerHTML = "";
        }
        if (newsLinkInput) {
            newsLinkInput.value = "";
        }
    }

    function renderSelectedNews(item) {
        if (!selectedNewsBox || !newsLinkInput) {
            return;
        }

        newsLinkInput.value = item.link;
        selectedNewsBox.innerHTML =
            '<div class="selectedNewsTag">' +
            '<span class="selectedNewsTagText">' + escapeHtml(item.title) + "</span>" +
            '<button type="button" class="selectedNewsRemove" aria-label="선택한 뉴스 제거">×</button>' +
            "</div>";

        const removeButton = selectedNewsBox.querySelector(".selectedNewsRemove");
        if (removeButton) {
            removeButton.addEventListener("click", function () {
                clearSelectedNews();
                if (newsKeywordInput) {
                    newsKeywordInput.focus();
                }
            });
        }
    }

    async function searchRelatedNews(keyword) {
        if (!newsSearchResult) {
            return;
        }

        if (!keyword || keyword.trim().length < 2) {
            renderEmptyMessage("두 글자 이상 입력하면 관련 뉴스를 찾을 수 있어요.");
            return;
        }

        try {
            const response = await fetch("/community/news/search?keyword=" + encodeURIComponent(keyword.trim()), {
                headers: {
                    "X-Requested-With": "XMLHttpRequest"
                }
            });

            if (!response.ok) {
                throw new Error("search_failed");
            }

            const items = await response.json();

            if (!Array.isArray(items) || items.length === 0) {
                renderEmptyMessage("검색된 뉴스가 없습니다.");
                return;
            }

            newsSearchResult.innerHTML = "";

            items.forEach(function (item) {
                const button = document.createElement("button");
                button.type = "button";
                button.className = "newsSearchItem";
                button.innerHTML =
                    "<strong>" + escapeHtml(item.title || "제목 없음") + "</strong>" +
                    "<span>" + escapeHtml(item.summary || "요약 없음") + "</span>";

                button.addEventListener("click", function () {
                    renderSelectedNews(item);
                });

                newsSearchResult.appendChild(button);
            });
        } catch (error) {
            renderEmptyMessage("뉴스 검색 중 오류가 발생했습니다.");
        }
    }

    if (newsKeywordInput) {
        renderEmptyMessage("두 글자 이상 입력하면 관련 뉴스를 찾을 수 있어요.");

        newsKeywordInput.addEventListener("input", function () {
            const keyword = newsKeywordInput.value;

            if (searchTimer) {
                clearTimeout(searchTimer);
            }

            searchTimer = setTimeout(function () {
                searchRelatedNews(keyword);
            }, 250);
        });
    }
});

function check() {
    const communityWriteForm = document.forms["communityWriteForm"];

    if (communityWriteForm.category.value === "") {
        alert("카테고리를 선택해주세요.");
        return false;
    }

    if (communityWriteForm.title.value.trim() === "") {
        alert("제목을 입력해주세요.");
        return false;
    }

    if (communityWriteForm.content.value.trim() === "") {
        alert("내용을 입력해주세요.");
        return false;
    }

    return true;
}
