window.addEventListener("load", function () {
    const communityMain = document.querySelector(".communityMain");
    const communityRight = document.getElementById("communityRight") || document.querySelector(".communityRight");

    const panelConfigs = [
        { toggleId: "togglePopular", panelId: "popularPanel", key: "popular" },
        { toggleId: "toggleGuide", panelId: "guidePanel", key: "guide" },
        { toggleId: "toggleAnalysis", panelId: "analysisPanel", key: "analysis" },
        { toggleId: "togglePrice", panelId: "pricePanel", key: "price" }
    ];

    const STORAGE_KEY = "communityRightPanelSettingsV2";

    if (!communityMain || !communityRight) {
        return;
    }

    function getCheckbox(toggleId) {
        return document.getElementById(toggleId);
    }

    function getPanel(panelId) {
        return document.getElementById(panelId);
    }

    function setPanelVisible(panel, visible) {
        if (!panel) return;
        panel.style.display = visible ? "" : "none";
    }

    function updateRightLayout() {
        let visibleCount = 0;

        panelConfigs.forEach(function (config) {
            const panel = getPanel(config.panelId);
            if (panel && panel.style.display !== "none") {
                visibleCount++;
            }
        });

        if (visibleCount === 0) {
            communityRight.style.display = "none";
            communityMain.classList.add("right-empty");
        } else {
            communityRight.style.display = "";
            communityMain.classList.remove("right-empty");
        }
    }

    function saveSettings() {
        const settings = {};

        panelConfigs.forEach(function (config) {
            const checkbox = getCheckbox(config.toggleId);
            settings[config.key] = checkbox ? checkbox.checked : true;
        });

        localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    }

    function loadSettings() {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (!saved) return;

        try {
            const settings = JSON.parse(saved);

            panelConfigs.forEach(function (config) {
                const checkbox = getCheckbox(config.toggleId);
                if (checkbox && typeof settings[config.key] === "boolean") {
                    checkbox.checked = settings[config.key];
                }
            });
        } catch (e) {
            localStorage.removeItem(STORAGE_KEY);
        }
    }

    function applySettings() {
        panelConfigs.forEach(function (config) {
            const checkbox = getCheckbox(config.toggleId);
            const panel = getPanel(config.panelId);

            if (!checkbox || !panel) return;

            setPanelVisible(panel, checkbox.checked);
        });

        updateRightLayout();
    }

    function bindToggles() {
        panelConfigs.forEach(function (config) {
            const checkbox = getCheckbox(config.toggleId);
            if (!checkbox) return;

            checkbox.addEventListener("change", function () {
                saveSettings();
                applySettings();
            });
        });
    }

    function bindAccordion() {
        const accordionButtons = document.querySelectorAll(".accordionButton");

        accordionButtons.forEach(function (button) {
            button.addEventListener("click", function () {
                const item = button.closest(".accordionItem");
                if (!item) return;

                item.classList.toggle("open");
            });
        });
    }

    loadSettings();
    applySettings();
    bindToggles();
    bindAccordion();
});