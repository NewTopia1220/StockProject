let mainChart = null;
let currentCode = '005930';
let currentStockName = '';
let currentTab = 'daily';
let currentChartSource = 'stock';
let searchTimer = null;
let pollingStarted = false;
let latestChartData = emptyChartData();
let aiRotatorItems = [];
let aiRotatorIndex = 0;
let aiRotatorTimer = null;
let aiRefreshToken = 0;
// 환율은 같은 통화를 반복 조회하는 경우가 많아서 브라우저 메모리에 짧게 캐시한다.
const EXCHANGE_QUOTE_TTL_MS = 60 * 1000;
const EXCHANGE_CHART_TTL_MS = 30 * 60 * 1000;
const exchangeQuoteCache = new Map();
const exchangeChartCache = new Map();
const exchangeQuoteRequests = new Map();
const exchangeChartRequests = new Map();

document.addEventListener('DOMContentLoaded', () => {
    currentStockName = document.getElementById('chartStockName')?.textContent.trim() || '삼성전자';
    currentCode = document.getElementById('chartStockCode')?.textContent.trim() || currentCode;

    animateGauges();
    bindChartTabs();
    bindSearchHandlers();
    bindTickerCards();
    bindTopStockTicker();
    bindExchangeSelector();
    bindSectorTrendCards();
    syncChartTabs();
    applyActiveTickerState();
    initAiRotator();
    startPolling();
});

function bindChartTabs() {
    document.querySelectorAll('.chartTab').forEach((button) => {
        button.addEventListener('click', async () => {
            if (button.disabled) {
                return;
            }

            currentTab = button.dataset.tab;
            syncChartTabs();
            await updateMainChart();
        });
    });
}

function bindSearchHandlers() {
    const searchInput = document.getElementById('stockSearch');
    const dropdown = document.getElementById('searchDropdown');

    if (!searchInput || !dropdown) {
        return;
    }

    searchInput.addEventListener('input', (event) => {
        const keyword = event.target.value.trim();
        clearTimeout(searchTimer);

        if (keyword.length < 1) {
            dropdown.style.display = 'none';
            dropdown.innerHTML = '';
            return;
        }

        searchTimer = setTimeout(async () => {
            try {
                const items = await fetchJson(`/api/stock/search?keyword=${encodeURIComponent(keyword)}`);
                if (!Array.isArray(items) || items.length === 0) {
                    dropdown.style.display = 'none';
                    dropdown.innerHTML = '';
                    return;
                }

                dropdown.innerHTML = items.slice(0, 8).map((item) => `
                    <div class="searchItem" data-code="${item.code}" data-name="${item.name}">
                        <div>
                            <span class="item-name">${item.name}</span>
                            <span class="item-market">${item.market || ''}</span>
                        </div>
                        <span class="item-code">${item.code}</span>
                    </div>
                `).join('');

                dropdown.querySelectorAll('.searchItem').forEach((entry) => {
                    entry.addEventListener('click', async () => {
                        await selectStockAndActivate(
                            entry.dataset.code,
                            entry.dataset.name || entry.dataset.code,
                            searchInput
                        );
                        dropdown.style.display = 'none';
                    });
                });

                dropdown.style.display = 'block';
            } catch (error) {
                console.log('search error:', error);
            }
        }, 300);
    });

    searchInput.addEventListener('keydown', async (event) => {
        if (event.key !== 'Enter') {
            return;
        }
        event.preventDefault();

        const keyword = event.target.value.trim();
        if (!keyword) {
            return;
        }

        const selection = await resolveSearchSelection(keyword);
        await selectStockAndActivate(selection.code, selection.name, searchInput);
        dropdown.style.display = 'none';
    });

    document.addEventListener('click', (event) => {
        if (!event.target.closest('.searchWrapper')) {
            dropdown.style.display = 'none';
        }
    });
}

async function resolveSearchSelection(keyword) {
    const parsed = parseSearchKeyword(keyword);
    if (parsed?.resolved) {
        return parsed;
    }

    try {
        const lookupKeyword = parsed?.code || keyword;
        const items = await fetchJson(`/api/stock/search?keyword=${encodeURIComponent(lookupKeyword)}`);
        if (!Array.isArray(items) || items.length === 0) {
            return {
                code: parsed?.code || keyword,
                name: parsed?.name || parsed?.code || keyword
            };
        }

        const normalizedKeyword = String(lookupKeyword || '').trim().toLowerCase();
        const exact = items.find((item) => {
            const code = String(item?.code || '').trim().toLowerCase();
            const name = String(item?.name || '').trim().toLowerCase();
            return code === normalizedKeyword || name === normalizedKeyword;
        });
        const candidate = exact || items[0];
        return {
            code: candidate?.code || keyword,
            name: candidate?.name || candidate?.code || keyword
        };
    } catch (error) {
        console.log('search resolve error:', error);
        return { code: keyword, name: keyword };
    }
}

async function ensureResolvedCurrentCode() {
    const rawCode = String(currentCode || '').trim();
    if (/^\d{6}$/.test(rawCode)) {
        return;
    }

    const keyword = rawCode || String(currentStockName || '').trim();
    if (!keyword) {
        return;
    }

    const selection = await resolveSearchSelection(keyword);
    applySearchSelection(
        selection.code,
        selection.name,
        document.getElementById('stockSearch')
    );
}

function parseSearchKeyword(keyword) {
    const value = String(keyword || '').trim();
    if (!value) {
        return null;
    }

    const labeledMatch = value.match(/^(.*?)\s*\((\d{6})\)$/);
    if (labeledMatch) {
        return {
            code: labeledMatch[2],
            name: labeledMatch[1].trim() || labeledMatch[2],
            resolved: true
        };
    }

    const codeMatch = value.match(/^(\d{6})$/);
    if (codeMatch) {
        return {
            code: codeMatch[1],
            name: codeMatch[1],
            resolved: false
        };
    }

    return null;
}

function applySearchSelection(code, name, searchInput) {
    currentCode = String(code || '').trim();
    currentStockName = String(name || code || '').trim();
    if (searchInput) {
        searchInput.value = currentStockName && currentCode
            ? `${currentStockName} (${currentCode})`
            : (currentStockName || currentCode);
    }
}

// 검색, 전광판 클릭 등 종목 전환 진입점을 하나로 묶어 차트와 AI 브리핑이 함께 갱신되게 한다.
async function selectStockAndActivate(code, name, searchInput = document.getElementById('stockSearch')) {
    applySearchSelection(code, name, searchInput);
    await activateStockSource();
}

function bindTickerCards() {
    document.querySelectorAll('.clickableTicker').forEach((card) => {
        card.addEventListener('click', async () => {
            const source = card.dataset.chartSource;
            if (!source) {
                return;
            }
            await activateChartSource(source);
        });
    });
}

function bindTopStockTicker() {
    const handleClick = async (event) => {
        const item = event.target.closest('.topStockItem');
        if (!item) {
            return;
        }

        event.preventDefault();
        const fallbackName = item.dataset.name || '';
        let stockCode = item.dataset.code || '';
        let stockName = fallbackName;

        if (!/^\d{6}$/.test(stockCode)) {
            const selection = await resolveSearchSelection(fallbackName);
            stockCode = selection.code;
            stockName = selection.name;
        }

        if (!stockCode) {
            return;
        }

        await selectStockAndActivate(stockCode, stockName || stockCode);
    };

    document.getElementById('tickerContent1')?.addEventListener('click', handleClick);
    document.getElementById('tickerContent2')?.addEventListener('click', handleClick);
}

function bindExchangeSelector() {
    const currencySelect = document.getElementById('currencySelect');
    if (!currencySelect) {
        return;
    }

    currencySelect.addEventListener('click', (event) => {
        event.stopPropagation();
    });

    currencySelect.addEventListener('change', handleExchangeCurrencyChange);
}

async function handleExchangeCurrencyChange(event) {
    event.stopPropagation();

    if (currentChartSource !== 'exchange') {
        await updateExchangeCard();
        return;
    }

    const currency = getSelectedCurrency();
    const { quote, chartData } = await loadExchangeSelectionData(currency);
    latestChartData = chartData;
    await updateMainChart();
    renderExchangeSummary(quote, chartData);
}

// 환율 화면에서는 시세를 먼저 갱신하고, 차트는 같은 통화 캐시를 재사용해서 뒤이어 반영한다.
async function loadExchangeSelectionData(currency) {
    const quotePromise = getExchangeQuote(currency);
    const chartPromise = getExchangeChart(currency);
    const quote = await quotePromise;
    await updateExchangeCard(quote, null, currency);
    const chartData = await chartPromise;
    return { quote, chartData };
}

function bindSectorTrendCards() {
    const setCollapsedState = (box, collapsed) => {
        box.classList.toggle('is-collapsed', collapsed);
        const toggle = box.querySelector('.scTrendToggle');
        if (!toggle) {
            return;
        }

        toggle.setAttribute('aria-expanded', String(!collapsed));
        toggle.setAttribute('aria-label', collapsed ? '상승 점수 펼치기' : '상승 점수 접기');
    };

    const getRowTrendBoxes = (sourceBox) => {
        const card = sourceBox.closest('.sectorCard');
        if (!card || !card.parentElement) {
            return [sourceBox];
        }

        const rowTop = card.offsetTop;
        return Array.from(card.parentElement.querySelectorAll('.sectorCard'))
            .filter((item) => Math.abs(item.offsetTop - rowTop) <= 4)
            .map((item) => item.querySelector('.scTrendBox'))
            .filter(Boolean);
    };

    document.querySelectorAll('.scTrendBox').forEach((box) => {
        const toggle = box.querySelector('.scTrendToggle');
        if (!toggle) {
            return;
        }

        setCollapsedState(box, box.classList.contains('is-collapsed'));

        toggle.addEventListener('click', (event) => {
            event.preventDefault();
            event.stopPropagation();
            const nextCollapsed = !box.classList.contains('is-collapsed');
            getRowTrendBoxes(box).forEach((rowBox) => setCollapsedState(rowBox, nextCollapsed));
        });
    });
}

async function startPolling() {
    if (pollingStarted) {
        return;
    }
    pollingStarted = true;

    await Promise.all([
        updateTicker(),
        updateExchangeCard(),
        updateTopStocks()
    ]);
    await refreshActiveView();

    setInterval(async () => {
        const tickerData = await updateTicker();

        if (currentChartSource === 'stock') {
            await updateStockInfo();
            return;
        }

        if (currentChartSource === 'kospi' && tickerData.kospi) {
            renderIndexSummary('kospi', tickerData.kospi);
        }

        if (currentChartSource === 'kosdaq' && tickerData.kosdaq) {
            renderIndexSummary('kosdaq', tickerData.kosdaq);
        }
    }, 5000);

    setInterval(async () => {
        await updateTopStocks();
    }, 30000);

    setInterval(async () => {
        if (currentChartSource === 'exchange') {
            return;
        }

        if (isMarketOpen()) {
            await updateMainChart();
        }
    }, 10000);

    setInterval(async () => {
        const exchangeData = await updateExchangeCard();
        if (currentChartSource === 'exchange') {
            renderExchangeSummary(exchangeData);
        }
    }, 60000);
}

async function activateStockSource() {
    await ensureResolvedCurrentCode();
    currentChartSource = 'stock';
    showPendingStockAiCard(currentCode, currentStockName || currentCode);
    await refreshActiveView();
    await refreshAiPredictionForCurrentCode();
}

async function activateChartSource(source) {
    currentChartSource = source;
    if (source === 'exchange') {
        currentTab = 'daily';
    }
    await refreshActiveView();
}

async function refreshActiveView() {
    syncChartTabs();
    applyActiveTickerState();

    if (mainChart) {
        await updateMainChart();
    } else {
        await initMainChart();
    }

    await refreshActiveSummary();
}

function syncChartTabs() {
    if (currentChartSource === 'exchange') {
        currentTab = 'daily';
    }

    if ((currentTab === 'time' || currentTab === 'minute') && !isMarketOpen()) {
        currentTab = 'daily';
    }

    document.querySelectorAll('.chartTab').forEach((button) => {
        const isExchangeDisabled = currentChartSource === 'exchange' && button.dataset.tab !== 'daily';
        button.disabled = isExchangeDisabled;
        button.classList.toggle('active', button.dataset.tab === currentTab);
    });
}

function applyActiveTickerState() {
    document.querySelectorAll('.clickableTicker').forEach((card) => {
        const isActive = currentChartSource !== 'stock' && card.dataset.chartSource === currentChartSource;
        card.classList.toggle('activeTicker', isActive);
    });
}

async function fetchMainChartData() {
    syncChartTabs();

    if (currentChartSource === 'exchange') {
        try {
            const data = await getExchangeChart(getSelectedCurrency());
            latestChartData = data;
            return data;
        } catch (error) {
            console.log('exchange chart fetch error:', error);
            latestChartData = emptyChartData();
            return latestChartData;
        }
    }

    const endpoint = getChartEndpoint(currentChartSource, currentTab);
    if (!endpoint) {
        latestChartData = emptyChartData();
        return latestChartData;
    }

    try {
        const data = normalizeChartData(await fetchJson(endpoint));
        if (data.labels.length === 0 && currentTab !== 'daily' && currentChartSource !== 'exchange') {
            currentTab = 'daily';
            syncChartTabs();
            return await fetchMainChartData();
        }

        latestChartData = data;
        return data;
    } catch (error) {
        console.log('chart fetch error:', error);
        latestChartData = emptyChartData();
        return latestChartData;
    }
}

function getChartEndpoint(source, tab) {
    const currency = encodeURIComponent(getSelectedCurrency());

    const endpoints = {
        stock: {
            daily: `/api/stock/${currentCode}/chart`,
            time: `/api/stock/${currentCode}/time`,
            minute: `/api/stock/${currentCode}/minute`
        },
        kospi: {
            daily: '/api/kospi/chart',
            time: '/api/kospi/time',
            minute: '/api/kospi/minute'
        },
        kosdaq: {
            daily: '/api/kosdaq/chart',
            time: '/api/kosdaq/time',
            minute: '/api/kosdaq/minute'
        },
        exchange: {
            daily: `/api/exchange/chart?currency=${currency}`
        }
    };

    return endpoints[source]?.[tab] || null;
}

async function initMainChart() {
    const data = await fetchMainChartData();

    if (mainChart) {
        mainChart.destroy();
    }

    mainChart = new Chart(document.getElementById('mainChart'), {
        type: 'line',
        data: {
            labels: data.labels,
            datasets: [{
                label: getDatasetLabel(),
                data: data.closePrices,
                borderColor: '#0E0F37',
                backgroundColor: 'rgba(14, 15, 55, 0.1)',
                tension: 0.35,
                fill: true,
                pointRadius: 0,
                pointHoverRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false
            },
            plugins: {
                legend: {
                    display: false
                },
                title: {
                    display: true,
                    text: getChartTitle()
                }
            },
            scales: {
                x: {
                    grid: {
                        display: false
                    }
                },
                y: {
                    grid: {
                        color: '#f3f4f6'
                    },
                    ticks: {
                        callback: (value) => Number(value).toLocaleString()
                    }
                }
            }
        }
    });
}

async function updateMainChart() {
    if (!mainChart) {
        await initMainChart();
        return;
    }

    const data = await fetchMainChartData();
    mainChart.data.labels = data.labels;
    mainChart.data.datasets[0].data = data.closePrices;
    mainChart.data.datasets[0].label = getDatasetLabel();
    mainChart.options.plugins.title.text = getChartTitle();
    mainChart.update();
}

function getDatasetLabel() {
    if (currentChartSource === 'exchange') {
        return '환율';
    }
    if (currentChartSource === 'kospi' || currentChartSource === 'kosdaq') {
        return '지수';
    }
    return currentTab === 'daily' ? '종가' : '가격';
}

function getChartTitle() {
    const tabLabel = currentTab === 'daily'
        ? '일봉'
        : currentTab === 'time'
            ? '시간 차트'
            : '분 차트';

    return `${getCurrentChartLabel()} - ${tabLabel}`;
}

function getCurrentChartLabel() {
    if (currentChartSource === 'kospi') {
        return 'KOSPI';
    }
    if (currentChartSource === 'kosdaq') {
        return 'KOSDAQ';
    }
    if (currentChartSource === 'exchange') {
        return getSelectedCurrencyLabel();
    }
    return currentStockName || currentCode;
}

async function refreshActiveSummary() {
    if (currentChartSource === 'kospi') {
        await updateIndexInfo('kospi');
        return;
    }

    if (currentChartSource === 'kosdaq') {
        await updateIndexInfo('kosdaq');
        return;
    }

    if (currentChartSource === 'exchange') {
        try {
            const exchangeData = await getExchangeQuote(getSelectedCurrency());
            await updateExchangeInfo(exchangeData, latestChartData);
        } catch (error) {
            console.log('exchange summary refresh error:', error);
            await updateExchangeInfo(null, latestChartData);
        }
        return;
    }

    await updateStockInfo();
}

async function updateStockInfo() {
    try {
        const data = await fetchJson(`/api/stock/${currentCode}`);
        currentStockName = currentStockName || data.stockName || currentCode;

        const currentPrice = parseNumber(data.currentPrice);
        const openPrice = parseNumber(data.openPrice);
        const highPrice = parseNumber(data.highPrice);
        const lowPrice = parseNumber(data.lowPrice);
        const volume = parseNumber(data.volume);
        const priceChange = safeNumber(currentPrice) - safeNumber(openPrice);
        const changeRate = openPrice ? (priceChange / openPrice) * 100 : 0;
        const changeDisplay = buildChangeDisplay(priceChange, changeRate, 0, '');

        renderChartSummary({
            name: currentStockName || currentCode,
            code: currentCode,
            currentPrice: `${formatNumber(currentPrice, 0)} KRW`,
            priceChangeText: changeDisplay.text,
            priceChangeClass: changeDisplay.className,
            openLabel: '시가',
            openValue: formatNumber(openPrice, 0),
            openClass: '',
            highLabel: '고가',
            highValue: formatNumber(highPrice, 0),
            highClass: 'up',
            lowLabel: '저가',
            lowValue: formatNumber(lowPrice, 0),
            lowClass: 'down',
            volumeLabel: '거래량',
            volumeValue: formatNumber(volume, 0),
            volumeClass: ''
        });
    } catch (error) {
        console.log('stock info error:', error);
    }
}

async function updateIndexInfo(source, existingData = null) {
    try {
        const data = existingData || await fetchJson(source === 'kospi' ? '/api/kospi' : '/api/kosdaq');
        renderIndexSummary(source, data);
    } catch (error) {
        console.log('index info error:', error);
    }
}

function renderIndexSummary(source, data) {
    const currentPrice = parseNumber(data.currentPrice);
    const openPrice = parseNumber(data.openPrice);
    const highPrice = parseNumber(data.highPrice);
    const lowPrice = parseNumber(data.lowPrice);
    const volume = parseNumber(data.volume);
    const changeDisplay = buildChangeDisplay(parseNumber(data.priceChange), parseNumber(data.changeRate), 2, '');

    renderChartSummary({
        name: source === 'kospi' ? 'KOSPI' : 'KOSDAQ',
        code: source === 'kospi' ? '0001' : '1001',
        currentPrice: `${formatNumber(currentPrice, 2)} pt`,
        priceChangeText: changeDisplay.text,
        priceChangeClass: changeDisplay.className,
        openLabel: '시가',
        openValue: formatNumber(openPrice, 2),
        openClass: '',
        highLabel: '고가',
        highValue: formatNumber(highPrice, 2),
        highClass: 'up',
        lowLabel: '저가',
        lowValue: formatNumber(lowPrice, 2),
        lowClass: 'down',
        volumeLabel: '거래량',
        volumeValue: formatNumber(volume, 0),
        volumeClass: ''
    });
}

async function updateExchangeInfo(existingData = null, existingChartData = null) {
    try {
        const currency = getSelectedCurrency();
        const data = existingData || await getExchangeQuote(currency);
        const chartData = existingChartData || (currentChartSource === 'exchange' ? latestChartData : null);
        renderExchangeSummary(data, chartData);
    } catch (error) {
        console.log('exchange info error:', error);
    }
}

function renderExchangeSummary(data, chartData = latestChartData) {
    const normalizedChartData = normalizeChartData(chartData);
    const metrics = resolveChangeMetrics(data, normalizedChartData);
    const recentHigh = getMaxValue(normalizedChartData.closePrices);
    const recentLow = getMinValue(normalizedChartData.closePrices);
    const latestLabel = normalizedChartData.labels.length > 0
        ? normalizedChartData.labels[normalizedChartData.labels.length - 1]
        : '-';
    const previousPrice = Number.isNaN(metrics.current) || Number.isNaN(metrics.change)
        ? NaN
        : metrics.current - metrics.change;
    const changeDisplay = buildChangeDisplay(metrics.change, metrics.rate, 2, '원');

    renderChartSummary({
        name: getSelectedCurrencyLabel(),
        code: normalizeCurrency(getSelectedCurrency()),
        currentPrice: `${formatNumber(metrics.current, 2)} 원`,
        priceChangeText: changeDisplay.text,
        priceChangeClass: changeDisplay.className,
        openLabel: '전일',
        openValue: formatNumber(previousPrice, 2),
        openClass: '',
        highLabel: '최근 고가',
        highValue: formatNumber(recentHigh, 2),
        highClass: '',
        lowLabel: '최근 저가',
        lowValue: formatNumber(recentLow, 2),
        lowClass: '',
        volumeLabel: '기준일',
        volumeValue: latestLabel,
        volumeClass: ''
    });
}

function renderChartSummary(summary) {
    setText('chartStockName', summary.name);
    setText('chartStockCode', summary.code);
    setText('chartCurrentPrice', summary.currentPrice);

    const changeEl = document.getElementById('chartPriceChange');
    if (changeEl) {
        changeEl.textContent = summary.priceChangeText;
        changeEl.className = summary.priceChangeClass || '';
    }

    setMetaField('chartOpenLabel', 'chartOpenPrice', summary.openLabel, summary.openValue, summary.openClass);
    setMetaField('chartHighLabel', 'chartHighPrice', summary.highLabel, summary.highValue, summary.highClass);
    setMetaField('chartLowLabel', 'chartLowPrice', summary.lowLabel, summary.lowValue, summary.lowClass);
    setMetaField('chartVolumeLabel', 'chartVolume', summary.volumeLabel, summary.volumeValue, summary.volumeClass);
}

function setMetaField(labelId, valueId, labelText, valueText, valueClass = '') {
    setText(labelId, labelText);
    const valueEl = document.getElementById(valueId);
    if (!valueEl) {
        return;
    }

    valueEl.textContent = valueText;
    valueEl.className = valueClass || '';
}

async function updateTicker() {
    try {
        const [kospi, kosdaq] = await Promise.all([
            fetchJson('/api/kospi'),
            fetchJson('/api/kosdaq')
        ]);

        renderIndexTicker('kospiPrice', 'kospiRate', kospi);
        renderIndexTicker('kosdaqPrice', 'kosdaqRate', kosdaq);
        return { kospi, kosdaq };
    } catch (error) {
        console.log('ticker error:', error);
        return {};
    }
}

function renderIndexTicker(priceId, rateId, data) {
    const priceEl = document.getElementById(priceId);
    const rateEl = document.getElementById(rateId);
    const rateInfo = buildRateOnly(parseNumber(data.changeRate));

    if (priceEl) {
        priceEl.textContent = formatNumber(parseNumber(data.currentPrice), 2);
    }

    if (rateEl) {
        rateEl.textContent = rateInfo.text;
        rateEl.className = rateInfo.className;
    }
}

async function updateExchangeCard(existingData = null, existingChartData = null, currency = getSelectedCurrency()) {
    try {
        const data = existingData || await getExchangeQuote(currency);
        const metrics = await resolveExchangeMetrics(data, existingChartData, currency);
        const rateInfo = buildRateOnly(metrics.rate, metrics.direction);

        setText('exchangePrice', formatNumber(metrics.current, 2));

        const rateEl = document.getElementById('exchangeRate');
        if (rateEl) {
            rateEl.textContent = rateInfo.text;
            rateEl.className = rateInfo.className;
        }

        return data;
    } catch (error) {
        console.log('exchange ticker error:', error);
        return null;
    }
}

async function updateTopStocks() {
    try {
        const stocks = await fetchJson('/api/stock/top-fluctuation');
        if (!Array.isArray(stocks) || stocks.length === 0) {
            return;
        }

        const tickerHtml = stocks.map((stock) => {
            const rate = parseNumber(stock.changeRate);
            const rateInfo = buildRateOnly(rate);
            const currentPrice = formatNumber(parseNumber(stock.currentPrice), 0);
            const stockCode = String(stock.stockCode || '').trim();
            const stockName = String(stock.stockName || '').trim();

            return `
                <a href="#" class="t-item topStockItem" data-code="${stockCode}" data-name="${stockName}">
                    <span>${stockName}</span>
                    <strong class="${rateInfo.className}">${currentPrice} ${rateInfo.text}</strong>
                </a>
            `;
        }).join('');

        setHtml('tickerContent1', tickerHtml);
        setHtml('tickerContent2', tickerHtml);
    } catch (error) {
        console.log('top fluctuation error:', error);
    }
}

function initAiRotator() {
    rebuildAiRotatorItems(true);
    startAiRotator();
}

function stopAiRotator() {
    if (aiRotatorTimer) {
        clearInterval(aiRotatorTimer);
        aiRotatorTimer = null;
    }
}

// 종목을 새로 검색했을 때는 기존 섹터 로테이션을 잠깐 멈추고, 해당 종목 확률 카드를 먼저 보여준다.
function showPendingStockAiCard(stockCode, stockName) {
    if (!stockCode) {
        return;
    }

    const seed = window.AI_ROTATOR_DATA || {};
    seed.stock = createPendingStockAiSeed(stockCode, stockName || stockCode);
    window.AI_ROTATOR_DATA = seed;
    stopAiRotator();
    rebuildAiRotatorItems(true);
}

function rebuildAiRotatorItems(resetIndex = false) {
    const seed = window.AI_ROTATOR_DATA || {};
    const sectorItems = Array.isArray(seed.sectors)
        ? seed.sectors.map(buildSectorAiRotatorItem).filter(Boolean)
        : [];
    const items = [];
    const stockItem = buildStockAiRotatorItem(seed.stock || {}, sectorItems.length === 0);
    if (stockItem) {
        items.push(stockItem);
    }
    items.push(...sectorItems);
    aiRotatorItems = items;

    if (resetIndex || aiRotatorIndex >= aiRotatorItems.length) {
        aiRotatorIndex = 0;
    }

    renderAiRotatorItem(aiRotatorItems[aiRotatorIndex] || null);
}

function startAiRotator() {
    stopAiRotator();

    if (aiRotatorItems.length <= 1) {
        return;
    }

    aiRotatorTimer = setInterval(() => {
        if (aiRotatorItems.length <= 1) {
            return;
        }

        aiRotatorIndex = (aiRotatorIndex + 1) % aiRotatorItems.length;
        renderAiRotatorItem(aiRotatorItems[aiRotatorIndex]);
    }, 10000);
}

async function refreshAiPredictionForCurrentCode() {
    if (!currentCode) {
        return;
    }

    const requestedCode = currentCode;
    const requestedName = currentStockName || requestedCode;
    const refreshToken = ++aiRefreshToken;
    showPendingStockAiCard(requestedCode, requestedName);
    const seed = window.AI_ROTATOR_DATA || {};

    try {
        const stockAi = await fetchJson(`/api/stock/${encodeURIComponent(requestedCode)}/ai?ts=${Date.now()}`);
        if (refreshToken !== aiRefreshToken || requestedCode !== currentCode) {
            return;
        }

        seed.stock = normalizeAiPredictionResponse(stockAi, requestedCode, requestedName);
        window.AI_ROTATOR_DATA = seed;
        rebuildAiRotatorItems(true);
        startAiRotator();
    } catch (error) {
        console.log('ai prediction refresh error:', error);
        if (refreshToken !== aiRefreshToken || requestedCode !== currentCode) {
            return;
        }
        seed.stock = {
            ...createPendingStockAiSeed(requestedCode, requestedName),
            message: 'AI 예측을 다시 불러오지 못했습니다.',
            loading: false
        };
        window.AI_ROTATOR_DATA = seed;
        rebuildAiRotatorItems(true);
        startAiRotator();
    }
}

function buildStockAiRotatorItem(stock, allowFallbackCard = false) {
    const stockCode = stock?.stockCode || currentCode;
    const stockName = stock?.stockName || currentStockName || stockCode;
    if (!stockCode && !stockName) {
        return null;
    }

    const sentimentMean = safeNumber(parseNumber(stock?.sentimentMean));
    const clickbaitMean = safeNumber(parseNumber(stock?.clickbaitMean));
    const volatility = safeNumber(parseNumber(stock?.volatility20d)) * 100;
    const articleCount = safeNumber(parseNumber(stock?.articleCount));
    const cvAuc = safeNumber(parseNumber(stock?.cvAuc));
    const nTrain = safeNumber(parseNumber(stock?.nTrain));
    const factRatio = safeNumber(parseNumber(stock?.factRatio)) * 100;

    if (stock?.loading) {
        return {
            title: 'AI 시장 흐름 브리핑',
            badge: '현재 종목',
            badgeClass: 'badge-neutral',
            subject: `${stockName} · ${stockCode}`,
            arrowClass: 'arrow-neutral',
            arrowText: '•',
            headline: '예측 데이터 불러오는 중',
            subline: '최근 뉴스와 가격 흐름을 다시 계산하고 있습니다.',
            factors: [
                createAiFactor('최근 뉴스', 0, 'fill-neutral', '계산중', 'neutral'),
                createAiFactor('모델 상태', 0, 'fill-neutral', '로딩중', 'neutral'),
                createAiFactor('기사 수', 0, 'fill-neutral', '-', 'neutral')
            ],
            meta: '잠시 후 최신 종목 예측으로 갱신됩니다.'
        };
    }

    if (!stock?.valid) {
        if (!allowFallbackCard) {
            return null;
        }

        return {
            title: 'AI 시장 흐름 브리핑',
            badge: '현재 종목',
            badgeClass: 'badge-neutral',
            subject: `${stockName} · ${stockCode}`,
            arrowClass: 'arrow-neutral',
            arrowText: '•',
            headline: '예측 데이터 부족',
            subline: stock?.message || '최근 뉴스가 부족해 참고 카드만 표시합니다.',
            factors: [
                createAiFactor('최근 뉴스', 0, 'fill-neutral', '대기', 'neutral'),
                createAiFactor('모델 상태', 0, 'fill-neutral', '준비중', 'neutral'),
                createAiFactor('데이터', 0, 'fill-neutral', '-', 'neutral')
            ],
            meta: '참고용 · 종목 뉴스가 쌓이면 자동으로 예측이 반영됩니다.'
        };
    }

    return {
        title: 'AI 시장 흐름 브리핑',
        badge: '현재 종목',
        badgeClass: 'badge-info',
        subject: `${stockName} · ${stockCode}`,
        arrowClass: stock?.up ? 'arrow-up' : 'arrow-down',
        arrowText: stock?.up ? '↑' : '↓',
        headline: `${stock?.prediction || '예측'} 예측`,
        subline: `익일 상승 확률 ${safeNumber(parseNumber(stock?.probabilityPercent)).toFixed(1)}% · 확신도 ${stock?.confidence || '참고용'}`,
        factors: [
            createAiFactor(
                `낚시성 ${describeClickbait(clickbaitMean)}`,
                clamp(100 - clickbaitMean, 0, 100),
                clickbaitMean <= 35 ? 'fill-pos' : 'fill-neg',
                clickbaitMean <= 35 ? '+강세' : '-주의',
                clickbaitMean <= 35 ? 'positive' : 'negative'
            ),
            createAiFactor(
                '변동성',
                clamp(volatility * 30, 0, 100),
                volatility <= 2.5 ? 'fill-pos' : 'fill-neg',
                volatility <= 2.5 ? '+안정' : '-주의',
                volatility <= 2.5 ? 'positive' : 'negative'
            ),
            createAiFactor(
                `기사 수 (${Math.round(articleCount)}건)`,
                clamp(articleCount / 3, 0, 100),
                articleCount >= 30 ? 'fill-pos' : 'fill-neutral',
                articleCount >= 30 ? '+강세' : '중립',
                articleCount >= 30 ? 'positive' : 'neutral'
            )
        ],
        meta: `감성 ${formatSignedDecimal(sentimentMean, 2)} · 사실형 ${formatNumber(factRatio, 0)}% · CV AUC ${formatNumber(cvAuc, 3)} · 데이터 ${formatNumber(nTrain, 0)}건 기반`
    };
}

function createPendingStockAiSeed(code, name) {
    return {
        valid: false,
        stockCode: code,
        stockName: name,
        prediction: '',
        up: false,
        probabilityPercent: 0,
        confidence: '',
        articleCount: 0,
        sentimentMean: 0,
        clickbaitMean: 0,
        typeProbMean: 0,
        factRatio: 0,
        volatility20d: 0,
        cvAuc: 0,
        nTrain: 0,
        message: '',
        loading: true
    };
}

function normalizeAiPredictionResponse(stockAi, fallbackCode, fallbackName) {
    const recentArticles = Array.isArray(stockAi?.recentArticles)
        ? stockAi.recentArticles
        : (Array.isArray(stockAi?.recent_articles) ? stockAi.recent_articles : []);
    const probability = firstDefined(stockAi?.probabilityPercent, stockAi?.probability_percent);
    const rawProbability = firstDefined(probability, stockAi?.probability);
    const prediction = stockAi?.prediction || '';
    const predictionInt = firstDefined(stockAi?.predictionInt, stockAi?.prediction_int);
    const probabilityPercent = probability != null
        ? safeNumber(parseNumber(probability))
        : safeNumber(parseNumber(rawProbability)) * (rawProbability != null && safeNumber(parseNumber(rawProbability)) <= 1 ? 100 : 1);
    const stockCode = stockAi?.stockCode || stockAi?.stock_code || fallbackCode;
    const stockName = stockAi?.stockName || stockAi?.stock_name || fallbackName || stockCode;

    return {
        valid: firstDefined(stockAi?.valid, !!prediction),
        stockCode,
        stockName,
        prediction,
        up: firstDefined(stockAi?.up, prediction === '상승' || predictionInt === 1, false),
        probabilityPercent,
        confidence: stockAi?.confidence || '',
        articleCount: firstDefined(stockAi?.articleCount, stockAi?.article_count, recentArticles.length, 0),
        sentimentMean: firstDefined(stockAi?.sentimentMean, stockAi?.sentiment_mean, 0),
        clickbaitMean: firstDefined(stockAi?.clickbaitMean, stockAi?.clickbait_mean, 0),
        typeProbMean: firstDefined(stockAi?.typeProbMean, stockAi?.type_prob_mean, 0),
        factRatio: firstDefined(stockAi?.factRatio, stockAi?.fact_ratio, 0),
        volatility20d: firstDefined(stockAi?.volatility20d, stockAi?.volatility_20d, 0),
        cvAuc: firstDefined(stockAi?.cvAuc, stockAi?.cv_auc, stockAi?.model_meta?.cv_auc, 0),
        nTrain: firstDefined(stockAi?.nTrain, stockAi?.ntrain, stockAi?.n_train, stockAi?.model_meta?.n_train, 0),
        message: stockAi?.message || stockAi?.error || ''
    };
}

function firstDefined(...values) {
    for (const value of values) {
        if (value !== undefined && value !== null) {
            return value;
        }
    }
    return undefined;
}

function buildSectorAiRotatorItem(card) {
    const articleCount = safeNumber(parseNumber(card?.articleCount));
    const trendScore = parseNumber(card?.trendScore);
    const posRatio = parseNumber(card?.posRatio);
    const avgTypeProb = parseNumber(card?.avgTypeProb);
    const avgClickbaitProb = parseNumber(card?.avgClickbaitProb);
    const positiveCount = safeNumber(parseNumber(card?.positiveCount));
    const negativeCount = safeNumber(parseNumber(card?.negativeCount));
    const neutralCount = safeNumber(parseNumber(card?.neutralCount));
    const sectorName = `${card?.sectorName || card?.sectorKey || ''}`.trim();
    const trendLabel = `${card?.trendLabel || ''}`.trim();

    if (articleCount <= 0 || !sectorName || !trendLabel) {
        return null;
    }

    if ([trendScore, posRatio, avgTypeProb, avgClickbaitProb].some((value) => Number.isNaN(value))) {
        return null;
    }

    if ([sectorName, trendLabel].some((value) => /예측\s*데이터\s*부족|데이터\s*부족|불러오지\s*못/i.test(value))) {
        return null;
    }

    const direction = card?.trendDirection || 'neutral';

    return {
        title: 'AI 시장 흐름 브리핑',
        badge: '섹터 흐름',
        badgeClass: 'badge-subtle',
        subject: `${sectorName} · 뉴스 ${Math.round(articleCount)}건`,
        arrowClass: direction === 'up' ? 'arrow-up' : direction === 'down' ? 'arrow-down' : 'arrow-neutral',
        arrowText: direction === 'up' ? '↑' : direction === 'down' ? '↓' : '•',
        headline: trendLabel,
        subline: `상승 점수 ${formatNumber(safeNumber(trendScore), 0)}점 · 신뢰도 ${formatNumber(safeNumber(avgTypeProb), 0)}%`,
        factors: [
            createAiFactor(
                '호재 비중',
                clamp(safeNumber(posRatio), 0, 100),
                safeNumber(posRatio) >= 50 ? 'fill-pos' : 'fill-neg',
                `${formatNumber(safeNumber(posRatio), 0)}%`,
                safeNumber(posRatio) >= 50 ? 'positive' : 'negative'
            ),
            createAiFactor(
                'AI 신뢰도',
                clamp(safeNumber(avgTypeProb), 0, 100),
                safeNumber(avgTypeProb) >= 60 ? 'fill-pos' : 'fill-neutral',
                `${formatNumber(safeNumber(avgTypeProb), 0)}%`,
                safeNumber(avgTypeProb) >= 60 ? 'positive' : 'neutral'
            ),
            createAiFactor(
                '노이즈 방어',
                clamp(100 - safeNumber(avgClickbaitProb), 0, 100),
                safeNumber(avgClickbaitProb) <= 30 ? 'fill-pos' : 'fill-neg',
                safeNumber(avgClickbaitProb) <= 30 ? '+안정' : '-주의',
                safeNumber(avgClickbaitProb) <= 30 ? 'positive' : 'negative'
            )
        ],
        meta: `호재 ${formatNumber(positiveCount, 0)}건 · 악재 ${formatNumber(negativeCount, 0)}건 · 중립 ${formatNumber(neutralCount, 0)}건 · 노이즈 ${card?.avgClickbaitProb || '0.0'}%`
    };
}

function renderAiRotatorItem(item) {
    const panel = document.getElementById('aiRotatorPanel');
    if (!panel) {
        return;
    }

    if (!item) {
        setText('aiRotatorTitle', 'AI 시장 흐름 브리핑');
        setText('aiRotatorBadge', 'INFO');
        setElementClass('aiRotatorBadge', 'aiPredictBadge badge-neutral');
        setText('aiRotatorSubject', currentCode || '-');
        setElementClass('aiRotatorSubject', 'aiPredictSubject');
        setText('aiRotatorHeadline', '표시할 데이터 없음');
        setText('aiRotatorSubline', 'AI 예측 데이터가 준비되면 자동으로 반영됩니다.');
        setElementClass('aiRotatorArrow', 'aiPredictArrow arrow-neutral');
        setText('aiRotatorArrow', '•');
        setText('aiRotatorMeta', '참고용');
        setAiFactor(1, createAiFactor('뉴스 데이터', 0, 'fill-neutral', '-', 'neutral'));
        setAiFactor(2, createAiFactor('모델 상태', 0, 'fill-neutral', '-', 'neutral'));
        setAiFactor(3, createAiFactor('신뢰도', 0, 'fill-neutral', '-', 'neutral'));
        return;
    }

    setText('aiRotatorTitle', item.title);
    setText('aiRotatorBadge', item.badge);
    setElementClass('aiRotatorBadge', `aiPredictBadge ${item.badgeClass || 'badge-info'}`);
    setText('aiRotatorSubject', item.subject);
    setElementClass('aiRotatorSubject', item.subject ? 'aiPredictSubject' : 'aiPredictSubject is-hidden');
    setElementClass('aiRotatorArrow', `aiPredictArrow ${item.arrowClass || 'arrow-neutral'}`);
    setText('aiRotatorArrow', item.arrowText || '•');
    setText('aiRotatorHeadline', item.headline);
    setText('aiRotatorSubline', item.subline);
    setAiFactor(1, item.factors?.[0] || createAiFactor('요인 1', 0, 'fill-neutral', '-', 'neutral'));
    setAiFactor(2, item.factors?.[1] || createAiFactor('요인 2', 0, 'fill-neutral', '-', 'neutral'));
    setAiFactor(3, item.factors?.[2] || createAiFactor('요인 3', 0, 'fill-neutral', '-', 'neutral'));
    setText('aiRotatorMeta', item.meta || '참고용');
}

function setAiFactor(index, factor) {
    setText(`aiFactor${index}Label`, factor.label);
    setElementClass(`aiFactor${index}Fill`, `factorFill ${factor.fillClass || 'fill-neutral'}`);
    setStyleWidth(`aiFactor${index}Fill`, `${clamp(factor.width, 0, 100)}%`);

    const valueEl = document.getElementById(`aiFactor${index}Value`);
    if (!valueEl) {
        return;
    }

    valueEl.textContent = factor.value;
    valueEl.className = `factorVal ${factor.valueClass || 'neutral'}`;
}

function createAiFactor(label, width, fillClass, value, valueClass) {
    return { label, width, fillClass, value, valueClass };
}

function describeClickbait(clickbaitValue) {
    if (clickbaitValue <= 20) {
        return '낮음';
    }
    if (clickbaitValue <= 40) {
        return '보통';
    }
    return '주의';
}

function animateGauges() {
    const data = window.STOCK_DATA || { typeProb: 0, noiseProb: 0, sentimentScore: 50 };

    const confidenceBar = document.getElementById('confBar');
    const noiseBar = document.getElementById('noiseBar');
    const sentimentBar = document.getElementById('sentBar');

    if (confidenceBar) {
        const value = parseFloat(confidenceBar.getAttribute('data-value') || data.typeProb) || 0;
        setTimeout(() => {
            confidenceBar.style.width = `${Math.min(value, 100)}%`;
        }, 100);
    }

    if (noiseBar) {
        const value = parseFloat(noiseBar.getAttribute('data-value') || data.noiseProb) || 0;
        setTimeout(() => {
            noiseBar.style.width = `${Math.min(value, 100)}%`;
        }, 200);
    }

    if (sentimentBar) {
        const value = parseFloat(sentimentBar.getAttribute('data-value') || data.sentimentScore) || 50;
        if (value >= 60) {
            sentimentBar.style.background = 'linear-gradient(90deg,#34d399,#10b981)';
        } else if (value <= 40) {
            sentimentBar.style.background = 'linear-gradient(90deg,#f87171,#ef4444)';
        } else {
            sentimentBar.style.background = 'linear-gradient(90deg,#fbbf24,#f59e0b)';
        }

        setTimeout(() => {
            sentimentBar.style.width = `${Math.min(value, 100)}%`;
        }, 300);
    }
}

function updateAIAnalysis(data) {
    setStyleWidth('confBar', `${data.confidence}%`);
    setText('confVal', `${data.confidence}%`);
    setStyleWidth('noiseBar', `${data.noise}%`);
    setText('noiseVal', `${data.noise}%`);
    setStyleWidth('sentBar', `${data.sentiment}%`);
    setText('aiStatus', data.status);
    setText('aiDesc', data.desc);
}

function isMarketOpen() {
    const now = new Date();
    const day = now.getDay();
    if (day === 0 || day === 6) {
        return false;
    }

    const time = now.getHours() * 100 + now.getMinutes();
    return time >= 900 && time <= 1530;
}

function getSelectedCurrency() {
    return document.getElementById('currencySelect')?.value || 'USD';
}

function getSelectedCurrencyLabel() {
    const select = document.getElementById('currencySelect');
    if (!select) {
        return 'USD/KRW';
    }

    const option = select.options[select.selectedIndex];
    return option ? option.text : 'USD/KRW';
}

function normalizeCurrency(currency) {
    return String(currency || 'USD').toUpperCase().split('(')[0].trim();
}

async function resolveExchangeMetrics(data, fallbackChartData = null, currency = getSelectedCurrency()) {
    let metrics = resolveChangeMetrics(
        data,
        fallbackChartData || (currentChartSource === 'exchange' ? latestChartData : null)
    );
    if (hasResolvedMetrics(metrics)) {
        return metrics;
    }

    try {
        const chartData = await getExchangeChart(currency);
        metrics = resolveChangeMetrics(data, chartData);
    } catch (error) {
        console.log('exchange metrics fallback error:', error);
    }

    return metrics;
}

function resolveChangeMetrics(data, fallbackChartData = null) {
    const current = parseNumber(data?.currentPrice);
    let change = parseNumber(data?.priceChange);
    let rate = parseNumber(data?.changeRate);
    const fallbackMetrics = deriveMetricsFromChart(current, fallbackChartData);
    const resolvedCurrent = Number.isNaN(current) ? fallbackMetrics.current : current;

    if (Number.isNaN(change)) {
        change = fallbackMetrics.change;
    }
    if (Number.isNaN(rate)) {
        rate = fallbackMetrics.rate;
    }

    if (Number.isNaN(rate) && !Number.isNaN(change) && !Number.isNaN(resolvedCurrent)) {
        const previousPrice = resolvedCurrent - change;
        if (previousPrice !== 0) {
            rate = (change / previousPrice) * 100;
        }
    }

    if (Number.isNaN(change) && !Number.isNaN(rate) && !Number.isNaN(resolvedCurrent)) {
        const denominator = 1 + (rate / 100);
        if (denominator !== 0) {
            const previousPrice = resolvedCurrent / denominator;
            if (Number.isFinite(previousPrice)) {
                change = resolvedCurrent - previousPrice;
            }
        }
    }

    const direction = !Number.isNaN(change) && change !== 0
        ? change
        : (!Number.isNaN(rate) ? rate : 0);

    return { current: resolvedCurrent, change, rate, direction };
}

function deriveMetricsFromChart(current, fallbackChartData) {
    const priceSource = Array.isArray(fallbackChartData?.closePrices)
        ? fallbackChartData.closePrices
        : [];
    const prices = priceSource.map(parseNumber).filter((value) => !Number.isNaN(value));
    if (prices.length === 0) {
        return { current: Number.NaN, change: Number.NaN, rate: Number.NaN };
    }

    const resolvedCurrent = Number.isNaN(current) ? prices[prices.length - 1] : current;
    if (prices.length < 2) {
        return { current: resolvedCurrent, change: Number.NaN, rate: Number.NaN };
    }

    const previousPrice = prices[prices.length - 2];
    if (previousPrice === 0) {
        return { current: resolvedCurrent, change: Number.NaN, rate: Number.NaN };
    }

    const change = resolvedCurrent - previousPrice;
    const rate = (change / previousPrice) * 100;
    return { current: resolvedCurrent, change, rate };
}

function hasResolvedMetrics(metrics) {
    return !Number.isNaN(metrics?.change) || !Number.isNaN(metrics?.rate);
}

function buildChangeDisplay(change, rate, digits, amountSuffix) {
    if (Number.isNaN(change) && Number.isNaN(rate)) {
        return { text: '-', className: '' };
    }

    const direction = !Number.isNaN(change) && change !== 0
        ? change
        : (!Number.isNaN(rate) ? rate : 0);
    const prefix = direction > 0 ? '+' : direction < 0 ? '-' : '';
    const className = direction > 0 ? 'up' : direction < 0 ? 'down' : '';

    const amountValue = Number.isNaN(change) ? 0 : Math.abs(change);
    const rateValue = Number.isNaN(rate) ? 0 : Math.abs(rate);
    const amountText = `${prefix}${formatNumber(amountValue, digits)}${amountSuffix}`;

    return {
        text: `${amountText} (${prefix}${rateValue.toFixed(2)}%)`,
        className
    };
}

function buildRateOnly(rate, directionOverride = null) {
    if (Number.isNaN(rate)) {
        if (directionOverride == null || directionOverride === 0) {
            return { text: '-', className: '' };
        }
        return {
            text: '0.00%',
            className: directionOverride > 0 ? 'up' : 'down'
        };
    }

    const direction = directionOverride == null || directionOverride === 0 ? rate : directionOverride;
    const prefix = direction > 0 ? '+' : direction < 0 ? '-' : '';
    const className = direction > 0 ? 'up' : direction < 0 ? 'down' : '';

    return {
        text: `${prefix}${Math.abs(rate).toFixed(2)}%`,
        className
    };
}

function emptyChartData() {
    return {
        labels: [],
        closePrices: [],
        volumes: []
    };
}

function normalizeChartData(data) {
    return {
        labels: Array.isArray(data?.labels) ? data.labels : [],
        closePrices: Array.isArray(data?.closePrices) ? data.closePrices : [],
        volumes: Array.isArray(data?.volumes) ? data.volumes : []
    };
}

function getExchangeCacheKey(currency) {
    return normalizeCurrency(currency || getSelectedCurrency());
}

function readExchangeCache(cache, key, ttlMs) {
    const entry = cache.get(key);
    if (!entry) {
        return null;
    }

    if (Date.now() - entry.timestamp > ttlMs) {
        cache.delete(key);
        return null;
    }

    return entry.data;
}

function writeExchangeCache(cache, key, data) {
    cache.set(key, {
        data,
        timestamp: Date.now()
    });
    return data;
}

async function getExchangeQuote(currency = getSelectedCurrency(), forceRefresh = false) {
    const cacheKey = getExchangeCacheKey(currency);
    if (!forceRefresh) {
        const cached = readExchangeCache(exchangeQuoteCache, cacheKey, EXCHANGE_QUOTE_TTL_MS);
        if (cached) {
            return cached;
        }
    }

    const inFlight = exchangeQuoteRequests.get(cacheKey);
    if (inFlight) {
        return inFlight;
    }

    const request = fetchJson(`/api/exchange?currency=${encodeURIComponent(currency)}`)
        .then((data) => writeExchangeCache(exchangeQuoteCache, cacheKey, data))
        .finally(() => exchangeQuoteRequests.delete(cacheKey));

    exchangeQuoteRequests.set(cacheKey, request);
    return request;
}

async function getExchangeChart(currency = getSelectedCurrency(), forceRefresh = false) {
    const cacheKey = getExchangeCacheKey(currency);
    if (!forceRefresh) {
        const cached = readExchangeCache(exchangeChartCache, cacheKey, EXCHANGE_CHART_TTL_MS);
        if (cached) {
            return cached;
        }
    }

    const inFlight = exchangeChartRequests.get(cacheKey);
    if (inFlight) {
        return inFlight;
    }

    const request = fetchJson(`/api/exchange/chart?currency=${encodeURIComponent(currency)}`)
        .then((data) => writeExchangeCache(exchangeChartCache, cacheKey, normalizeChartData(data)))
        .finally(() => exchangeChartRequests.delete(cacheKey));

    exchangeChartRequests.set(cacheKey, request);
    return request;
}

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`Request failed: ${response.status}`);
    }
    return response.json();
}

function parseNumber(value) {
    if (value == null || value === '') {
        return Number.NaN;
    }

    const parsed = Number(String(value).replace(/,/g, '').trim());
    return Number.isFinite(parsed) ? parsed : Number.NaN;
}

function safeNumber(value) {
    return Number.isNaN(value) ? 0 : value;
}

function formatNumber(value, digits) {
    if (Number.isNaN(value)) {
        return '-';
    }

    return Number(value).toLocaleString(undefined, {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits
    });
}

function getMaxValue(values) {
    const numbers = values.map(parseNumber).filter((value) => !Number.isNaN(value));
    return numbers.length ? Math.max(...numbers) : Number.NaN;
}

function getMinValue(values) {
    const numbers = values.map(parseNumber).filter((value) => !Number.isNaN(value));
    return numbers.length ? Math.min(...numbers) : Number.NaN;
}

function setText(id, value) {
    const element = document.getElementById(id);
    if (element) {
        element.textContent = value;
    }
}

function setHtml(id, value) {
    const element = document.getElementById(id);
    if (element) {
        element.innerHTML = value;
    }
}

function setStyleWidth(id, value) {
    const element = document.getElementById(id);
    if (element) {
        element.style.width = value;
    }
}

function setElementClass(id, value) {
    const element = document.getElementById(id);
    if (element) {
        element.className = value;
    }
}

function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
}

function formatSignedDecimal(value, digits) {
    if (Number.isNaN(value)) {
        return '-';
    }

    const prefix = value > 0 ? '+' : '';
    return `${prefix}${Number(value).toFixed(digits)}`;
}
