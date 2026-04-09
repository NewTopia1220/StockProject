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

document.addEventListener('DOMContentLoaded', () => {
    currentStockName = document.getElementById('chartStockName')?.textContent.trim() || '삼성전자';
    currentCode = document.getElementById('chartStockCode')?.textContent.trim() || currentCode;

    animateGauges();
    bindChartTabs();
    bindSearchHandlers();
    bindTickerCards();
    bindExchangeSelector();
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
                        currentCode = entry.dataset.code;
                        currentStockName = entry.dataset.name || entry.dataset.code;
                        searchInput.value = `${currentStockName} (${currentCode})`;
                        dropdown.style.display = 'none';
                        await activateStockSource();
                    });
                });

                dropdown.style.display = 'block';
            } catch (error) {
                console.log('search error:', error);
            }
        }, 300);
    });

    searchInput.addEventListener('keypress', async (event) => {
        if (event.key !== 'Enter') {
            return;
        }

        const keyword = event.target.value.trim();
        if (!keyword) {
            return;
        }

        currentCode = keyword;
        currentStockName = keyword;
        dropdown.style.display = 'none';
        await activateStockSource();
    });

    document.addEventListener('click', (event) => {
        if (!event.target.closest('.searchWrapper')) {
            dropdown.style.display = 'none';
        }
    });
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

function bindExchangeSelector() {
    const currencySelect = document.getElementById('currencySelect');
    if (!currencySelect) {
        return;
    }

    currencySelect.addEventListener('click', (event) => {
        event.stopPropagation();
    });

    currencySelect.addEventListener('change', async (event) => {
        event.stopPropagation();
        const data = await updateExchangeCard();
        if (currentChartSource === 'exchange') {
            await updateMainChart();
            renderExchangeSummary(data);
        }
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
    currentChartSource = 'stock';
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
        await updateExchangeInfo();
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

async function updateExchangeInfo(existingData = null) {
    try {
        const data = existingData || await fetchJson(`/api/exchange?currency=${encodeURIComponent(getSelectedCurrency())}`);
        renderExchangeSummary(data);
    } catch (error) {
        console.log('exchange info error:', error);
    }
}

function renderExchangeSummary(data) {
    const metrics = resolveChangeMetrics(data);
    const recentHigh = getMaxValue(latestChartData.closePrices);
    const recentLow = getMinValue(latestChartData.closePrices);
    const latestLabel = latestChartData.labels.length > 0
        ? latestChartData.labels[latestChartData.labels.length - 1]
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

async function updateExchangeCard() {
    try {
        const data = await fetchJson(`/api/exchange?currency=${encodeURIComponent(getSelectedCurrency())}`);
        const metrics = resolveChangeMetrics(data);
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

            return `
                <div class="t-item">
                    <span>${stock.stockName}</span>
                    <strong class="${rateInfo.className}">${currentPrice} ${rateInfo.text}</strong>
                </div>
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
    if (aiRotatorTimer) {
        clearInterval(aiRotatorTimer);
    }

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

    try {
        const stockAi = await fetchJson(`/api/stock/${currentCode}/ai`);
        const seed = window.AI_ROTATOR_DATA || {};
        seed.stock = {
            valid: !!stockAi?.valid,
            stockCode: stockAi?.stockCode || currentCode,
            stockName: stockAi?.stockName || currentStockName || currentCode,
            prediction: stockAi?.prediction || '',
            up: !!stockAi?.up,
            probabilityPercent: stockAi?.probabilityPercent ?? 0,
            confidence: stockAi?.confidence || '',
            articleCount: stockAi?.articleCount ?? 0,
            sentimentMean: stockAi?.sentimentMean ?? 0,
            clickbaitMean: stockAi?.clickbaitMean ?? 0,
            typeProbMean: stockAi?.typeProbMean ?? 0,
            factRatio: stockAi?.factRatio ?? 0,
            volatility20d: stockAi?.volatility20d ?? 0,
            cvAuc: stockAi?.cvAuc ?? 0,
            nTrain: stockAi?.nTrain ?? 0,
            message: stockAi?.message || stockAi?.error || ''
        };
        window.AI_ROTATOR_DATA = seed;
        rebuildAiRotatorItems(true);
        startAiRotator();
    } catch (error) {
        console.log('ai prediction refresh error:', error);
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

    if (!stock?.valid) {
        if (!allowFallbackCard) {
            return null;
        }

        return {
            title: 'AI 주가 영향도 예측',
            badge: 'INFO',
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
        title: 'AI 주가 영향도 예측',
        badge: 'NEW',
        badgeClass: 'badge-info',
        subject: `${stockName} · ${stockCode}`,
        arrowClass: stock?.up ? 'arrow-up' : 'arrow-down',
        arrowText: stock?.up ? '↑' : '↓',
        headline: `${stock?.prediction || '예측'} 예측`,
        subline: `확률 ${safeNumber(parseNumber(stock?.probabilityPercent)).toFixed(1)}% · 확신도 ${stock?.confidence || '참고용'}`,
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

function buildSectorAiRotatorItem(card) {
    const articleCount = safeNumber(parseNumber(card?.articleCount));
    const trendScore = parseNumber(card?.trendScore);
    const posRatio = parseNumber(card?.posRatio);
    const avgTypeProb = parseNumber(card?.avgTypeProb);
    const avgClickbaitProb = parseNumber(card?.avgClickbaitProb);
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
        title: `${sectorName} · 뉴스 ${Math.round(articleCount)}건`,
        badge: '참고용',
        badgeClass: 'badge-subtle',
        subject: '',
        arrowClass: direction === 'up' ? 'arrow-up' : direction === 'down' ? 'arrow-down' : 'arrow-neutral',
        arrowText: direction === 'up' ? '↑' : direction === 'down' ? '↓' : '•',
        headline: trendLabel,
        subline: `상승 점수 ${formatNumber(safeNumber(trendScore), 0)}점`,
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
        meta: `${sectorName} · 신뢰 ${card?.avgTypeProb || '0.0'}% · 노이즈 ${card?.avgClickbaitProb || '0.0'}%`
    };
}

function renderAiRotatorItem(item) {
    const panel = document.getElementById('aiRotatorPanel');
    if (!panel) {
        return;
    }

    if (!item) {
        setText('aiRotatorTitle', 'AI 주가 영향도 예측');
        setText('aiRotatorBadge', 'INFO');
        setElementClass('aiRotatorBadge', 'aiPredictBadge badge-neutral');
        setText('aiRotatorSubject', currentCode || '-');
        setElementClass('aiRotatorSubject', 'aiPredictSubject');
        setText('aiRotatorHeadline', '표시할 데이터 없음');
        setText('aiRotatorSubline', '예측 또는 섹터 데이터가 준비되면 자동으로 반영됩니다.');
        setElementClass('aiRotatorArrow', 'aiPredictArrow arrow-neutral');
        setText('aiRotatorArrow', '•');
        setText('aiRotatorMeta', '참고용');
        setAiFactor(1, createAiFactor('뉴스 데이터', 0, 'fill-neutral', '-', 'neutral'));
        setAiFactor(2, createAiFactor('AI 예측', 0, 'fill-neutral', '-', 'neutral'));
        setAiFactor(3, createAiFactor('섹터 참고', 0, 'fill-neutral', '-', 'neutral'));
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

function resolveChangeMetrics(data) {
    const current = parseNumber(data?.currentPrice);
    const change = parseNumber(data?.priceChange);
    let rate = parseNumber(data?.changeRate);

    if (Number.isNaN(rate) && !Number.isNaN(change) && !Number.isNaN(current)) {
        const previousPrice = current - change;
        if (previousPrice !== 0) {
            rate = (change / previousPrice) * 100;
        }
    }

    const direction = !Number.isNaN(change) && change !== 0
        ? change
        : (!Number.isNaN(rate) ? rate : 0);

    return { current, change, rate, direction };
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
