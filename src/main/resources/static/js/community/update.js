document.addEventListener("DOMContentLoaded", function () {
    const newsKeywordInput = document.getElementById("newsKeyword");
    const newsSearchResult = document.getElementById("newsSearchResult");
    const selectedNewsBox = document.getElementById("selectedNewsBox");
    const newsLinkInput = document.getElementById("newsLink");
    const initialNewsLink = selectedNewsBox?.dataset.initialLink?.trim()
        || newsLinkInput?.value?.trim()
        || "";
    const initialNewsTitle = selectedNewsBox?.dataset.initialTitle?.trim() || "";

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
        if (!newsSearchResult) return;
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
                    "<span>" + escapeHtml(item.summary || "요약 없음").slice(0, 90) + "...</span>";

                button.addEventListener("click", function () {
                    renderSelectedNews(item);
                    newsSearchResult.innerHTML = "";
                    newsKeywordInput.value = "";
                });

                newsSearchResult.appendChild(button);
            });
        } catch (error) {
            renderEmptyMessage("뉴스 검색 중 오류가 발생했습니다.");
        }
    }

    if (newsKeywordInput) {
        renderEmptyMessage("두 글자 이상 입력하면 관련 뉴스를 찾을 수 있어요.");

        if (initialNewsLink) {
            renderSelectedNews({
                link: initialNewsLink,
                title: initialNewsTitle || initialNewsLink
            });
        }

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

const bannedWords = [
    "시발", "병신", "개새끼", "뒤져", "뒤질", "뒤졌", "존나", "십창",
    "맘충", "여적여", "개줌마", "빨갱이", "찍어야", "낙선시켜",
    "좌파", "우파", "정치충", "종북", "느금", "개비", "니애미",
    "샤갈", "ㅅㅂ", "썅", "tlqkf", "야발", "시바", "좇", "샹", "시앙", "바보"
];

function findBannedWord(...values) {
    for (const value of values) {
        const text = String(value || "").trim().toLowerCase();

        for (const bannedWord of bannedWords) {
            if (text.includes(bannedWord.toLowerCase())) {
                return bannedWord;
            }
        }
    }
    return null;
}

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

    const bannedWord = findBannedWord(
        communityWriteForm.title.value,
        communityWriteForm.content.value,
        communityWriteForm.tagNames ? communityWriteForm.tagNames.value : ""
    );

    if (bannedWord) {
        alert("금지어가 포함되어 있습니다: " + bannedWord);
        return false;
    }

    return true;
}
