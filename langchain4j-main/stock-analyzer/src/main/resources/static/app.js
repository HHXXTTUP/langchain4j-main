const state = {
    publicClean: false,
    currentOverview: null,
    chartMode: 'minute',
    insightFocus: 'review',
    watchlistItems: [],
    selectedMonitorCodes: new Set(),
    changeSortDirection: 'desc',
    monitorActive: false,
    monitorIntervalSeconds: 5,
    sectors: [],
    sectorResponse: null,
    sectorSortKey: 'score',
    sectorSortDirection: 'desc',
    currentSectorId: null,
    currentSectorSelectedCode: null,
    sectorDetailLoading: false,
    top10SelectedSectors: new Map(),
    sectorWatchlist: new Map(),
    sectorWatchlistLoaded: false,
    top10SectorDetails: new Map(),
    top10PickerOpen: false,
    top10RefreshInProgress: false,
    top10RefreshProgress: null,
    top10LastRound: null,
    sectorDayTracks: new Map(),
    sectorDayTrackLoading: false,
    top10AutoRefreshTimer: null,
    top10AutoRefreshSeconds: 30,
    top10AutoRefreshActive: false,
    top10LastRefreshAt: null,
    rankingHistoryExpanded: false,
    rankingHistoryControllers: [],
    rankingLogResponse: null,
    rankingLogSelectedSectorId: null,
    rankingTrajectorySelectedSectorId: null,
    rankingStatisticsSortKey: 'latestRank',
    rankingStatisticsSortDirection: 'asc',
    rankingLogAutoRefreshTimer: null,
    rankingLogAutoRefreshActive: false,
    rankingLogAutoRefreshSeconds: 10,
    fundamentals: null,
    fundamentalTab: 'performance'
};

const runtimeReady = initializeRuntimeMode();

document.querySelectorAll('.tab').forEach((tab) => {
    tab.addEventListener('click', () => switchTab(tab.dataset.tab));
});

document.querySelectorAll('[data-sector-sort]').forEach((button) => {
    button.addEventListener('click', () => changeSectorSort(button.dataset.sectorSort));
});

document.getElementById('search-form').addEventListener('submit', handleSearch);
document.getElementById('refresh-watchlist').addEventListener('click', loadWatchlist);
document.getElementById('refresh-sectors').addEventListener('click', () => loadSectors(true));
document.getElementById('refresh-sector-detail').addEventListener('click', () => refreshSelectedTop10Sectors());
document.getElementById('start-top10-auto-refresh').addEventListener('click', startTop10AutoRefresh);
document.getElementById('stop-top10-auto-refresh').addEventListener('click', () => stopTop10AutoRefresh());
document.getElementById('toggle-top10-sector-picker').addEventListener('click', toggleTop10SectorPicker);
document.getElementById('top10-sector-search').addEventListener('input', renderTop10SectorOptions);
document.getElementById('clear-top10-sectors').addEventListener('click', clearTop10SectorSelections);
document.getElementById('save-top10-sector-watchlist').addEventListener('click', saveSelectedSectorWatchlist);
document.getElementById('refresh-ranking-logs').addEventListener('click', () => loadRankingLogs());
document.getElementById('start-ranking-log-auto-refresh').addEventListener('click', startRankingLogAutoRefresh);
document.getElementById('stop-ranking-log-auto-refresh').addEventListener('click', stopRankingLogAutoRefresh);
document.getElementById('refresh-ranking-trajectory').addEventListener('click', () => loadRankingLogs('trajectory'));
document.getElementById('toggle-ranking-history').addEventListener('click', toggleAllRankingHistory);
document.getElementById('refresh-capital-flow').addEventListener('click', loadCapitalFlow);
document.getElementById('sort-change').addEventListener('click', toggleChangeSort);
document.getElementById('select-all-monitor').addEventListener('change', toggleAllMonitorSelections);
document.getElementById('start-monitor').addEventListener('click', startDesktopMonitor);
document.getElementById('stop-monitor').addEventListener('click', stopDesktopMonitor);
document.getElementById('overview-watch-btn').addEventListener('click', addCurrentToWatchlist);
document.getElementById('overview-ai-btn').addEventListener('click', () => openResearch(state.currentOverview));
document.getElementById('refresh-fundamentals').addEventListener('click', () => {
    if (state.currentOverview) loadFundamentals(state.currentOverview.stock.fullCode, true);
});
document.getElementById('insight-form').addEventListener('submit', requestInsight);
document.getElementById('insight-question').addEventListener('input', (event) => {
    document.getElementById('question-count').textContent = event.target.value.length;
});

document.querySelectorAll('.chart-mode').forEach((button) => {
    button.addEventListener('click', () => {
        state.chartMode = button.dataset.chart;
        document.querySelectorAll('.chart-mode').forEach((item) => item.classList.toggle('active', item === button));
        renderChart();
    });
});

document.querySelectorAll('.fundamental-tab').forEach((button) => {
    button.addEventListener('click', () => switchFundamentalTab(button.dataset.fundamentalTab));
});

document.querySelectorAll('.prompt-chip').forEach((button) => {
    button.addEventListener('click', () => {
        state.insightFocus = button.dataset.focus;
        document.querySelectorAll('.prompt-chip').forEach((item) => item.classList.toggle('active', item === button));
    });
});

async function api(url, options = {}) {
    const response = await fetch(url, {cache: 'no-store', ...options});
    if (response.status === 204) return null;

    let payload = null;
    try {
        payload = await response.json();
    } catch (ignored) {
        // Some upstream failures return no JSON body.
    }
    if (!response.ok) {
        throw new Error(payload && payload.message ? payload.message : '请求失败，请稍后重试');
    }
    return payload;
}

async function initializeRuntimeMode() {
    try {
        const runtime = await api('/api/runtime');
        state.publicClean = runtime.publicClean === true;
        document.body.classList.toggle('public-clean', state.publicClean);
        if (state.publicClean) {
            document.querySelectorAll('.private-feature').forEach((element) => element.remove());
            document.querySelectorAll('.clean-only').forEach((element) => element.classList.remove('hidden'));
            document.title = 'Stock Lens - A股公开行情与公司资料';
            setText('product-title', 'A股行情与公司资料');
            setText('search-label', '股票名称或代码');
            document.getElementById('search-input').placeholder = '输入股票名称或代码，例如 贵州茅台、600519';
            setText('market-empty-title', '搜索股票');
            setText('market-empty-description', '查询公开行情、K线、公司业绩、估值与机构研报资料。');
        }
    } catch (ignored) {
        state.publicClean = false;
    }
}

async function switchTab(tabName) {
    document.querySelectorAll('.tab').forEach((tab) => tab.classList.toggle('active', tab.dataset.tab === tabName));
    document.querySelectorAll('.tab-panel').forEach((panel) => panel.classList.toggle('active', panel.id === `tab-${tabName}`));

    if (tabName === 'watchlist') {
        await loadWatchlist();
        await loadMonitorStatus();
    } else if (tabName === 'sectors' && state.sectors.length === 0) {
        await loadSectors();
    } else if (tabName === 'capital-flow') {
        await loadCapitalFlow();
    } else if (tabName === 'sector-strength') {
        if (state.sectors.length === 0) await loadSectors();
        await loadSectorWatchlist();
        renderTop10SectorOptions();
        renderTop10SelectedSectors();
        renderSectorWatchlist();
        syncTop10RefreshControls();
    } else if (tabName === 'ranking-logs') {
        await loadRankingLogs();
    } else if (tabName === 'ranking-trajectory') {
        await loadRankingLogs('trajectory');
    }
}

async function handleSearch(event) {
    event.preventDefault();
    await runtimeReady;
    const input = document.getElementById('search-input');
    const keyword = input.value.trim();
    if (!keyword) {
        showToast('请输入股票名称或代码');
        return;
    }

    toggle('search-loading', true);
    document.getElementById('search-btn').disabled = true;
    try {
        const stocks = state.publicClean
            ? await api(`/api/stock/search?keyword=${encodeURIComponent(keyword)}`)
            : await searchStocksAndSectors(keyword);
        renderSearchResults(stocks || []);
        if (stocks && stocks.length === 1) {
            await loadOverview(stocks[0].fullCode);
        } else if (stocks && stocks.length > 1) {
            const exact = stocks.find((stock) => stock.code === keyword || stock.fullCode === keyword.toLowerCase());
            if (exact) await loadOverview(exact.fullCode);
        }
    } catch (error) {
        showToast(error.message);
    } finally {
        toggle('search-loading', false);
        document.getElementById('search-btn').disabled = false;
    }
}

async function searchStocksAndSectors(keyword) {
    const [sectorSearch, stocks] = await Promise.all([
        api(`/api/sectors/search?keyword=${encodeURIComponent(keyword)}`),
        api(`/api/stock/search?keyword=${encodeURIComponent(keyword)}`)
    ]);
    const matchedSector = sectorSearch && sectorSearch.results && sectorSearch.results[0];
    const thirdLevelMatch = await discoverThirdLevelSector(keyword, stocks || []);
    if (thirdLevelMatch) {
        await loadSectorDetail(
            thirdLevelMatch.sector.id,
            thirdLevelMatch.stock.fullCode
        );
        showToast(`已进入同花顺三级行业：${thirdLevelMatch.sector.name}`);
        return [];
    }
    if (matchedSector) {
        await loadSectorDetail(matchedSector.id);
        showToast(`已进入${matchedSector.type || ''}板块：${matchedSector.name}`);
        return [];
    }
    return stocks || [];
}

async function discoverThirdLevelSector(keyword, stocks) {
    const normalizedKeyword = String(keyword || '')
        .replace(/概念|行业|板块|\s+/g, '')
        .toLowerCase();
    if (!normalizedKeyword || !Array.isArray(stocks) || !stocks.length) return null;
    const exactCode = stocks.some((stock) =>
        String(stock.code || '').toLowerCase() === String(keyword).toLowerCase()
        || String(stock.fullCode || '').toLowerCase() === String(keyword).toLowerCase()
    );
    if (exactCode) return null;
    for (const stock of stocks.slice(0, 5)) {
        if (!stock || !stock.fullCode) continue;
        try {
            const mapping = await api(`/api/sectors/stock-map/${encodeURIComponent(stock.fullCode)}`);
            const sector = mapping && mapping.sector;
            if (!sector || !sector.name) continue;
            const normalizedName = String(sector.name)
                .replace(/概念|行业|板块|\s+/g, '')
                .toLowerCase();
            if (normalizedName.includes(normalizedKeyword)
                || normalizedKeyword.includes(normalizedName)) {
                return {stock, sector};
            }
        } catch (ignored) {
            // A failed candidate should not prevent normal stock search.
        }
    }
    return null;
}

function renderSearchResults(stocks) {
    const section = document.getElementById('search-results-section');
    const list = document.getElementById('search-results');
    list.replaceChildren();
    section.classList.remove('hidden');
    setText('search-result-count', `${stocks.length} 个结果`);

    if (stocks.length === 0) {
        list.append(createElement('p', 'no-results', '没有找到匹配股票。在线股票目录可能仍在后台更新。'));
        return;
    }

    stocks.forEach((stock) => {
        const button = createElement('button', 'search-result-item');
        button.type = 'button';
        const identity = createElement('span', 'result-identity');
        identity.append(
            createElement('strong', '', stock.name),
            createElement('span', 'stock-code', stock.fullCode)
        );
        button.append(identity, createElement('span', 'industry-tag', stock.industry || '其他'));
        button.addEventListener('click', () => loadOverview(stock.fullCode));
        list.append(button);
    });
}

async function loadOverview(fullCode) {
    toggle('market-empty', false);
    toggle('stock-overview', false);
    toggle('overview-loading', true);
    try {
        const overview = await api(`/api/stock/${encodeURIComponent(fullCode)}/overview`);
        state.currentOverview = overview;
        renderOverview(overview);
        toggle('stock-overview', true);
        loadFundamentals(overview.stock.fullCode);
        await updateWatchButton(overview.stock.fullCode);
    } catch (error) {
        toggle('market-empty', true);
        showToast(error.message);
    } finally {
        toggle('overview-loading', false);
    }
}

function renderOverview(overview) {
    const {stock, quote} = overview;
    setText('overview-name', stock.name);
    setText('overview-code', stock.fullCode);
    setText('overview-industry', stock.industry || '其他');
    setText('overview-time', quote.dateTime ? `行情时间 ${formatQuoteTime(quote.dateTime)}` : '行情时间暂不可用');
    setText('overview-price', formatPrice(quote.currentPrice));

    const change = document.getElementById('overview-change');
    change.textContent = formatPercent(quote.changePercent);
    setTrendClass(change, quote.changePercent);
    setTrendClass(document.getElementById('overview-price'), quote.changePercent);

    setText('metric-open', formatPrice(quote.openPrice));
    setText('metric-high', formatPrice(quote.highPrice));
    setText('metric-low', formatPrice(quote.lowPrice));
    setText('metric-close', formatPrice(quote.yesterdayClose));
    setText('metric-volume', formatVolume(quote.volume));
    setText('metric-turnover', formatAmount(quote.turnover));
    renderChart();
}

function renderChart() {
    if (!state.currentOverview) return;
    const fullCode = state.currentOverview.stock.fullCode;
    const chart = document.getElementById('stock-chart');
    const error = document.getElementById('chart-error');
    error.classList.add('hidden');
    chart.classList.remove('hidden');
    chart.alt = `${state.currentOverview.stock.name}${state.chartMode === 'minute' ? '分时图' : '日K线图'}`;
    chart.onload = () => error.classList.add('hidden');
    chart.onerror = () => {
        chart.classList.add('hidden');
        error.classList.remove('hidden');
    };
    const path = state.chartMode === 'minute' ? 'min' : 'daily';
    chart.src = `https://image.sinajs.cn/newchart/${path}/n/${fullCode}.gif?t=${Date.now()}`;
}

function switchFundamentalTab(tabName) {
    state.fundamentalTab = tabName;
    document.querySelectorAll('.fundamental-tab').forEach((button) => {
        button.classList.toggle('active', button.dataset.fundamentalTab === tabName);
    });
    document.querySelectorAll('.fundamental-panel').forEach((panel) => {
        panel.classList.toggle('active', panel.id === `fundamental-panel-${tabName}`);
    });
}

async function loadFundamentals(fullCode, refresh = false) {
    toggle('fundamentals-loading', true);
    toggle('fundamentals-unavailable', false);
    toggle('fundamentals-content', false);
    document.getElementById('refresh-fundamentals').disabled = true;
    setText('fundamentals-status', '正在连接数据源');
    try {
        const query = refresh ? '?refresh=true' : '';
        const snapshot = await api(`/api/stock/${encodeURIComponent(fullCode)}/fundamentals${query}`);
        if (!state.currentOverview || state.currentOverview.stock.fullCode !== fullCode) return;
        state.fundamentals = snapshot;
        renderFundamentals(snapshot);
    } catch (error) {
        toggle('fundamentals-content', false);
        const notice = document.getElementById('fundamentals-unavailable');
        notice.textContent = error.message;
        notice.classList.remove('hidden');
        setText('fundamentals-status', '资料暂不可用');
    } finally {
        toggle('fundamentals-loading', false);
        document.getElementById('refresh-fundamentals').disabled = false;
    }
}

function renderFundamentals(snapshot) {
    const warnings = snapshot.warnings || [];
    const sources = snapshot.sources || [];
    const availableSources = sources.filter((source) => source.available).length;
    const statusParts = [];
    if (snapshot.fetchedAt) statusParts.push(`更新 ${formatDataDateTime(snapshot.fetchedAt)}`);
    if (sources.length) statusParts.push(`数据源 ${availableSources}/${sources.length}`);
    if (warnings.length) statusParts.push(`${warnings.length} 项暂缺`);
    setText('fundamentals-status', statusParts.join(' · ') || '资料已更新');

    const notice = document.getElementById('fundamentals-unavailable');
    if (!snapshot.available) {
        toggle('fundamentals-content', false);
        notice.textContent = warnings[0] || '基本面资料暂不可用';
        notice.classList.remove('hidden');
        return;
    }
    notice.classList.add('hidden');
    toggle('fundamentals-content', true);

    if (snapshot.profile && snapshot.profile.industry) {
        setText('overview-industry', snapshot.profile.industry);
    }
    renderPerformance(snapshot.performance || []);
    renderValuation(snapshot.valuation);
    renderResearchReports(snapshot.researchReports || []);
    renderIndustryProfile(snapshot);
    switchFundamentalTab(state.fundamentalTab);
}

function renderPerformance(periods) {
    const summary = document.getElementById('performance-summary');
    const body = document.getElementById('performance-body');
    summary.replaceChildren();
    body.replaceChildren();
    toggle('performance-empty', periods.length === 0);
    document.querySelector('#fundamental-panel-performance .research-table-wrap')
        .classList.toggle('hidden', periods.length === 0);
    if (periods.length === 0) return;

    const latest = periods[0];
    summary.append(
        metricItem('营业收入', formatFinancialAmount(latest.revenue), formatPercent(latest.revenueYoY), latest.revenueYoY),
        metricItem('归母净利润', formatFinancialAmount(latest.netProfit), formatPercent(latest.netProfitYoY), latest.netProfitYoY),
        metricItem('ROE', formatPlainPercent(latest.roe), latest.reportDate || ''),
        metricItem('毛利率', formatPlainPercent(latest.grossMargin), latest.reportName || '')
    );

    periods.forEach((period) => {
        const row = document.createElement('tr');
        row.append(createElement('td', '', period.reportDate || period.reportName || '--'));
        row.append(numberCell(formatFinancialAmount(period.revenue)));
        row.append(trendNumberCell(formatPercent(period.revenueYoY), period.revenueYoY));
        row.append(numberCell(formatFinancialAmount(period.netProfit)));
        row.append(trendNumberCell(formatPercent(period.netProfitYoY), period.netProfitYoY));
        row.append(numberCell(formatPlainPercent(period.roe)));
        row.append(numberCell(formatPlainPercent(period.grossMargin)));
        row.append(numberCell(formatPlainPercent(period.debtRatio)));
        row.append(createElement('td', 'source-cell', period.source || '--'));
        body.append(row);
    });
}

function renderValuation(valuation) {
    const metrics = document.getElementById('valuation-metrics');
    const peerBody = document.getElementById('valuation-peer-body');
    metrics.replaceChildren();
    peerBody.replaceChildren();
    toggle('valuation-empty', !valuation);
    if (!valuation) {
        toggle('valuation-peer-wrap', false);
        return;
    }

    metrics.append(
        metricItem('PE-TTM', formatMultiple(valuation.peTtm), valuation.peRank ? `行业排名 ${valuation.peRank}` : valuation.date || ''),
        metricItem('市净率 PB', formatMultiple(valuation.pb), valuation.industryPb != null ? `行业中位 ${formatMultiple(valuation.industryPb)}` : ''),
        metricItem('PEG', formatMultiple(valuation.peg), '盈利增速匹配度'),
        metricItem('市销率 PS', formatMultiple(valuation.ps), valuation.industryPeTtm != null ? `行业PE ${formatMultiple(valuation.industryPeTtm)}` : ''),
        metricItem('市现率 PCF', formatMultiple(valuation.pcf), valuation.date || ''),
        metricItem('总市值', formatFinancialAmount(valuation.totalMarketValue), '最新估值数据')
    );

    const peers = valuation.peers || [];
    toggle('valuation-peer-wrap', peers.length > 0);
    peers.forEach((peer) => {
        const row = document.createElement('tr');
        const nameCell = document.createElement('td');
        const identity = createElement('div', 'table-stock-identity');
        identity.append(createElement('strong', '', peer.name || '--'), createElement('span', 'stock-code', peer.code || ''));
        nameCell.append(identity);
        row.append(nameCell);
        row.append(numberCell(formatMultiple(peer.peTtm)));
        row.append(numberCell(formatMultiple(peer.pb)));
        row.append(numberCell(formatMultiple(peer.peg)));
        row.append(numberCell(peer.rank || '--'));
        peerBody.append(row);
    });
}

function renderResearchReports(reports) {
    const list = document.getElementById('research-report-list');
    list.replaceChildren();
    toggle('reports-empty', reports.length === 0);
    reports.forEach((report) => {
        const article = createElement('article', 'research-report-item');
        const meta = createElement('div', 'report-meta');
        meta.append(
            createElement('span', '', report.date || '--'),
            createElement('span', '', report.institution || '未知机构')
        );
        if (!state.publicClean) meta.append(createElement('strong', '', report.rating || '未评级'));
        const title = report.pdfUrl && report.pdfUrl.startsWith('http')
            ? document.createElement('a')
            : document.createElement('strong');
        title.textContent = report.title || '未命名研报';
        if (title.tagName === 'A') {
            title.href = report.pdfUrl;
            title.target = '_blank';
            title.rel = 'noopener noreferrer';
        }
        const forecasts = createElement('div', 'report-forecasts');
        appendForecasts(forecasts, 'EPS', report.epsForecasts);
        appendForecasts(forecasts, 'PE', report.peForecasts);
        article.append(meta, title);
        if (forecasts.childElementCount) article.append(forecasts);
        list.append(article);
    });
}

function renderIndustryProfile(snapshot) {
    const profileContainer = document.getElementById('company-profile');
    const positionContainer = document.getElementById('industry-position');
    const tagsContainer = document.getElementById('business-tags');
    const sourcesContainer = document.getElementById('data-sources');
    profileContainer.replaceChildren();
    positionContainer.replaceChildren();
    tagsContainer.replaceChildren();
    sourcesContainer.replaceChildren();

    const profile = snapshot.profile;
    if (profile) {
        const heading = createElement('div', 'subsection-heading');
        heading.append(createElement('h3', '', profile.companyName || '公司概况'), createElement('span', '', profile.industry || '行业待补充'));
        const grid = createElement('dl', 'profile-grid');
        appendProfileItem(grid, '上市日期', profile.listDate);
        appendProfileItem(grid, '总市值', formatFinancialAmount(profile.totalMarketValue));
        appendProfileItem(grid, '流通市值', formatFinancialAmount(profile.floatMarketValue));
        appendProfileItem(grid, '主营业务', profile.mainBusiness, true);
        appendProfileItem(grid, '经营范围', profile.businessScope, true);
        profileContainer.append(heading, grid);
    }

    const position = snapshot.industryPosition;
    if (position) {
        positionContainer.append(createElement('h3', '', '同行位置'));
        const metrics = createElement('div', 'position-metrics');
        metrics.append(
            metricItem('估值排名', position.valuationRank || '--', position.industry || ''),
            metricItem('成长排名', position.growthRank || '--', '同行净利润增速'),
            metricItem('ROE排名', position.roeRank || '--', '数据源可用时更新'),
            metricItem('规模排名', position.scaleRank || '--', '数据源可用时更新')
        );
        positionContainer.append(metrics);
    }

    const concepts = snapshot.concepts || [];
    if (concepts.length) {
        tagsContainer.append(createElement('h3', '', '业务标签'));
        const tags = createElement('div', 'tag-list');
        concepts.forEach((concept) => tags.append(createElement('span', 'business-tag', concept)));
        tagsContainer.append(tags);
    }

    const sources = snapshot.sources || [];
    if (sources.length) {
        sourcesContainer.append(createElement('h3', '', '数据来源'));
        const list = createElement('div', 'source-list');
        sources.forEach((item) => {
            const sourceItem = createElement('div', `source-status ${item.available ? 'available' : 'unavailable'}`);
            sourceItem.append(createElement('span', 'source-dot'), createElement('span', '', item.name));
            if (!item.available && item.message) sourceItem.title = item.message;
            list.append(sourceItem);
        });
        sourcesContainer.append(list);
    }
}

function metricItem(label, value, detail = '', trendValue = null) {
    const item = createElement('div', 'fundamental-metric');
    const strong = createElement('strong', '', value == null || value === '' ? '--' : value);
    if (Number.isFinite(trendValue)) setTrendClass(strong, trendValue);
    item.append(createElement('span', '', label), strong);
    if (detail) item.append(createElement('small', '', detail));
    return item;
}

function appendProfileItem(list, label, value, wide = false) {
    if (!value || value === '--') return;
    const item = createElement('div', wide ? 'profile-item wide' : 'profile-item');
    item.append(createElement('dt', '', label), createElement('dd', '', value));
    list.append(item);
}

function appendForecasts(container, label, values) {
    if (!values) return;
    Object.entries(values).forEach(([year, value]) => {
        if (!Number.isFinite(value)) return;
        container.append(createElement('span', '', `${year} ${label} ${Number(value).toFixed(2)}`));
    });
}

function trendNumberCell(text, value) {
    const cell = numberCell(text);
    if (Number.isFinite(value)) setTrendClass(cell, value);
    return cell;
}

function formatPlainPercent(value) {
    return Number.isFinite(value) ? `${Number(value).toFixed(2)}%` : '--';
}

function formatMultiple(value) {
    return Number.isFinite(value) ? `${Number(value).toFixed(2)}x` : '--';
}

function formatFinancialAmount(value) {
    if (!Number.isFinite(value)) return '--';
    const absolute = Math.abs(value);
    const sign = value < 0 ? '-' : '';
    if (absolute >= 100000000) return `${sign}${(absolute / 100000000).toFixed(2)}亿元`;
    if (absolute >= 10000) return `${sign}${(absolute / 10000).toFixed(1)}万元`;
    return `${sign}${absolute.toFixed(0)}元`;
}

function formatDataDateTime(value) {
    return value ? String(value).replace('T', ' ').slice(0, 16) : '--';
}

// 行情详情和自选列表仍需要展示行情时间；排名日志不再使用该字段展示。
function formatQuoteTime(value) {
    if (!value) return '--';
    const digits = String(value).replace(/\D/g, '');
    if (digits.length >= 14) {
        return `${digits.slice(8, 10)}:${digits.slice(10, 12)}:${digits.slice(12, 14)}`;
    }
    return String(value);
}

async function updateWatchButton(fullCode) {
    const button = document.getElementById('overview-watch-btn');
    try {
        const result = await api(`/api/watchlist/${encodeURIComponent(fullCode)}/exists`);
        button.textContent = result.exists ? '已在自选' : '加入自选';
        button.disabled = result.exists;
    } catch (ignored) {
        button.textContent = '加入自选';
        button.disabled = false;
    }
}

async function addCurrentToWatchlist() {
    if (!state.currentOverview) return;
    const button = document.getElementById('overview-watch-btn');
    button.disabled = true;
    try {
        const item = await addStockToWatchlist(state.currentOverview.stock.fullCode, 'MANUAL');
        upsertWatchlistItem(item);
        button.textContent = '已在自选';
        showToast('已加入自选');
    } catch (error) {
        button.disabled = false;
        showToast(error.message);
    }
}

async function addStockToWatchlist(fullCode, sourceCategory = 'MANUAL') {
    return api('/api/watchlist', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({code: fullCode, sourceCategory})
    });
}

function upsertWatchlistItem(item) {
    if (!item || !item.stock || !item.stock.fullCode) return;
    const code = item.stock.fullCode;
    const index = state.watchlistItems.findIndex((current) => current.stock.fullCode === code);
    if (index >= 0) state.watchlistItems[index] = item;
    else state.watchlistItems.push(item);
    if (document.getElementById('tab-watchlist')?.classList.contains('active')) {
        renderWatchlist(state.watchlistItems);
    }
}

function findWatchlistItem(fullCode) {
    return state.watchlistItems.find((item) => item.stock && item.stock.fullCode === fullCode) || null;
}

function watchlistSourceLabel(item) {
    const source = item && item.sourceCategory;
    if (source === 'RANKING_TRACE') return '排名轨迹';
    if (source === 'MANUAL_AND_RANKING_TRACE') return '普通自选 + 排名轨迹';
    return '普通自选';
}

function hasRankingTraceSource(item) {
    return item && (item.sourceCategory === 'RANKING_TRACE'
        || item.sourceCategory === 'MANUAL_AND_RANKING_TRACE');
}

async function removeRankingTraceSource(fullCode) {
    const response = await fetch(`/api/watchlist/${encodeURIComponent(fullCode)}/source/RANKING_TRACE`, {
        method: 'DELETE'
    });
    if (!response.ok) {
        const payload = await response.json().catch(() => null);
        throw new Error(payload?.message || `请求失败（${response.status}）`);
    }
    if (response.status === 204) return null;
    return response.json();
}

async function loadWatchlist() {
    toggle('watchlist-loading', true);
    try {
        const items = await api('/api/watchlist');
        renderWatchlist(items || []);
    } catch (error) {
        showToast(error.message);
    } finally {
        toggle('watchlist-loading', false);
    }
}

function renderWatchlist(items) {
    state.watchlistItems = items;
    const activeCodes = new Set(items.map((item) => item.stock.fullCode));
    state.selectedMonitorCodes.forEach((code) => {
        if (!activeCodes.has(code)) state.selectedMonitorCodes.delete(code);
    });

    const body = document.getElementById('watchlist-body');
    body.replaceChildren();
    toggle('watchlist-empty', items.length === 0);
    toggle('watchlist-table-wrap', items.length > 0);
    sortedWatchlistItems().forEach((item) => body.append(createWatchlistRow(item)));
    updateMonitorSelectionUi();
}

function sortedWatchlistItems() {
    const direction = state.changeSortDirection === 'desc' ? -1 : 1;
    return [...state.watchlistItems].sort((left, right) => {
        const leftAvailable = left.quoteAvailable && left.quote;
        const rightAvailable = right.quoteAvailable && right.quote;
        if (!leftAvailable && !rightAvailable) return 0;
        if (!leftAvailable) return 1;
        if (!rightAvailable) return -1;
        const leftChange = left.quote.changePercent;
        const rightChange = right.quote.changePercent;
        return (leftChange - rightChange) * direction;
    });
}

function toggleChangeSort() {
    state.changeSortDirection = state.changeSortDirection === 'desc' ? 'asc' : 'desc';
    setText('sort-change-icon', state.changeSortDirection === 'desc' ? '↓' : '↑');
    renderWatchlist(state.watchlistItems);
}

function createWatchlistRow(item) {
    const {stock, quote, quoteAvailable} = item;
    const row = document.createElement('tr');
    if (!quoteAvailable) row.classList.add('quote-missing-row');

    const stockCell = document.createElement('td');
    const identity = createElement('div', 'table-stock-identity');
    identity.append(createElement('strong', '', stock.name), createElement('span', 'stock-code', stock.fullCode));
    stockCell.append(identity);
    row.append(stockCell);
    const sourceCell = createElement('td', 'watchlist-source-cell', watchlistSourceLabel(item));
    if (item.sourceCategory === 'RANKING_TRACE' || item.sourceCategory === 'MANUAL_AND_RANKING_TRACE') {
        sourceCell.classList.add('ranking-trace-source');
    }
    row.append(sourceCell);

    if (quoteAvailable && quote) {
        row.append(numberCell(formatPrice(quote.currentPrice)));
        const changeCell = numberCell(formatPercent(quote.changePercent));
        changeCell.classList.add('strong-cell');
        setTrendClass(changeCell, quote.changePercent);
        row.append(changeCell);
        row.append(numberCell(`${formatPrice(quote.highPrice)} / ${formatPrice(quote.lowPrice)}`));
        row.append(numberCell(formatVolume(quote.volume)));
        row.append(numberCell(formatAmount(quote.turnover)));
        row.append(createElement('td', 'time-cell', formatQuoteTime(quote.dateTime)));
    } else {
        row.append(numberCell('--'), numberCell('--'), numberCell('--'), numberCell('--'), numberCell('--'));
        row.append(createElement('td', 'time-cell', '行情暂不可用'));
    }

    const actionCell = document.createElement('td');
    const actions = createElement('div', 'table-actions');
    const viewButton = createElement('button', 'text-button', '走势');
    viewButton.type = 'button';
    viewButton.addEventListener('click', () => {
        switchTab('market');
        loadOverview(stock.fullCode);
    });
    const aiButton = createElement('button', 'text-button', 'AI研判');
    aiButton.type = 'button';
    aiButton.addEventListener('click', async () => {
        if (quoteAvailable && quote) {
            openResearch({stock, quote});
            return;
        }
        try {
            openResearch(await api(`/api/stock/${encodeURIComponent(stock.fullCode)}/overview`));
        } catch (error) {
            showToast(error.message);
        }
    });
    const sectorButton = createElement('button', 'text-button', '板块强度');
    sectorButton.type = 'button';
    sectorButton.addEventListener('click', () => openStockSector(stock.fullCode));
    const removeButton = createElement('button', 'text-button danger', '移除');
    removeButton.type = 'button';
    removeButton.addEventListener('click', () => removeWatchlistItem(stock.fullCode));
    actions.append(viewButton);
    if (!state.publicClean) actions.append(sectorButton, aiButton);
    actions.append(removeButton);
    actionCell.append(actions);
    row.append(actionCell);

    const monitorCell = createElement('td', 'monitor-cell');
    const monitorCheckbox = document.createElement('input');
    monitorCheckbox.type = 'checkbox';
    monitorCheckbox.className = 'monitor-checkbox';
    monitorCheckbox.checked = state.selectedMonitorCodes.has(stock.fullCode);
    monitorCheckbox.disabled = !quoteAvailable;
    monitorCheckbox.setAttribute('aria-label', `盯盘 ${stock.name}`);
    monitorCheckbox.addEventListener('change', () => {
        if (monitorCheckbox.checked) state.selectedMonitorCodes.add(stock.fullCode);
        else state.selectedMonitorCodes.delete(stock.fullCode);
        updateMonitorSelectionUi();
    });
    monitorCell.append(monitorCheckbox);
    row.append(monitorCell);
    return row;
}

async function removeWatchlistItem(fullCode) {
    try {
        await api(`/api/watchlist/${encodeURIComponent(fullCode)}`, {method: 'DELETE'});
        state.selectedMonitorCodes.delete(fullCode);
        showToast('已移除自选');
        await loadWatchlist();
    } catch (error) {
        showToast(error.message);
    }
}

function numberCell(value) {
    return createElement('td', 'number-cell', value);
}

function toggleAllMonitorSelections(event) {
    const selectableCodes = state.watchlistItems
        .filter((item) => item.quoteAvailable)
        .map((item) => item.stock.fullCode);
    selectableCodes.forEach((code) => {
        if (event.target.checked) state.selectedMonitorCodes.add(code);
        else state.selectedMonitorCodes.delete(code);
    });
    renderWatchlist(state.watchlistItems);
}

function updateMonitorSelectionUi() {
    const selectedCount = state.selectedMonitorCodes.size;
    setText('monitor-selection-count', `已选 ${selectedCount} 只`);
    document.getElementById('start-monitor').disabled = selectedCount === 0;

    const selectableCodes = state.watchlistItems
        .filter((item) => item.quoteAvailable)
        .map((item) => item.stock.fullCode);
    const selectAll = document.getElementById('select-all-monitor');
    const checkedCount = selectableCodes.filter((code) => state.selectedMonitorCodes.has(code)).length;
    selectAll.checked = selectableCodes.length > 0 && checkedCount === selectableCodes.length;
    selectAll.indeterminate = checkedCount > 0 && checkedCount < selectableCodes.length;
}

async function startDesktopMonitor() {
    const codes = [...state.selectedMonitorCodes];
    if (codes.length === 0) return;
    const intervalInput = document.getElementById('monitor-interval-seconds');
    const intervalSeconds = Number.parseInt(intervalInput.value, 10);
    if (!Number.isInteger(intervalSeconds) || intervalSeconds < 1 || intervalSeconds > 300) {
        showToast('刷新频率请输入 1 到 300 秒');
        intervalInput.focus();
        return;
    }
    const button = document.getElementById('start-monitor');
    button.disabled = true;
    try {
        const status = await api('/api/watchlist/monitor/start', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({codes, intervalSeconds})
        });
        renderMonitorStatus(status);
        showToast(`已启动 ${status.count} 只股票桌面盯盘`);
    } catch (error) {
        showToast(error.message);
    } finally {
        button.disabled = state.selectedMonitorCodes.size === 0;
    }
}

async function stopDesktopMonitor() {
    try {
        const status = await api('/api/watchlist/monitor/stop', {method: 'POST'});
        renderMonitorStatus(status);
        showToast('已停止桌面盯盘');
    } catch (error) {
        showToast(error.message);
    }
}

async function loadMonitorStatus() {
    try {
        const status = await api('/api/watchlist/monitor/status');
        if (status.active && status.codes) {
            state.selectedMonitorCodes = new Set(status.codes);
            if (state.watchlistItems.length) renderWatchlist(state.watchlistItems);
        }
        if (Number.isInteger(status.intervalSeconds)) {
            state.monitorIntervalSeconds = status.intervalSeconds;
            document.getElementById('monitor-interval-seconds').value = status.intervalSeconds;
        }
        renderMonitorStatus(status);
    } catch (ignored) {
        // Monitor status does not block watchlist rendering.
    }
}

function renderMonitorStatus(status) {
    state.monitorActive = Boolean(status && status.active);
    const element = document.getElementById('monitor-status');
    if (!state.monitorActive) {
        element.classList.add('hidden');
        return;
    }
    element.textContent = `桌面盯盘运行中，共 ${status.count} 只，每 ${status.intervalSeconds} 秒刷新。悬浮文字可直接拖动位置。`;
    element.classList.remove('hidden');
}

async function loadSectors(refresh = false) {
    toggle('sector-loading', true);
    toggle('sector-empty', false);
    toggle('sector-table-wrap', false);
    document.getElementById('refresh-sectors').disabled = true;
    try {
        const response = await api(`/api/sectors?refresh=${refresh}`);
        state.sectors = response.sectors || [];
        state.sectorResponse = response;
        renderSectors(response);
        renderTop10SectorOptions();
    } catch (error) {
        toggle('sector-empty', true);
        showToast(error.message);
    } finally {
        toggle('sector-loading', false);
        document.getElementById('refresh-sectors').disabled = false;
    }
}

function renderSectors(response) {
    const sectors = response.sectors || [];
    const body = document.getElementById('sector-body');
    body.replaceChildren();
    toggle('sector-empty', sectors.length === 0);
    toggle('sector-table-wrap', sectors.length > 0);

    sortedSectorItems(sectors).forEach((sector) => {
        const row = document.createElement('tr');
        row.classList.add('clickable-sector-row');
        row.tabIndex = 0;
        row.addEventListener('click', () => loadSectorDetail(sector.id));
        row.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                loadSectorDetail(sector.id);
            }
        });
        const sectorCell = document.createElement('td');
        const identity = createElement('div', 'table-stock-identity');
        identity.append(createElement('strong', '', sector.name), createElement('span', 'stock-code', sector.id));
        sectorCell.append(identity);
        row.append(sectorCell);
        row.append(flowNumberCell(formatPercent(sector.changePercent), sector.changePercent));
        row.append(flowNumberCell(formatYuanAmount(sector.netInflow), sector.netInflow));
        row.append(flowNumberCell(formatPercent(sector.netInflowRatio), sector.netInflowRatio));
        row.append(numberCell(formatYuanAmount(sector.totalAmount)));
        row.append(numberCell(String(sector.companyCount || 0)));

        const leaderCell = document.createElement('td');
        const leader = createElement('div', 'table-stock-identity');
        leader.append(
            createElement('strong', '', sector.leaderName || '--'),
            createElement('span', 'stock-code', sector.leaderCode || '')
        );
        leaderCell.append(leader);
        row.append(leaderCell);
        row.append(flowNumberCell(formatPercent(sector.leaderChangePercent), sector.leaderChangePercent));

        const scoreCell = numberCell(Number.isFinite(sector.score) ? sector.score.toFixed(1) : '--');
        scoreCell.classList.add('mainline-score');
        row.append(scoreCell);
        row.append(createElement('td', `sector-status status-${sectorStatusClass(sector.status)}`, sector.status || '观察'));

        const actionCell = document.createElement('td');
        const button = createElement('button', 'text-button', '查看龙头');
        button.type = 'button';
        button.addEventListener('click', (event) => {
            event.stopPropagation();
            loadSectorDetail(sector.id);
        });
        actionCell.append(button);
        row.append(actionCell);
        body.append(row);
    });

    const warnings = [...(response.warnings || [])];
    if (!response.flowAvailable) warnings.push('行业资金流暂不可用，当前主线评分仅使用板块涨幅、领涨强度与成交活跃度。');
    const warningBox = document.getElementById('sector-warnings');
    warningBox.textContent = [...new Set(warnings)].join(' ');
    warningBox.classList.toggle('hidden', warnings.length === 0);
    updateSectorSortIcons();
}

function changeSectorSort(key) {
    if (state.sectorSortKey === key) {
        state.sectorSortDirection = state.sectorSortDirection === 'desc' ? 'asc' : 'desc';
    } else {
        state.sectorSortKey = key;
        state.sectorSortDirection = ['name', 'leaderName', 'status'].includes(key) ? 'asc' : 'desc';
    }
    if (state.sectorResponse) renderSectors(state.sectorResponse);
}

function sortedSectorItems(sectors) {
    const direction = state.sectorSortDirection === 'desc' ? -1 : 1;
    const key = state.sectorSortKey;
    return [...sectors].sort((left, right) => {
        const leftValue = left[key];
        const rightValue = right[key];
        const leftMissing = leftValue === null || leftValue === undefined || leftValue === '';
        const rightMissing = rightValue === null || rightValue === undefined || rightValue === '';
        if (leftMissing && rightMissing) return 0;
        if (leftMissing) return 1;
        if (rightMissing) return -1;
        if (typeof leftValue === 'string' || typeof rightValue === 'string') {
            return String(leftValue).localeCompare(String(rightValue), 'zh-CN') * direction;
        }
        return (Number(leftValue) - Number(rightValue)) * direction;
    });
}

function updateSectorSortIcons() {
    document.querySelectorAll('[data-sector-sort-icon]').forEach((icon) => {
        icon.textContent = icon.dataset.sectorSortIcon === state.sectorSortKey
            ? state.sectorSortDirection === 'desc' ? '↓' : '↑'
            : '';
    });
}

function sectorStatusClass(status) {
    if (status === '主线候选') return 'mainline';
    if (status === '活跃') return 'active';
    if (status === '偏弱') return 'weak';
    return 'watch';
}

async function openStockSector(fullCode) {
    await switchTab('sector-strength');
    state.currentSectorSelectedCode = fullCode;
    await requestSectorDetail(`/api/sectors/stock/${encodeURIComponent(fullCode)}`, true, {activateTab: false});
}

async function loadSectorDetail(sectorId, selectedCode = null, refresh = false, options = {}) {
    if (state.sectorDetailLoading) return false;
    addTop10SectorSelection(sectorId, selectedCode, true);
    const selectedQuery = selectedCode ? `&selected=${encodeURIComponent(selectedCode)}` : '';
    return requestSectorDetail(
        `/api/sectors/${encodeURIComponent(sectorId)}?refresh=${refresh}${selectedQuery}`,
        false,
        {...options, requestedSectorId: sectorId}
    );
}

async function requestSectorDetail(path, stockLookup, options = {}) {
    if (state.sectorDetailLoading) return false;
    if (options.activateTab !== false) await switchTab('sector-strength');

    state.sectorDetailLoading = true;
    toggle('sector-detail-loading', true);
    setText('sector-detail-loading-text', '正在计算 Top10 龙头的分钟动量、量价强度、业绩和研报...');
    toggle('sector-detail-empty', false);
    toggle('sector-strength-table-wrap', false);
    syncTop10RefreshControls();
    try {
        const response = await api(path);
        if (response.available === false) throw new Error(response.message || '板块强度数据暂不可用，请稍后重试');
        acceptTop10SectorResponse(response, {
            selectedCode: response.selectedCode || state.currentSectorSelectedCode,
            requestedSectorId: options.requestedSectorId,
            activate: true,
            render: true
        });
        return true;
    } catch (error) {
        toggle('sector-detail-empty', true);
        showToast(error.message);
        return false;
    } finally {
        state.sectorDetailLoading = false;
        toggle('sector-detail-loading', false);
        syncTop10RefreshControls();
    }
}

function findSectorSummary(sectorId) {
    return state.sectors.find((sector) => sector.id === sectorId)
        || (state.top10SectorDetails.get(sectorId) || {}).sector
        || null;
}

function addTop10SectorSelection(sectorOrId, selectedCode = null, activate = false) {
    const sectorId = typeof sectorOrId === 'string' ? sectorOrId : sectorOrId && sectorOrId.id;
    if (!sectorId) return null;
    const summary = typeof sectorOrId === 'string' ? findSectorSummary(sectorId) : sectorOrId;
    const existing = state.top10SelectedSectors.get(sectorId) || {};
    const selection = {
        ...existing,
        id: sectorId,
        name: (summary && summary.name) || existing.name || sectorId,
        type: (summary && summary.type) || existing.type || '板块',
        selectedCode: selectedCode || existing.selectedCode || null
    };
    state.top10SelectedSectors.set(sectorId, selection);
    if (activate || !state.currentSectorId) {
        state.currentSectorId = sectorId;
        state.currentSectorSelectedCode = selection.selectedCode;
    }
    renderTop10SectorOptions();
    renderTop10SelectedSectors();
    renderSectorWatchlist();
    syncTop10RefreshControls();
    return selection;
}

async function loadSectorWatchlist(force = false) {
    if (state.sectorWatchlistLoaded && !force) return;
    try {
        const items = await api('/api/sectors/watchlist');
        state.sectorWatchlist = new Map((items || []).map((item) => [item.sectorId, item]));
        state.sectorWatchlistLoaded = true;
        let first = state.top10SelectedSectors.size === 0;
        state.sectorWatchlist.forEach((item) => {
            addTop10SectorSelection({
                id: item.sectorId,
                name: item.sectorName,
                type: item.sectorType
            }, item.selectedCode, first);
            first = false;
        });
        const savedInterval = Number.parseInt(window.localStorage.getItem('stockLens.top10IntervalSeconds'), 10);
        if (Number.isInteger(savedInterval) && savedInterval >= 5 && savedInterval <= 3600) {
            state.top10AutoRefreshSeconds = savedInterval;
            document.getElementById('top10-interval-seconds').value = String(savedInterval);
        }
        if (state.currentSectorId && !state.top10SectorDetails.has(state.currentSectorId)) {
            renderPendingTop10Sector(state.top10SelectedSectors.get(state.currentSectorId));
        }
        renderSectorWatchlist();
    } catch (error) {
        showToast(`自选盯盘板块加载失败：${error.message}`);
    }
}

async function saveSelectedSectorWatchlist() {
    const selections = [...state.top10SelectedSectors.values()];
    if (selections.length === 0) {
        showToast('请先选择至少一个板块');
        return;
    }
    const button = document.getElementById('save-top10-sector-watchlist');
    button.disabled = true;
    let saved = 0;
    try {
        for (const selection of selections) {
            const item = await api('/api/sectors/watchlist', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    sectorId: selection.id,
                    sectorName: selection.name,
                    sectorType: selection.type,
                    selectedCode: selection.selectedCode
                })
            });
            state.sectorWatchlist.set(item.sectorId, item);
            saved += 1;
        }
        state.sectorWatchlistLoaded = true;
        renderSectorWatchlist();
        renderTop10SelectedSectors();
        showToast(`已保存 ${saved} 个自选盯盘板块`);
    } catch (error) {
        showToast(error.message);
    } finally {
        syncTop10RefreshControls();
    }
}

async function removeSectorWatchlistItem(sectorId) {
    try {
        await api(`/api/sectors/watchlist/${encodeURIComponent(sectorId)}`, {method: 'DELETE'});
        state.sectorWatchlist.delete(sectorId);
        renderSectorWatchlist();
        renderTop10SelectedSectors();
        syncTop10RefreshControls();
        showToast('已移出自选盯盘板块');
    } catch (error) {
        showToast(error.message);
    }
}

function activateSectorWatchlistItem(item) {
    const existing = state.top10SelectedSectors.has(item.sectorId);
    addTop10SectorSelection({
        id: item.sectorId,
        name: item.sectorName,
        type: item.sectorType
    }, item.selectedCode, true);
    if (state.top10SectorDetails.has(item.sectorId)) {
        showTop10Sector(item.sectorId, false);
    } else {
        renderPendingTop10Sector(state.top10SelectedSectors.get(item.sectorId));
    }
    if (!existing) showToast(`${item.sectorName} 已加入本轮刷新队列`);
}

function renderSectorWatchlist() {
    const panel = document.getElementById('sector-watchlist-panel');
    const container = document.getElementById('sector-watchlist-items');
    if (!panel || !container) return;
    const items = [...state.sectorWatchlist.values()];
    container.replaceChildren();
    items.forEach((item) => {
        const chip = createElement('div', `sector-watchlist-chip${state.top10SelectedSectors.has(item.sectorId) ? ' selected' : ''}`);
        const activate = createElement('button', 'sector-watchlist-chip-main');
        activate.type = 'button';
        const identity = createElement('span', 'sector-watchlist-chip-identity');
        identity.append(
            createElement('strong', '', item.sectorName),
            createElement('small', '', `${item.sectorType || '板块'} · ${item.lastRefreshedAt ? `上次刷新 ${formatDataDateTime(item.lastRefreshedAt)}` : '尚未刷新'}`)
        );
        activate.append(identity);
        activate.addEventListener('click', () => activateSectorWatchlistItem(item));
        const remove = createElement('button', 'sector-watchlist-chip-remove', '×');
        remove.type = 'button';
        remove.title = `从自选盯盘移除${item.sectorName}`;
        remove.setAttribute('aria-label', remove.title);
        remove.addEventListener('click', () => removeSectorWatchlistItem(item.sectorId));
        chip.append(activate, remove);
        container.append(chip);
    });
    setText('sector-watchlist-count', `${items.length} 个`);
    panel.classList.toggle('hidden', items.length === 0);
}

function removeTop10SectorSelection(sectorId) {
    if (state.sectorDetailLoading) return;
    const wasActive = state.currentSectorId === sectorId;
    state.top10SelectedSectors.delete(sectorId);
    if (wasActive) {
        const next = state.top10SelectedSectors.values().next().value || null;
        state.currentSectorId = next ? next.id : null;
        state.currentSectorSelectedCode = next ? next.selectedCode : null;
        if (next) {
            showTop10Sector(next.id, false);
        } else {
            renderEmptyTop10Selection();
        }
    }
    if (state.top10SelectedSectors.size === 0 && state.top10AutoRefreshActive) {
        stopTop10AutoRefresh(true);
    }
    renderTop10SectorOptions();
    renderTop10SelectedSectors();
    renderSectorWatchlist();
    syncTop10RefreshControls();
    updateTop10AutoStatus();
}

function clearTop10SectorSelections() {
    if (state.sectorDetailLoading) return;
    state.top10SelectedSectors.clear();
    state.currentSectorId = null;
    state.currentSectorSelectedCode = null;
    if (state.top10AutoRefreshActive) stopTop10AutoRefresh(true);
    renderTop10SectorOptions();
    renderTop10SelectedSectors();
    renderSectorWatchlist();
    renderEmptyTop10Selection();
    syncTop10RefreshControls();
    updateTop10AutoStatus();
}

async function toggleTop10SectorPicker() {
    if (state.sectors.length === 0) await loadSectors();
    state.top10PickerOpen = !state.top10PickerOpen;
    const picker = document.getElementById('top10-sector-picker');
    const button = document.getElementById('toggle-top10-sector-picker');
    picker.classList.toggle('hidden', !state.top10PickerOpen);
    button.setAttribute('aria-expanded', String(state.top10PickerOpen));
    if (state.top10PickerOpen) {
        renderTop10SectorOptions();
        document.getElementById('top10-sector-search').focus();
    }
}

function renderTop10SectorOptions() {
    const container = document.getElementById('top10-sector-options');
    if (!container) return;
    const keyword = document.getElementById('top10-sector-search').value.trim().toLocaleLowerCase('zh-CN');
    const matches = state.sectors
        .filter((sector) => !keyword
            || String(sector.name || '').toLocaleLowerCase('zh-CN').includes(keyword)
            || String(sector.id || '').toLocaleLowerCase('zh-CN').includes(keyword))
        .slice(0, 120);
    container.replaceChildren();
    if (matches.length === 0) {
        container.append(createElement('p', 'top10-sector-no-result', '没有找到匹配板块'));
        return;
    }
    matches.forEach((sector) => {
        const label = createElement('label', 'top10-sector-option');
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.checked = state.top10SelectedSectors.has(sector.id);
        checkbox.disabled = state.sectorDetailLoading;
        checkbox.addEventListener('change', () => {
            if (checkbox.checked) {
                const isFirst = state.top10SelectedSectors.size === 0;
                addTop10SectorSelection(sector, null, isFirst);
                if (isFirst) renderPendingTop10Sector(sector);
            } else {
                removeTop10SectorSelection(sector.id);
            }
        });
        const identity = createElement('span', 'top10-sector-option-name');
        identity.append(
            createElement('strong', '', sector.name),
            createElement('small', '', `${sector.type || '板块'} · 今日 ${formatPercent(sector.changePercent)}`)
        );
        label.append(checkbox, identity);
        container.append(label);
    });
}

function renderTop10SelectedSectors() {
    const container = document.getElementById('top10-selected-sectors');
    if (!container) return;
    container.replaceChildren();
    const selections = [...state.top10SelectedSectors.values()];
    selections.forEach((selection) => {
        const chip = createElement('div', 'top10-sector-chip');
        chip.classList.toggle('active', selection.id === state.currentSectorId);
        chip.classList.toggle('is-favorite', state.sectorWatchlist.has(selection.id));
        const activate = createElement('button', 'top10-sector-chip-label', selection.name);
        activate.type = 'button';
        activate.title = state.top10SectorDetails.has(selection.id) ? '查看已缓存的 Top10' : '加载该板块 Top10';
        activate.addEventListener('click', () => showTop10Sector(selection.id, true));
        const remove = createElement('button', 'top10-sector-chip-remove', '×');
        remove.type = 'button';
        remove.disabled = state.sectorDetailLoading;
        remove.setAttribute('aria-label', `移除${selection.name}`);
        remove.title = `移除${selection.name}`;
        remove.addEventListener('click', () => removeTop10SectorSelection(selection.id));
        chip.append(activate, remove);
        container.append(chip);
    });
    container.classList.toggle('hidden', selections.length === 0);
    setText('top10-selected-count', `已选 ${selections.length} 个板块`);
    document.getElementById('clear-top10-sectors').disabled = selections.length === 0 || state.sectorDetailLoading;
    document.getElementById('save-top10-sector-watchlist').disabled = selections.length === 0 || state.sectorDetailLoading;
}

async function showTop10Sector(sectorId, loadIfMissing) {
    const selection = state.top10SelectedSectors.get(sectorId);
    if (!selection) return;
    state.currentSectorId = sectorId;
    state.currentSectorSelectedCode = selection.selectedCode;
    renderTop10SelectedSectors();
    const cached = state.top10SectorDetails.get(sectorId);
    if (cached) {
        renderSectorDetail(cached);
        loadSectorDayTrack(sectorId);
    } else if (loadIfMissing) {
        await loadSectorDetail(sectorId, selection.selectedCode, false, {activateTab: false});
    } else {
        renderPendingTop10Sector(selection);
    }
}

function renderPendingTop10Sector(sector) {
    setText('sector-detail-title', `${sector.name || sector.id} · 龙头 Top10 分时强度`);
    setText('sector-detail-meta', '已加入刷新队列，点击“刷新所选板块”获取数据。');
    setText('sector-formula', '');
    setText('sector-detail-source', '');
    toggle('sector-selected-summary', false);
    toggle('sector-strength-table-wrap', false);
    toggle('sector-day-track', false);
    toggle('sector-detail-empty', true);
    const empty = document.getElementById('sector-detail-empty');
    empty.replaceChildren(
        createElement('strong', '', '等待刷新该板块'),
        createElement('p', '', '可以继续选择其他板块，然后一次刷新全部所选板块。')
    );
}

function renderEmptyTop10Selection() {
    setText('sector-detail-title', '板块龙头 Top10 分时强度');
    setText('sector-detail-meta', '请选择一个或多个板块，可在同一轮中依次刷新。');
    setText('sector-formula', '');
    setText('sector-detail-source', '');
    toggle('sector-selected-summary', false);
    toggle('sector-strength-table-wrap', false);
    toggle('sector-day-track', false);
    toggle('sector-detail-empty', true);
    const empty = document.getElementById('sector-detail-empty');
    empty.replaceChildren(
        createElement('strong', '', '先选择板块'),
        createElement('p', '', '点击“选择多个板块”，或从板块主线、行情搜索进入。')
    );
}

function acceptTop10SectorResponse(response, options = {}) {
    if (!response || !response.sector || !response.sector.id) return;
    const sectorId = response.sector.id;
    const requestedSectorId = options.requestedSectorId;
    if (requestedSectorId && requestedSectorId !== sectorId) {
        const previous = state.top10SelectedSectors.get(requestedSectorId);
        state.top10SelectedSectors.delete(requestedSectorId);
        state.top10SectorDetails.delete(requestedSectorId);
        if (state.currentSectorId === requestedSectorId) state.currentSectorId = sectorId;
        if (previous && !options.selectedCode) options.selectedCode = previous.selectedCode;
    }
    const selection = addTop10SectorSelection(
        response.sector,
        options.selectedCode || response.selectedCode || null,
        options.activate !== false
    );
    state.top10SectorDetails.set(sectorId, response);
    const watchedSector = state.sectorWatchlist.get(sectorId);
    if (watchedSector) {
        state.sectorWatchlist.set(sectorId, {
            ...watchedSector,
            sectorName: response.sector.name || watchedSector.sectorName,
            sectorType: response.sector.type || watchedSector.sectorType,
            lastRefreshedAt: new Date().toISOString()
        });
        renderSectorWatchlist();
    }
    if (selection) selection.selectedCode = options.selectedCode || response.selectedCode || selection.selectedCode;
    state.top10LastRefreshAt = response.fetchedAt || new Date().toISOString();
    if (options.render !== false && state.currentSectorId === sectorId) renderSectorDetail(response);
    if (options.render !== false && state.currentSectorId === sectorId) loadSectorDayTrack(sectorId);
    renderTop10SelectedSectors();
    updateTop10AutoStatus();
}

async function loadSectorDayTrack(sectorId) {
    const cached = state.sectorDayTracks.get(sectorId);
    if (cached) {
        renderSectorDayTrack(cached);
        return;
    }
    state.sectorDayTrackLoading = true;
    if (state.currentSectorId === sectorId) {
        toggle('sector-day-track', true);
        setText('sector-day-track-meta', '正在加载当天个股排名轨迹...');
    }
    try {
        const date = localDateValue(new Date());
        const response = await api(`/api/sectors/ranking-logs?date=${date}&sectorId=${encodeURIComponent(sectorId)}`);
        state.sectorDayTracks.set(sectorId, response);
        if (state.currentSectorId === sectorId) renderSectorDayTrack(response);
    } catch (error) {
        if (state.currentSectorId === sectorId) {
            toggle('sector-day-track', true);
            setText('sector-day-track-meta', '当天排名轨迹暂不可用');
        }
    } finally {
        state.sectorDayTrackLoading = false;
    }
}

function renderSectorDayTrack(response) {
    const sectors = response && response.sectors ? response.sectors : [];
    const sector = sectors[0];
    const body = document.getElementById('sector-day-track-body');
    body.replaceChildren();
    toggle('sector-day-track', Boolean(sector));
    if (!sector) {
        setText('sector-day-track-meta', '当天还没有该板块的排名快照');
        return;
    }
    setText('sector-day-track-meta', `${sector.sectorName} · 当天刷新 ${sector.refreshCount || 0} 次 · 最新一次在左侧`);
    const signalHistory = buildRankingSignalHistory(response.snapshots || []);
    const table = document.createElement('table');
    table.className = 'stock-table sector-day-track-table';
    const head = document.createElement('thead');
    const headRow = document.createElement('tr');
    ['股票', '总分', '最新1/3/5分钟', '当天名次轨迹'].forEach((label) => headRow.append(createElement('th', '', label)));
    head.append(headRow);
    const rows = document.createElement('tbody');
    (sector.stocks || []).forEach((stock) => {
        const row = document.createElement('tr');
        const identity = createElement('div', 'table-stock-identity');
        identity.append(createElement('strong', '', stock.stockName), createElement('span', 'stock-code', stock.fullCode));
        const identityCell = document.createElement('td');
        identityCell.append(identity);
        row.append(identityCell);
        row.append(numberCell(Number.isFinite(stock.totalScore) ? stock.totalScore.toFixed(1) : '--'));
        row.append(createRankingMinuteCell(stock.latestReturn1m, stock.latestReturn3m, stock.latestReturn5m, stock.latestLimitUp));
        const historyKey = `${sector.sectorId}\u0000${stock.fullCode}`;
        const historyCell = createRankingHistoryCell(
            stock.rankHistory || [], stock.rankTimeHistory || [], signalHistory.get(historyKey) || [],
            stock.rankingReasonHistory || [], false);
        row.append(historyCell);
        rows.append(row);
    });
    table.append(head, rows);
    body.append(table);
}

function readTop10RefreshSeconds() {
    const input = document.getElementById('top10-interval-seconds');
    const seconds = Number.parseInt(input.value, 10);
    if (!Number.isInteger(seconds) || seconds < 5 || seconds > 3600) {
        showToast('定时刷新间隔需设置为 5 到 3600 秒');
        input.focus();
        return null;
    }
    input.value = String(seconds);
    return seconds;
}

async function startTop10AutoRefresh() {
    if (state.top10SelectedSectors.size === 0) {
        showToast('请先选择至少一个板块');
        return;
    }
    const seconds = readTop10RefreshSeconds();
    if (seconds === null) return;

    stopTop10AutoRefresh(true);
    state.top10AutoRefreshSeconds = seconds;
    window.localStorage.setItem('stockLens.top10IntervalSeconds', String(seconds));
    state.top10AutoRefreshActive = true;
    syncTop10RefreshControls();
    updateTop10AutoStatus();
    await runTop10AutoRefresh();
}

async function runTop10AutoRefresh() {
    if (!state.top10AutoRefreshActive) return;
    const tradingSession = getTop10TradingSession();
    if (!tradingSession.open) {
        scheduleTop10AutoRefresh(tradingSession.waitMs);
        updateTop10AutoStatus();
        return;
    }
    await refreshSelectedTop10Sectors({automatic: true});
    if (state.top10AutoRefreshActive) scheduleTop10AutoRefresh();
}

async function refreshSelectedTop10Sectors(options = {}) {
    if (state.top10RefreshInProgress || state.sectorDetailLoading) return false;
    const selections = [...state.top10SelectedSectors.values()].map((selection) => ({...selection}));
    if (selections.length === 0) {
        if (!options.automatic) showToast('请先选择至少一个板块');
        return false;
    }

    if (!options.automatic && state.top10AutoRefreshActive) {
        window.clearTimeout(state.top10AutoRefreshTimer);
        state.top10AutoRefreshTimer = null;
    }

    state.top10RefreshInProgress = true;
    state.sectorDetailLoading = true;
    state.top10RefreshProgress = {current: 0, total: selections.length, name: ''};
    toggle('sector-detail-loading', true);
    syncTop10RefreshControls();
    renderTop10SectorOptions();
    renderTop10SelectedSectors();
    let successCount = 0;
    const failedNames = [];

    try {
        for (let index = 0; index < selections.length; index += 1) {
            const selection = selections[index];
            state.top10RefreshProgress = {
                current: index + 1,
                total: selections.length,
                name: selection.name
            };
            setText(
                'sector-detail-loading-text',
                `正在刷新 ${index + 1}/${selections.length} · ${selection.name}，成功结果将自动写入排名日志...`
            );
            updateTop10AutoStatus();
            const selectedQuery = selection.selectedCode
                ? `&selected=${encodeURIComponent(selection.selectedCode)}`
                : '';
            try {
                const response = await api(
                    `/api/sectors/${encodeURIComponent(selection.id)}?refresh=true${selectedQuery}`
                );
                if (response.available === false) {
                    throw new Error(response.message || '板块强度数据暂不可用');
                }
                acceptTop10SectorResponse(response, {
                    selectedCode: selection.selectedCode,
                    requestedSectorId: selection.id,
                    activate: false,
                    render: state.currentSectorId === selection.id || state.currentSectorId === response.sector.id
                });
                successCount += 1;
            } catch (error) {
                failedNames.push(selection.name);
            }
        }
        state.top10LastRound = {
            success: successCount,
            total: selections.length,
            failed: failedNames,
            completedAt: new Date().toISOString()
        };
        if (!options.automatic) {
            if (successCount === selections.length) {
                showToast(`已刷新 ${successCount} 个板块，结果已写入排名日志`);
            } else {
                showToast(`本轮成功 ${successCount}/${selections.length}，失败：${failedNames.join('、')}`);
            }
        }
        return successCount > 0;
    } finally {
        state.top10RefreshProgress = null;
        state.top10RefreshInProgress = false;
        state.sectorDetailLoading = false;
        toggle('sector-detail-loading', false);
        syncTop10RefreshControls();
        renderTop10SectorOptions();
        renderTop10SelectedSectors();
        updateTop10AutoStatus();
        if (!options.automatic && state.top10AutoRefreshActive) scheduleTop10AutoRefresh();
    }
}

function scheduleTop10AutoRefresh(delayMs = null) {
    window.clearTimeout(state.top10AutoRefreshTimer);
    if (!state.top10AutoRefreshActive) return;
    const tradingSession = getTop10TradingSession();
    const nextDelay = delayMs == null
        ? (tradingSession.open ? state.top10AutoRefreshSeconds * 1000 : tradingSession.waitMs)
        : delayMs;
    state.top10AutoRefreshTimer = window.setTimeout(
        runTop10AutoRefresh,
        Math.max(1000, nextDelay)
    );
}

function getTop10TradingSession(now = new Date()) {
    const parts = Object.fromEntries(
        new Intl.DateTimeFormat('en-US', {
            timeZone: 'Asia/Shanghai',
            weekday: 'short',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hourCycle: 'h23'
        }).formatToParts(now)
            .filter((part) => part.type !== 'literal')
            .map((part) => [part.type, part.value])
    );
    const weekday = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].indexOf(parts.weekday);
    const secondOfDay = Number(parts.hour) * 3600 + Number(parts.minute) * 60 + Number(parts.second);
    const morningOpen = 9 * 3600 + 15 * 60;
    const morningClose = 11 * 3600 + 30 * 60;
    const afternoonOpen = 13 * 3600;
    const afternoonClose = 15 * 3600;
    const weekdayOpen = weekday >= 1 && weekday <= 5;
    const open = weekdayOpen && (
        (secondOfDay >= morningOpen && secondOfDay <= morningClose)
        || (secondOfDay >= afternoonOpen && secondOfDay <= afternoonClose)
    );
    if (open) return {open: true, waitMs: 0, nextLabel: ''};

    let daysAhead = 0;
    let targetSecond = morningOpen;
    let nextLabel = '下个交易日 09:15';
    if (weekdayOpen && secondOfDay < morningOpen) {
        nextLabel = '今日 09:15';
    } else if (weekdayOpen && secondOfDay > morningClose && secondOfDay < afternoonOpen) {
        targetSecond = afternoonOpen;
        nextLabel = '今日 13:00';
    } else {
        daysAhead = weekday === 5 ? 3 : weekday === 6 ? 2 : weekday === 0 ? 1 : 1;
    }
    const waitSeconds = daysAhead * 24 * 3600 + targetSecond - secondOfDay;
    return {
        open: false,
        waitMs: Math.max(1000, waitSeconds * 1000),
        nextLabel
    };
}

function stopTop10AutoRefresh(silent = false) {
    window.clearTimeout(state.top10AutoRefreshTimer);
    state.top10AutoRefreshTimer = null;
    state.top10AutoRefreshActive = false;
    syncTop10RefreshControls();
    updateTop10AutoStatus();
    if (!silent) showToast('Top10 定时刷新已停止');
}

function syncTop10RefreshControls() {
    const hasSector = state.top10SelectedSectors.size > 0;
    document.getElementById('refresh-sector-detail').disabled = !hasSector || state.sectorDetailLoading;
    document.getElementById('start-top10-auto-refresh').disabled = !hasSector
        || state.top10AutoRefreshActive
        || state.sectorDetailLoading;
    document.getElementById('stop-top10-auto-refresh').disabled = !state.top10AutoRefreshActive;
    document.getElementById('top10-interval-seconds').disabled = state.top10AutoRefreshActive;
    document.getElementById('clear-top10-sectors').disabled = !hasSector || state.sectorDetailLoading;
    document.getElementById('save-top10-sector-watchlist').disabled = !hasSector || state.sectorDetailLoading;
}

function updateTop10AutoStatus() {
    const status = document.getElementById('top10-auto-status');
    if (state.top10RefreshProgress) {
        const progress = state.top10RefreshProgress;
        status.textContent = `正在刷新 ${progress.current}/${progress.total} · ${progress.name}`;
        status.classList.remove('hidden');
        return;
    }
    if (!state.top10AutoRefreshActive) {
        status.classList.add('hidden');
        return;
    }
    const tradingSession = getTop10TradingSession();
    const lastRound = state.top10LastRound
        ? `上轮成功 ${state.top10LastRound.success}/${state.top10LastRound.total} · 完成 ${formatRefreshClock(state.top10LastRound.completedAt)}`
        : '等待首次更新';
    status.textContent = tradingSession.open
        ? `定时刷新运行中 · 已选 ${state.top10SelectedSectors.size} 个板块 · 每 ${state.top10AutoRefreshSeconds} 秒一轮 · ${lastRound}`
        : `定时刷新已暂停 · 当前为非刷新时段 · ${tradingSession.nextLabel} 自动恢复 · ${lastRound}`;
    status.classList.remove('hidden');
}

function formatRefreshClock(value) {
    if (!value) return '--';
    const text = String(value).replace('T', ' ');
    return text.length >= 19 ? text.slice(11, 19) : text;
}

function renderSectorDetail(response) {
    const stocks = response.stocks || [];
    toggle('sector-detail-empty', stocks.length === 0);
    toggle('sector-strength-table-wrap', stocks.length > 0);
    if (response.sector) {
        setText('sector-detail-title', `${response.sector.name} · 龙头 Top10 分时强度`);
        setText('sector-detail-meta', `${response.sector.type || '板块'} · ${response.sector.companyCount || '--'} 家公司 · 展示 ${stocks.length} 只 · 今日 ${formatPercent(response.sector.changePercent)} · 主线评分 ${formatScore(response.sector.score)}`);
    } else {
        setText('sector-detail-title', '板块龙头分时强度');
        setText('sector-detail-meta', '暂未识别所属板块');
    }
    setText('sector-formula', response.formula || '');

    const selected = stocks.find((stock) => stock.selected);
    const selectedSummary = document.getElementById('sector-selected-summary');
    selectedSummary.replaceChildren();
    if (selected) {
        const score = createElement('strong', '', `${formatScore(selected.score)} 分`);
        setTrendClass(score, selected.score - 50);
        selectedSummary.append(
            createElement('span', '', `所选个股 ${selected.name}`),
            createElement('span', '', `板块排名 ${selected.rank}/${stocks.length}`),
            score,
            createElement('span', '', selected.strengthLabel || '')
        );
        selectedSummary.classList.remove('hidden');
    } else {
        selectedSummary.classList.add('hidden');
    }

    const body = document.getElementById('sector-strength-body');
    body.replaceChildren();
    stocks.forEach((stock) => body.append(createStrengthRow(stock)));

    const warnings = response.warnings && response.warnings.length
        ? ` · ${response.warnings.join('；')}`
        : '';
    setText('sector-detail-source', `${response.source || ''} · 更新时间 ${formatDataDateTime(response.fetchedAt)}${warnings}`);
}

async function loadRankingLogs(target = 'logs', options = {}) {
    const trajectoryTarget = target === 'trajectory';
    const dateInput = document.getElementById(trajectoryTarget ? 'ranking-trajectory-date' : 'ranking-log-date');
    if (!dateInput.value) dateInput.value = localDateValue(new Date());
    const otherDateInput = document.getElementById(trajectoryTarget ? 'ranking-log-date' : 'ranking-trajectory-date');
    if (otherDateInput) otherDateInput.value = dateInput.value;
    const prefix = trajectoryTarget ? 'ranking-trajectory' : 'ranking-log';
    if (!options.silent) {
        toggle(`${prefix}-loading`, true);
        toggle(`${prefix}-empty`, false);
        toggle(`${prefix}-content`, false);
    }
    const refreshButton = document.getElementById(trajectoryTarget ? 'refresh-ranking-trajectory' : 'refresh-ranking-logs');
    refreshButton.disabled = true;
    try {
        const response = await api(`/api/sectors/ranking-logs?date=${encodeURIComponent(dateInput.value)}`);
        try {
            state.watchlistItems = await api('/api/watchlist') || [];
        } catch (ignored) {
            // Ranking logs remain usable when the watchlist quote service is unavailable.
        }
        renderRankingLogs(response, target);
    } catch (error) {
        if (!options.silent) toggle(`${prefix}-empty`, true);
        if (!options.silent) showToast(error.message);
    } finally {
        if (!options.silent) toggle(`${prefix}-loading`, false);
        refreshButton.disabled = false;
    }
}

function startRankingLogAutoRefresh() {
    const input = document.getElementById('ranking-log-refresh-seconds');
    const seconds = Math.max(3, Math.min(3600, Number(input.value) || 10));
    input.value = String(seconds);
    state.rankingLogAutoRefreshSeconds = seconds;
    state.rankingLogAutoRefreshActive = true;
    syncRankingLogAutoRefreshControls();
    scheduleRankingLogAutoRefresh(0);
}

function stopRankingLogAutoRefresh() {
    state.rankingLogAutoRefreshActive = false;
    window.clearTimeout(state.rankingLogAutoRefreshTimer);
    state.rankingLogAutoRefreshTimer = null;
    syncRankingLogAutoRefreshControls();
}

function scheduleRankingLogAutoRefresh(delaySeconds = state.rankingLogAutoRefreshSeconds) {
    window.clearTimeout(state.rankingLogAutoRefreshTimer);
    if (!state.rankingLogAutoRefreshActive) return;
    state.rankingLogAutoRefreshTimer = window.setTimeout(async () => {
        try {
            await loadRankingLogs('logs', {silent: true});
        } finally {
            scheduleRankingLogAutoRefresh();
        }
    }, Math.max(0, delaySeconds) * 1000);
}

function syncRankingLogAutoRefreshControls() {
    const input = document.getElementById('ranking-log-refresh-seconds');
    const start = document.getElementById('start-ranking-log-auto-refresh');
    const stop = document.getElementById('stop-ranking-log-auto-refresh');
    input.disabled = state.rankingLogAutoRefreshActive;
    start.disabled = state.rankingLogAutoRefreshActive;
    stop.disabled = !state.rankingLogAutoRefreshActive;
    start.textContent = state.rankingLogAutoRefreshActive
        ? `定时刷新中 · ${state.rankingLogAutoRefreshSeconds}秒`
        : '开始定时刷新';
}

function renderRankingLogs(response, target = 'logs') {
    const sectors = response.sectors || [];
    const snapshots = response.snapshots || [];
    const hasLogs = snapshots.length > 0;
    const trajectoryTarget = target === 'trajectory';
    const prefix = trajectoryTarget ? 'ranking-trajectory' : 'ranking-log';
    toggle(`${prefix}-empty`, !hasLogs);
    toggle(`${prefix}-content`, hasLogs);
    if (!hasLogs) return;

    state.rankingLogResponse = response;
    if (!sectors.some((sector) => sector.sectorId === state.rankingLogSelectedSectorId)) {
        state.rankingLogSelectedSectorId = sectors[0] ? sectors[0].sectorId : null;
    }
    if (!sectors.some((sector) => sector.sectorId === state.rankingTrajectorySelectedSectorId)) {
        state.rankingTrajectorySelectedSectorId = sectors[0] ? sectors[0].sectorId : null;
    }

    const overview = document.getElementById(trajectoryTarget
        ? 'ranking-trajectory-overview'
        : 'ranking-log-overview');
    overview.replaceChildren();
    overview.append(
        rankingOverviewItem('交易日期', response.date || '--'),
        rankingOverviewItem('累计刷新', `${response.refreshCount || 0} 次`),
        rankingOverviewItem('记录板块', `${sectors.length} 个`),
        rankingOverviewItem('排名记录', `${snapshots.reduce((sum, item) => sum + (item.stocks || []).length, 0)} 条`)
    );

    if (trajectoryTarget) renderRankingTrajectoryView();
    else renderRankingLogSectorView();
}

function renderRankingLogSectorView() {
    const response = state.rankingLogResponse;
    if (!response) return;
    const sectors = response.sectors || [];
    const selectedSector = sectors.find((sector) => sector.sectorId === state.rankingLogSelectedSectorId) || sectors[0];

    const tabs = document.getElementById('ranking-log-sector-tabs');
    tabs.replaceChildren();
    sectors.forEach((sector) => {
        const tab = createElement('button', `ranking-log-sector-tab${sector === selectedSector ? ' active' : ''}`, sector.sectorName || '--');
        tab.type = 'button';
        tab.title = `${sector.refreshCount || 0} 次刷新`;
        tab.addEventListener('click', () => {
            state.rankingLogSelectedSectorId = sector.sectorId;
            renderRankingLogSectorView();
        });
        tabs.append(tab);
    });

    state.rankingHistoryExpanded = false;
    state.rankingHistoryControllers = [];
    updateRankingHistoryToggle();
    const signalHistory = buildRankingSignalHistory(response.snapshots || []);
    const statistics = document.getElementById('ranking-statistics');
    statistics.replaceChildren();
    if (selectedSector) statistics.append(createSectorRankingStatistics(selectedSector, signalHistory));
}

function renderRankingTrajectoryView() {
    const response = state.rankingLogResponse;
    if (!response) return;
    const sectors = response.sectors || [];
    const snapshots = response.snapshots || [];
    const selectedSector = sectors.find((sector) => sector.sectorId === state.rankingTrajectorySelectedSectorId) || sectors[0];
    const tabs = document.getElementById('ranking-trajectory-sector-tabs');
    tabs.replaceChildren();
    sectors.forEach((sector) => {
        const tab = createElement('button', `ranking-log-sector-tab${sector === selectedSector ? ' active' : ''}`, sector.sectorName || '--');
        tab.type = 'button';
        tab.title = `${sector.refreshCount || 0} 次刷新`;
        tab.addEventListener('click', () => {
            state.rankingTrajectorySelectedSectorId = sector.sectorId;
            renderRankingTrajectoryView();
        });
        tabs.append(tab);
    });
    const chart = document.getElementById('ranking-trajectory-chart-container');
    chart.replaceChildren();
    if (selectedSector) chart.append(createRankingTrajectoryChart(selectedSector));
    const snapshotBody = document.getElementById('ranking-snapshot-body');
    snapshotBody.replaceChildren();
    snapshots
        .filter((snapshot) => !selectedSector || snapshot.sectorId === selectedSector.sectorId)
        .forEach((snapshot) => snapshotBody.append(createRankingSnapshotRow(snapshot)));
}

function toggleAllRankingHistory() {
    state.rankingHistoryExpanded = !state.rankingHistoryExpanded;
    state.rankingHistoryControllers.forEach((controller) => controller.setExpanded(state.rankingHistoryExpanded));
    updateRankingHistoryToggle();
}

function updateRankingHistoryToggle() {
    const button = document.getElementById('toggle-ranking-history');
    if (button) button.textContent = state.rankingHistoryExpanded ? '全部折叠' : '全部展开';
}

function buildRankingSignalHistory(snapshots) {
    const history = new Map();
    const bySector = new Map();
    snapshots.forEach((snapshot) => {
        if (!bySector.has(snapshot.sectorId)) bySector.set(snapshot.sectorId, []);
        bySector.get(snapshot.sectorId).push(snapshot);
    });
    bySector.forEach((sectorSnapshots, sectorId) => {
        const ordered = [...sectorSnapshots]
            .sort((left, right) => String(left.capturedAt || '').localeCompare(String(right.capturedAt || '')));
        const codes = new Set();
        ordered.forEach((snapshot) => (snapshot.stocks || []).forEach((stock) => codes.add(stock.fullCode)));
        codes.forEach((code) => {
            const key = `${sectorId}\u0000${code}`;
            history.set(key, ordered.map((snapshot) => {
                const stock = (snapshot.stocks || []).find((item) => item.fullCode === code);
                return stock ? stock.signal || null : null;
            }));
        });
    });
    return history;
}

function rankingOverviewItem(label, value) {
    const item = createElement('div', 'ranking-overview-item');
    item.append(createElement('span', '', label), createElement('strong', '', value));
    return item;
}

function createSectorRankingStatistics(sector, signalHistory) {
    const section = createElement('section', 'ranking-sector-block');
    const heading = createElement('div', 'ranking-sector-heading');
    const title = createElement('div');
    title.append(
        createElement('h3', '', sector.sectorName || '--'),
        createElement('p', '', `当天刷新 ${sector.refreshCount || 0} 次 · 可按最新名次、前3次数、第一次数或总分排序`)
    );
    heading.append(title);
    section.append(heading);

    const wrap = createElement('div', 'stock-table-wrap');
    const table = createElement('table', 'stock-table ranking-stat-table');
    const thead = document.createElement('thead');
    const headRow = document.createElement('tr');
    headRow.append(createElement('th', '', '股票'));
    const latestChangeHeader = createElement('th', 'number-cell', '最新涨跌幅');
    headRow.append(latestChangeHeader);
    headRow.append(createRankingSortHeader('最新名次', 'latestRank'));
    headRow.append(createRankingSortHeader('前3次数', 'topThreeCount'));
    headRow.append(createRankingSortHeader('第一次数', 'firstCount'));
    headRow.append(createRankingSortHeader('总分', 'totalScore'));
    headRow.append(createElement('th', 'number-cell', '最新1/3/5分钟'));
    headRow.append(createElement('th', '', '名次轨迹'));
    thead.append(headRow);
    const tbody = document.createElement('tbody');
    sortedRankingStocks(sector.stocks || []).forEach((stock) => {
        const key = `${sector.sectorId}\u0000${stock.fullCode}`;
        tbody.append(createRankingStatisticRow(stock, signalHistory.get(key) || []));
    });
    table.append(thead, tbody);
    wrap.append(table);
    section.append(wrap);
    return section;
}

function createRankingTrajectoryChart(sector) {
    const chartSection = createElement('section', 'ranking-trajectory-chart');
    const heading = createElement('div', 'ranking-trajectory-heading');
    heading.append(
        createElement('h4', '', '名次轨迹图'),
        createElement('span', '', '横轴为采集时间，纵轴为板块内名次；空白点表示该时间未采集到个股')
    );
    chartSection.append(heading);

    const stocks = (sector.stocks || []).filter((stock) =>
        Array.isArray(stock.rankHistory) && stock.rankHistory.length > 0);
    if (stocks.length === 0) {
        chartSection.append(createElement('p', 'ranking-trajectory-empty', '暂无可绘制的名次轨迹'));
        return chartSection;
    }

    const timelineStock = stocks.reduce((current, stock) =>
        (stock.rankHistory.length > (current?.rankHistory?.length || 0) ? stock : current), null);
    const timeline = timelineStock.rankTimeHistory || [];
    const pointCount = Math.max(1, ...stocks.map((stock) => stock.rankHistory.length));
    const maxRank = Math.max(10, ...stocks.flatMap((stock) =>
        stock.rankHistory.filter((rank) => Number.isFinite(rank))));
    const colors = ['#126b5b', '#c93632', '#b7791f', '#6b4fb3', '#087ea4', '#d14d72', '#4b7f52', '#d2691e', '#536878', '#8b3a62'];
    const selected = new Set(stocks.map((stock) => stock.fullCode));
    let windowStart = 0;
    let windowEnd = Math.max(0, pointCount - 1);
    const plotHost = createElement('div', 'ranking-trajectory-plot');
    const controls = createElement('div', 'ranking-trajectory-controls');
    const rangeLabel = createElement('span', 'ranking-trajectory-range-label');
    const startRange = document.createElement('input');
    const endRange = document.createElement('input');
    [startRange, endRange].forEach((range) => {
        range.type = 'range';
        range.min = '0';
        range.max = String(Math.max(0, pointCount - 1));
        range.step = '1';
        range.setAttribute('aria-label', '调整名次轨迹时间范围');
    });
    startRange.value = '0';
    endRange.value = String(windowEnd);
    const selectAll = createElement('button', 'ranking-trajectory-control', '全选');
    const clearAll = createElement('button', 'ranking-trajectory-control', '清空');
    selectAll.type = 'button';
    clearAll.type = 'button';
    controls.append(
        createElement('span', 'ranking-trajectory-control-label', '时间范围'),
        createElement('span', 'ranking-trajectory-bound-label', '起'),
        startRange,
        createElement('span', 'ranking-trajectory-bound-label', '止'),
        endRange,
        rangeLabel,
        selectAll,
        clearAll
    );
    chartSection.append(controls, plotHost);

    const render = () => {
        plotHost.replaceChildren();
        const end = Math.min(pointCount - 1, Math.max(windowStart, windowEnd));
        const visibleCount = Math.max(1, end - windowStart + 1);
        const width = Math.max(1280, visibleCount * 34);
        const height = 620;
        const plot = {left: 58, right: 24, top: 24, bottom: 58};
        const plotWidth = width - plot.left - plot.right;
        const plotHeight = height - plot.top - plot.bottom;
        const xAt = (index) => plot.left + (visibleCount <= 1 ? plotWidth / 2 : index / (visibleCount - 1) * plotWidth);
        const yAt = (rank) => plot.top + (rank - 1) / Math.max(1, maxRank - 1) * plotHeight;
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
        svg.setAttribute('width', String(width));
        svg.setAttribute('height', String(height));
        svg.setAttribute('preserveAspectRatio', 'none');
        svg.setAttribute('role', 'img');
        svg.setAttribute('aria-label', `${sector.sectorName || '板块'}个股名次轨迹图`);
        const appendSvg = (tag, attributes, textValue = '') => {
            const element = document.createElementNS('http://www.w3.org/2000/svg', tag);
            Object.entries(attributes).forEach(([name, value]) => element.setAttribute(name, String(value)));
            if (textValue) element.textContent = textValue;
            svg.append(element);
            return element;
        };
        for (let rank = 1; rank <= maxRank; rank += 1) {
            const y = yAt(rank);
            appendSvg('line', {x1: plot.left, y1: y, x2: width - plot.right, y2: y, class: 'ranking-chart-grid'});
            appendSvg('text', {x: plot.left - 12, y: y + 4, class: 'ranking-chart-y-label', 'text-anchor': 'end'}, `#${rank}`);
        }
        appendSvg('line', {x1: plot.left, y1: plot.top, x2: plot.left, y2: height - plot.bottom, class: 'ranking-chart-axis'});
        appendSvg('line', {x1: plot.left, y1: height - plot.bottom, x2: width - plot.right, y2: height - plot.bottom, class: 'ranking-chart-axis'});
        appendSvg('text', {x: 14, y: plot.top - 8, class: 'ranking-chart-axis-label'}, '排名');
        appendSvg('text', {x: width - plot.right, y: height - 5, class: 'ranking-chart-axis-label', 'text-anchor': 'end'}, '时间');
        const labelStep = Math.max(1, Math.ceil(visibleCount / 12));
        for (let localIndex = 0; localIndex < visibleCount; localIndex += labelStep) {
            const index = windowStart + localIndex;
            const x = xAt(localIndex);
            const time = timeline[index] || `第${index + 1}次`;
            appendSvg('line', {x1: x, y1: height - plot.bottom, x2: x, y2: height - plot.bottom + 5, class: 'ranking-chart-axis'});
            appendSvg('text', {x, y: height - 22, class: 'ranking-chart-x-label', 'text-anchor': 'middle'}, time);
        }
        stocks.forEach((stock, stockIndex) => {
            if (!selected.has(stock.fullCode)) return;
            const color = colors[stockIndex % colors.length];
            const pathParts = [];
            let segment = [];
            const reasons = stock.rankingReasonHistory || [];
            for (let index = windowStart; index <= end; index += 1) {
                const rank = stock.rankHistory[index];
                if (!Number.isFinite(rank)) {
                    if (segment.length > 0) pathParts.push(segment);
                    segment = [];
                    continue;
                }
                segment.push({x: xAt(index - windowStart), y: yAt(rank)});
            }
            if (segment.length > 0) pathParts.push(segment);
            const smoothPath = (points) => {
                if (points.length === 0) return '';
                if (points.length === 1) return `M ${points[0].x} ${points[0].y}`;
                let d = `M ${points[0].x} ${points[0].y}`;
                for (let index = 1; index < points.length; index += 1) {
                    const previous = points[index - 1];
                    const current = points[index];
                    const midX = (previous.x + current.x) / 2;
                    d += ` Q ${midX} ${previous.y} ${midX} ${(previous.y + current.y) / 2}`;
                    d += ` Q ${midX} ${current.y} ${current.x} ${current.y}`;
                }
                return d;
            };
            pathParts.forEach((points) => appendSvg('path', {d: smoothPath(points), class: 'ranking-chart-line', stroke: color}));
            for (let index = windowStart; index <= end; index += 1) {
                const rank = stock.rankHistory[index];
                if (!Number.isFinite(rank)) continue;
                const circle = appendSvg('circle', {
                    cx: xAt(index - windowStart), cy: yAt(rank), r: 2.5, fill: color, class: 'ranking-chart-point'
                });
                const title = document.createElementNS('http://www.w3.org/2000/svg', 'title');
                const time = (stock.rankTimeHistory || [])[index] || timeline[index] || `第${index + 1}次`;
                title.textContent = `${stock.stockName || stock.fullCode} · ${time} · 第${rank}名` +
                    (reasons[index] ? `\n${reasons[index]}` : '');
                circle.append(title);
            }
        });
        plotHost.append(svg);
        rangeLabel.textContent = `${timeline[windowStart] || `第${windowStart + 1}次`} - ${timeline[end] || `第${end + 1}次`}`;
        renderLegend();
    };
    const legend = createElement('div', 'ranking-trajectory-legend');
    const renderLegend = () => {
        legend.replaceChildren();
        stocks.forEach((stock, stockIndex) => {
            const color = colors[stockIndex % colors.length];
            const item = createElement('button', `ranking-trajectory-legend-item${selected.has(stock.fullCode) ? ' is-active' : ''}`);
            item.type = 'button';
            item.setAttribute('aria-pressed', String(selected.has(stock.fullCode)));
            item.title = selected.size === 1 && selected.has(stock.fullCode)
                ? '点击恢复显示全部股票'
                : '点击只查看这只股票';
            const swatch = createElement('i', 'ranking-trajectory-swatch');
            swatch.style.backgroundColor = color;
            item.append(swatch, createElement('strong', '', stock.stockName || stock.fullCode));
            item.addEventListener('click', () => {
                if (selected.size === 1 && selected.has(stock.fullCode)) {
                    stocks.forEach((current) => selected.add(current.fullCode));
                } else {
                    selected.clear();
                    selected.add(stock.fullCode);
                }
                render();
            });
            legend.append(item);
        });
        if (!legend.parentElement) chartSection.append(legend);
    };
    selectAll.addEventListener('click', () => {
        stocks.forEach((stock) => selected.add(stock.fullCode));
        render();
    });
    clearAll.addEventListener('click', () => {
        selected.clear();
        render();
    });
    startRange.addEventListener('input', () => {
        windowStart = Math.min(Number(startRange.value) || 0, windowEnd);
        startRange.value = String(windowStart);
        render();
    });
    endRange.addEventListener('input', () => {
        windowEnd = Math.max(Number(endRange.value) || 0, windowStart);
        endRange.value = String(windowEnd);
        render();
    });
    chartSection.append(legend);
    render();
    return chartSection;
}

function latestRecordedRank(stock) {
    const history = stock.rankHistory || [];
    for (let index = history.length - 1; index >= 0; index -= 1) {
        if (Number.isFinite(history[index])) return history[index];
    }
    return Number.POSITIVE_INFINITY;
}

function sortedRankingStocks(stocks) {
    const direction = state.rankingStatisticsSortDirection === 'desc' ? -1 : 1;
    return [...stocks].sort((left, right) => {
        const leftRank = latestRecordedRank(left);
        const rightRank = latestRecordedRank(right);
        if (state.rankingStatisticsSortKey === 'latestRank'
            && Number.isFinite(leftRank) !== Number.isFinite(rightRank)) {
            return Number.isFinite(leftRank) ? -1 : 1;
        }
        let comparison;
        if (['totalScore', 'topThreeCount', 'firstCount'].includes(state.rankingStatisticsSortKey)) {
            const sortKey = state.rankingStatisticsSortKey;
            const leftValue = Number.isFinite(left[sortKey]) ? left[sortKey] : 0;
            const rightValue = Number.isFinite(right[sortKey]) ? right[sortKey] : 0;
            comparison = leftValue - rightValue;
        } else {
            comparison = leftRank - rightRank;
        }
        if (comparison !== 0 && Number.isFinite(comparison)) return comparison * direction;

        const rankComparison = leftRank - rightRank;
        if (rankComparison !== 0 && Number.isFinite(rankComparison)) return rankComparison;
        const scoreComparison = (right.totalScore || 0) - (left.totalScore || 0);
        if (scoreComparison !== 0) return scoreComparison;
        return String(left.stockName || '').localeCompare(String(right.stockName || ''), 'zh-CN');
    });
}

function createRankingSortHeader(label, sortKey) {
    const header = createElement('th', 'number-cell');
    const active = state.rankingStatisticsSortKey === sortKey;
    const icon = active ? (state.rankingStatisticsSortDirection === 'desc' ? '↓' : '↑') : '';
    const button = createElement('button', 'sort-button ranking-sort-button');
    button.type = 'button';
    button.setAttribute('aria-label', `按${label}排序`);
    button.setAttribute('aria-pressed', String(active));
    button.append(document.createTextNode(`${label} `), createElement('span', '', icon));
    button.addEventListener('click', () => {
        if (state.rankingStatisticsSortKey === sortKey) {
            state.rankingStatisticsSortDirection = state.rankingStatisticsSortDirection === 'desc' ? 'asc' : 'desc';
        } else {
            state.rankingStatisticsSortKey = sortKey;
            state.rankingStatisticsSortDirection =
                ['totalScore', 'topThreeCount', 'firstCount'].includes(sortKey) ? 'desc' : 'asc';
        }
        renderRankingLogSectorView();
    });
    header.append(button);
    return header;
}

function createRankingStatisticRow(stock, signalHistory = []) {
    const row = document.createElement('tr');
    const stockCell = document.createElement('td');
    const identity = createElement('div', 'table-stock-identity');
    const watchlistItem = findWatchlistItem(stock.fullCode);
    const traceSelected = hasRankingTraceSource(watchlistItem);
    const stockName = createElement('button', `ranking-stock-name${watchlistItem ? ' is-watched' : ''}`, stock.stockName);
    stockName.type = 'button';
    stockName.title = traceSelected
        ? '点击取消排名轨迹自选'
        : '点击加入自选（排名轨迹）';
    identity.append(stockName, createElement('span', 'stock-code', stock.fullCode));
    const watchStatus = createElement('small', watchlistItem ? 'ranking-watch-status is-watched' : 'ranking-watch-status',
        watchlistItem ? `已在自选 · ${watchlistSourceLabel(watchlistItem)}` : '未在自选');
    identity.append(watchStatus);
    if (!traceSelected || watchlistItem) {
        stockName.addEventListener('click', async () => {
            stockName.disabled = true;
            try {
                const item = traceSelected
                    ? await removeRankingTraceSource(stock.fullCode)
                    : await addStockToWatchlist(stock.fullCode, 'RANKING_TRACE');
                if (item) upsertWatchlistItem(item);
                else state.watchlistItems = state.watchlistItems.filter((current) =>
                    current.stock?.fullCode !== stock.fullCode);
                renderRankingLogSectorView();
                showToast(traceSelected
                    ? `${stock.stockName} 已取消排名轨迹自选`
                    : `${stock.stockName} 已加入自选（排名轨迹）`);
            } catch (error) {
                stockName.disabled = false;
                showToast(error.message);
            }
        });
    }
    stockCell.append(identity);
    row.append(stockCell);
    const dailyChangeCell = numberCell(stock.latestLimitUp ? '涨停' : formatPercent(stock.latestDailyChangePercent));
    if (!stock.latestLimitUp) setTrendClass(dailyChangeCell, stock.latestDailyChangePercent);
    dailyChangeCell.classList.add('ranking-latest-change');
    row.append(dailyChangeCell);
    const latestRank = latestRecordedRank(stock);
    const latestRankCell = numberCell(Number.isFinite(latestRank) ? `#${latestRank}` : '--');
    latestRankCell.classList.add('ranking-latest-rank');
    row.append(latestRankCell);
    const topThreeCountCell = numberCell(String(Number.isFinite(stock.topThreeCount) ? stock.topThreeCount : 0));
    topThreeCountCell.classList.add('ranking-top-three-count');
    row.append(topThreeCountCell);
    const firstCountCell = numberCell(String(Number.isFinite(stock.firstCount) ? stock.firstCount : 0));
    firstCountCell.classList.add('ranking-first-count');
    row.append(firstCountCell);
    const totalScoreCell = numberCell(Number.isFinite(stock.totalScore) ? stock.totalScore.toFixed(1) : '--');
    totalScoreCell.classList.add('ranking-total-score');
    row.append(totalScoreCell);
    row.append(createRankingMinuteCell(stock.latestReturn1m, stock.latestReturn3m, stock.latestReturn5m, stock.latestLimitUp));
    row.append(createRankingHistoryCell(
        stock.rankHistory || [], stock.rankTimeHistory || [], signalHistory,
        stock.rankingReasonHistory || []));
    return row;
}

function createRankingMinuteCell(return1m, return3m, return5m, limitUp = false) {
    const cell = document.createElement('td');
    const values = createElement('div', 'ranking-minute-values');
    if (limitUp) {
        ['1m', '3m', '5m'].forEach((label) => {
            const item = createElement('span', 'ranking-minute-value');
            item.append(createElement('small', '', label));
            item.append(createElement('strong', 'limit-up-metric', '\u5c01\u677f'));
            values.append(item);
        });
        cell.append(values);
        return cell;
    }
    [['1m', return1m], ['3m', return3m], ['5m', return5m]].forEach(([label, value]) => {
        const item = createElement('span', 'ranking-minute-value');
        item.append(createElement('small', '', label));
        const number = createElement('strong', '', limitUp && value == null ? '涨停' : formatPercent(value));
        if (!(limitUp && value == null)) setTrendClass(number, value);
        item.append(number);
        values.append(item);
    });
    cell.append(values);
    return cell;
}

function createRankingHistoryCell(rankHistory, rankTimeHistory, signalHistory,
                                  rankingReasonHistory = [], registerForBulkToggle = true) {
    const cell = document.createElement('td');
    const wrapper = createElement('div', 'rank-history-wrapper');
    const history = createElement('div', 'rank-history');
    let expanded = false;
    const render = () => {
        history.replaceChildren();
        const start = expanded ? 0 : Math.max(0, rankHistory.length - 35);
        for (let index = rankHistory.length - 1; index >= start; index -= 1) {
            const rank = rankHistory[index];
            const missing = rank == null;
            const rankClass = missing ? 'missing-rank' : rank <= 3 ? 'top-rank' : rank >= 8 ? 'bottom-rank' : '';
            const badge = createElement('span', rankClass, '');
            badge.append(
                createElement('small', 'rank-history-time', rankTimeHistory[index] || '--.--'),
                createElement('strong', 'rank-history-position', missing ? '-' : `#${rank}`)
            );
            if (!missing && signalHistory[index]) {
                badge.classList.add(signalHistory[index] === 'B' ? 'signal-buy' : 'signal-sell');
            }
            const reason = rankingReasonHistory[index];
            badge.title = missing
                ? '该时间点未检测到个股'
                : reason || `${rankTimeHistory[index] || '--.--'} 排名第 ${rank} 位；旧日志未保存完整指标`;
            history.append(badge);
        }
        const oldCount = Math.max(0, rankHistory.length - 35);
        if (oldCount > 0) {
            toggleButton.textContent = expanded ? '收起' : '…';
            toggleButton.title = expanded ? '收起较早记录' : `展开较早 ${oldCount} 条记录`;
            toggleButton.classList.remove('hidden');
            history.append(toggleButton);
        } else {
            toggleButton.classList.add('hidden');
        }
    };
    const toggleButton = createElement('button', 'history-toggle', '');
    toggleButton.type = 'button';
    toggleButton.addEventListener('click', () => {
        expanded = !expanded;
        render();
    });
    wrapper.append(history);
    cell.append(wrapper);
    if (registerForBulkToggle) {
        state.rankingHistoryControllers.push({
            setExpanded(value) {
                expanded = Boolean(value);
                render();
            }
        });
    }
    render();
    return cell;
}

function createRankingSnapshotRow(snapshot) {
    const row = document.createElement('tr');
    row.append(createElement('td', 'time-cell', formatDataDateTime(snapshot.capturedAt)));
    const sectorCell = document.createElement('td');
    const sector = createElement('div', 'table-stock-identity');
    sector.append(createElement('strong', '', snapshot.sectorName), createElement('span', 'stock-code', snapshot.sectorId));
    sectorCell.append(sector);
    row.append(sectorCell);
    const listCell = document.createElement('td');
    const list = createElement('div', 'snapshot-top10-list');
    const bottomRankStart = Math.max(1, (snapshot.stocks || []).length - 2);
    (snapshot.stocks || []).forEach((stock) => {
        const rankClass = stock.rank <= 3 ? 'top-rank' : stock.rank >= bottomRankStart ? 'bottom-rank' : '';
        const item = createElement('span', rankClass);
        item.append(
            createElement('b', '', `#${stock.rank}`),
            document.createTextNode(` ${stock.stockName} `),
            createElement('small', '', formatPercent(stock.dailyChangePercent)),
            createElement('small', 'ranking-minute-values-inline', `1/3/5 ${formatPercent(stock.return1m)} / ${formatPercent(stock.return3m)} / ${formatPercent(stock.return5m)}`)
        );
        if (stock.signal) {
            item.append(createRankingSignal(stock.signal));
        }
        if (stock.limitUp && item.lastChild && item.lastChild.classList.contains('ranking-minute-values-inline')) {
            item.lastChild.textContent = `1/3/5 ${'\u5c01\u677f'} / ${'\u5c01\u677f'} / ${'\u5c01\u677f'}`;
        }
        const minuteValue = item.querySelector('.ranking-minute-values-inline');
        if (stock.limitUp && minuteValue) {
            minuteValue.textContent = `1/3/5 ${'封板'} / ${'封板'} / ${'封板'}`;
        }
        if (stock.limitUp && minuteValue) {
            const sealed = String.fromCharCode(0x5c01, 0x677f);
            minuteValue.textContent = `1/3/5 ${sealed} / ${sealed} / ${sealed}`;
        }
        list.append(item);
    });
    listCell.append(list);
    row.append(listCell);
    return row;
}

function createRankingSignal(signalValue) {
    const normalized = signalValue === 'B' ? 'B' : 'S';
    const signalClass = normalized === 'B' ? 'signal-buy' : 'signal-sell';
    const signal = createElement('b', `ranking-signal ${signalClass}`, normalized);
    signal.title = normalized === 'B'
        ? '连续10次位于前3，第11次仍在前3'
        : '连续5次位于后3，第6次仍在后3';
    return signal;
}

function localDateValue(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function createStrengthRow(stock) {
    const row = document.createElement('tr');
    if (stock.selected) row.classList.add('selected-strength-row');
    if (stock.limitUp) row.classList.add('limit-up-strength-row');

    const stockCell = document.createElement('td');
    const identity = createElement('div', 'strength-identity');
    identity.append(
        createElement('strong', '', stock.name),
        createElement('span', 'stock-code', stock.fullCode),
        createElement('small', '', stock.performanceLabel || ''),
        createElement('small', 'leader-reason', stock.leaderReason || '板块综合排名靠前')
    );
    stockCell.append(identity);
    row.append(stockCell);

    const rankCell = numberCell(`#${stock.rank || '--'}`);
    rankCell.classList.add('rank-cell');
    row.append(rankCell);

    const chartCell = document.createElement('td');
    const canvas = document.createElement('canvas');
    canvas.className = 'sector-sparkline';
    canvas.width = 112;
    canvas.height = 42;
    canvas.setAttribute('aria-label', `${stock.name}近36分钟走势`);
    chartCell.append(canvas);
    row.append(chartCell);
    window.requestAnimationFrame(() => drawSparkline(canvas, stock.points || []));

    const priceCell = numberCell('');
    const price = createElement('strong', '', formatPrice(stock.currentPrice));
    const dayChange = createElement('span', stock.limitUp ? 'limit-up-label' : '',
        stock.limitUp && stock.dailyChangePercent == null ? '涨停' : formatPercent(stock.dailyChangePercent));
    if (!stock.limitUp) setTrendClass(dayChange, stock.dailyChangePercent);
    priceCell.append(price, dayChange);
    if (stock.limitUp) {
        priceCell.append(createElement('small', 'limit-up-detail',
            `封单 ${formatYuanAmount(stock.sealAmount)} · 炸板 ${stock.breakoutCount == null ? '--' : stock.breakoutCount} 次`));
    }
    priceCell.classList.add('stacked-number');
    row.append(priceCell);

    row.append(metricTripletCell([stock.return1m, stock.return3m, stock.return5m], true, stock.limitUp));

    const volumeCell = numberCell(formatRatio(stock.volumeRatio));
    if (stock.volumeExpanded) {
        volumeCell.append(createElement('small', stock.return5m >= 0 ? 'signal-up' : 'signal-risk', stock.return5m >= 0 ? '放量上攻' : '放量回落'));
    } else {
        volumeCell.append(createElement('small', '', '未明显放量'));
    }
    if (stock.limitUp && stock.limitQualityScore != null) {
        volumeCell.append(createElement('small', 'limit-quality-label', `封板质量 ${stock.limitQualityScore.toFixed(1)}`));
    }
    volumeCell.classList.add('stacked-number');
    row.append(volumeCell);

    const flowCell = numberCell(formatYuanAmount(stock.mainNetInflow));
    setTrendClass(flowCell, stock.mainNetInflow);
    flowCell.append(createElement('small', '', `${formatPercent(stock.mainNetRatio)} · ${stock.flowDate || '--'}`));
    flowCell.classList.add('stacked-number');
    row.append(flowCell);

    const valuationCell = numberCell(formatMultiple(stock.pe));
    valuationCell.append(createElement('small', '', '实时板块行情口径'));
    valuationCell.classList.add('stacked-number');
    row.append(valuationCell);

    row.append(researchRatingCell(stock.latestReport));
    row.append(quarterlyPerformanceCell(stock.quarterlyPerformance, 'revenue'));
    row.append(quarterlyPerformanceCell(stock.quarterlyPerformance, 'netProfit'));

    const scoreCell = numberCell(formatScore(stock.score));
    scoreCell.classList.add('strength-score', 'stacked-number');
    scoreCell.append(
        createElement('small', '', stock.strengthLabel || ''),
        createElement('small', 'strength-signals', (stock.signals || []).join(' · '))
    );
    row.append(scoreCell);
    return row;
}

function researchRatingCell(report) {
    const cell = document.createElement('td');
    cell.className = 'research-rating-cell';
    if (!report) {
        cell.append(createElement('span', 'subtle-value', '暂无近期评级'));
        return cell;
    }
    cell.append(
        createElement('strong', '', report.rating || '未评级'),
        createElement('span', '', report.institution || '--'),
        createElement('small', '', report.date || '--')
    );
    if (report.title) cell.title = report.title;
    return cell;
}

function quarterlyPerformanceCell(periods, field) {
    const cell = document.createElement('td');
    const list = createElement('div', 'quarterly-series');
    const items = (periods || []).slice(0, 8);
    if (items.length === 0) {
        list.append(createElement('span', 'subtle-value', '暂无季度数据'));
    } else {
        items.forEach((period) => {
            const row = createElement('span', 'quarterly-line');
            row.append(
                createElement('small', '', period.period || '--'),
                createElement('strong', '', formatFinancialAmount(period[field]))
            );
            if (field === 'netProfit') setTrendClass(row.querySelector('strong'), period[field]);
            list.append(row);
        });
    }
    cell.append(list);
    return cell;
}

function metricTripletCell(values, directional, limitUp = false) {
    const cell = document.createElement('td');
    const grid = createElement('div', 'metric-triplet');
    if (limitUp) {
        ['1m', '3m', '5m'].forEach((label) => {
            const item = createElement('span', 'metric-triplet-item');
            item.append(createElement('small', '', label));
            item.append(createElement('strong', 'limit-up-metric', '\u5c01\u677f'));
            grid.append(item);
        });
        cell.append(grid);
        return cell;
    }
    ['1m', '3m', '5m'].forEach((label, index) => {
        const item = createElement('span', 'metric-triplet-item');
        item.append(createElement('small', '', label));
        const value = createElement('strong', limitUp && values[index] == null ? 'limit-up-metric' : '',
            limitUp && values[index] == null ? '封板' : formatPercent(values[index]));
        if (directional && !(limitUp && values[index] == null)) setTrendClass(value, values[index]);
        item.append(value);
        grid.append(item);
    });
    cell.append(grid);
    return cell;
}

function drawSparkline(canvas, points) {
    const context = canvas.getContext('2d');
    context.clearRect(0, 0, canvas.width, canvas.height);
    const prices = points.map((point) => point.price).filter(Number.isFinite);
    if (prices.length < 2) {
        context.fillStyle = '#8b9692';
        context.font = '11px sans-serif';
        context.fillText('暂无分钟线', 34, 25);
        return;
    }
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const range = max - min || 1;
    const rising = prices[prices.length - 1] >= prices[0];
    context.strokeStyle = rising ? '#c93632' : '#14805e';
    context.lineWidth = 1.6;
    context.beginPath();
    prices.forEach((price, index) => {
        const x = 2 + index / (prices.length - 1) * (canvas.width - 4);
        const y = 3 + (max - price) / range * (canvas.height - 6);
        if (index === 0) context.moveTo(x, y);
        else context.lineTo(x, y);
    });
    context.stroke();
}

function formatRatio(value) {
    return Number.isFinite(value) ? `${value.toFixed(2)}x` : '--';
}

function formatScore(value) {
    return Number.isFinite(value) ? value.toFixed(1) : '--';
}

async function loadCapitalFlow() {
    toggle('capital-flow-loading', true);
    toggle('capital-flow-empty', false);
    toggle('capital-flow-table-wrap', false);
    toggle('capital-flow-summary', false);
    try {
        const response = await api('/api/watchlist/capital-flow');
        renderCapitalFlow(response.items || []);
    } catch (error) {
        toggle('capital-flow-empty', true);
        showToast(error.message);
    } finally {
        toggle('capital-flow-loading', false);
    }
}

function renderCapitalFlow(items) {
    const body = document.getElementById('capital-flow-body');
    body.replaceChildren();
    toggle('capital-flow-empty', items.length === 0);
    toggle('capital-flow-table-wrap', items.length > 0);

    let totalNetAmount = 0;
    let availableCount = 0;
    items.forEach((item) => {
        const row = document.createElement('tr');
        const stockCell = document.createElement('td');
        const identity = createElement('div', 'table-stock-identity');
        identity.append(createElement('strong', '', item.stock.name), createElement('span', 'stock-code', item.stock.fullCode));
        stockCell.append(identity);
        row.append(stockCell);

        if (!item.available || !item.latest) {
            const unavailable = createElement('td', 'flow-unavailable', item.message || '资金流暂不可用');
            unavailable.colSpan = 8;
            row.append(unavailable);
            body.append(row);
            return;
        }

        const flow = item.latest;
        availableCount += 1;
        totalNetAmount += flow.netAmount;
        row.append(createElement('td', '', flow.date));
        row.append(flowNumberCell(formatPercent(flow.changePercent), flow.changePercent));
        row.append(flowNumberCell(formatYuanAmount(flow.netAmount), flow.netAmount));
        row.append(flowNumberCell(formatPercent(flow.netRatio), flow.netRatio));
        row.append(flowNumberCell(formatYuanAmount(flow.superLargeNet), flow.superLargeNet));
        row.append(flowNumberCell(formatYuanAmount(flow.largeNet), flow.largeNet));
        row.append(flowNumberCell(formatYuanAmount(flow.mediumNet), flow.mediumNet));
        row.append(flowNumberCell(formatYuanAmount(flow.smallNet), flow.smallNet));
        body.append(row);
    });

    const summary = document.getElementById('capital-flow-summary');
    summary.replaceChildren();
    const total = createElement('strong', '', formatYuanAmount(totalNetAmount));
    setTrendClass(total, totalNetAmount);
    summary.append(
        createElement('span', '', `可用数据 ${availableCount}/${items.length} 只`),
        createElement('span', '', '自选股合计净流入'),
        total,
        createElement('small', '', '最近交易日数据，正数代表净流入，负数代表净流出')
    );
    toggle('capital-flow-summary', items.length > 0);
}

function flowNumberCell(text, value) {
    const cell = numberCell(text);
    setTrendClass(cell, value);
    return cell;
}

function openResearch(overview) {
    if (!overview) {
        showToast('请先选择一只股票');
        return;
    }
    state.currentOverview = overview;
    const {stock, quote} = overview;
    setText('research-stock-name', stock.name);
    setText('research-stock-code', stock.fullCode);
    const price = document.getElementById('research-stock-price');
    price.textContent = `${formatPrice(quote.currentPrice)}  ${formatPercent(quote.changePercent)}`;
    setTrendClass(price, quote.changePercent);
    toggle('research-empty', false);
    toggle('research-workspace', true);
    switchTab('research');
}

async function requestInsight(event) {
    event.preventDefault();
    if (!state.currentOverview) {
        showToast('请先选择一只股票');
        return;
    }

    const question = document.getElementById('insight-question').value.trim();
    const submit = document.getElementById('insight-submit');
    submit.disabled = true;
    toggle('insight-result', false);
    toggle('insight-loading', true);
    try {
        const response = await api('/api/stock/insight', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                code: state.currentOverview.stock.fullCode,
                focus: state.insightFocus,
                question
            })
        });
        renderInsight(response);
        toggle('insight-result', true);
    } catch (error) {
        showToast(error.message);
    } finally {
        toggle('insight-loading', false);
        submit.disabled = false;
    }
}

function renderInsight(response) {
    const insight = response.insight;
    const container = document.getElementById('insight-result');
    container.replaceChildren();

    const heading = createElement('div', 'insight-heading');
    heading.append(
        createElement('span', 'eyebrow', 'AI RESEARCH NOTE'),
        createElement('h3', '', insight.headline),
        createElement('p', 'answered-question', `问题：${response.question}`)
    );
    container.append(heading);

    const answer = createElement('div', 'insight-answer');
    const paragraphs = Array.isArray(insight.answer) ? insight.answer : [insight.answer];
    paragraphs.filter(Boolean).forEach((paragraph) => answer.append(createElement('p', '', paragraph)));
    container.append(answer);

    if (insight.keyPoints && insight.keyPoints.length) {
        const grid = createElement('div', 'insight-points');
        insight.keyPoints.forEach((point) => {
            const item = createElement('article', `insight-point stance-${normalizeStance(point.stance)}`);
            item.append(createElement('h4', '', point.title), createElement('p', '', point.detail));
            grid.append(item);
        });
        container.append(grid);
    }

    appendInsightList(container, '风险与数据边界', insight.risks, 'risk-list');
    appendInsightList(container, '后续关注', insight.watchItems, 'watch-list');

    if (insight.followUpQuestions && insight.followUpQuestions.length) {
        const followUp = createElement('section', 'follow-up');
        followUp.append(createElement('h4', '', '继续追问'));
        const buttons = createElement('div', 'follow-up-buttons');
        insight.followUpQuestions.forEach((question) => {
            const button = createElement('button', 'follow-up-button', question);
            button.type = 'button';
            button.addEventListener('click', () => {
                const input = document.getElementById('insight-question');
                input.value = question;
                input.dispatchEvent(new Event('input'));
                input.focus();
            });
            buttons.append(button);
        });
        followUp.append(buttons);
        container.append(followUp);
    }

    container.append(createElement('p', 'disclaimer', insight.disclaimer));
}

function appendInsightList(container, title, items, className) {
    if (!items || items.length === 0) return;
    const section = createElement('section', `insight-list ${className}`);
    section.append(createElement('h4', '', title));
    const list = document.createElement('ul');
    items.forEach((item) => list.append(createElement('li', '', item)));
    section.append(list);
    container.append(section);
}

function createElement(tag, className = '', text = '') {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined && text !== null) element.textContent = text;
    return element;
}

function setText(id, value) {
    document.getElementById(id).textContent = value == null ? '' : value;
}

function toggle(id, visible) {
    document.getElementById(id).classList.toggle('hidden', !visible);
}

function setTrendClass(element, value) {
    element.classList.remove('trend-up', 'trend-down', 'trend-flat');
    element.classList.add(value > 0 ? 'trend-up' : value < 0 ? 'trend-down' : 'trend-flat');
}

function normalizeStance(stance) {
    return ['positive', 'neutral', 'risk'].includes(stance) ? stance : 'neutral';
}

function formatPrice(value) {
    return Number.isFinite(value) ? value.toFixed(2) : '--';
}

function formatPercent(value) {
    if (!Number.isFinite(value)) return '--';
    return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function formatVolume(value) {
    if (!Number.isFinite(value)) return '--';
    if (value >= 100000000) return `${(value / 100000000).toFixed(2)}亿股`;
    if (value >= 10000) return `${(value / 10000).toFixed(1)}万股`;
    return `${Math.round(value)}股`;
}

function formatAmount(value) {
    if (!Number.isFinite(value)) return '--';
    if (value >= 10000) return `${(value / 10000).toFixed(2)}亿元`;
    return `${value.toFixed(1)}万元`;
}

function formatYuanAmount(value) {
    if (!Number.isFinite(value)) return '--';
    const absolute = Math.abs(value);
    const sign = value > 0 ? '+' : value < 0 ? '-' : '';
    if (absolute >= 100000000) return `${sign}${(absolute / 100000000).toFixed(2)}亿元`;
    if (absolute >= 10000) return `${sign}${(absolute / 10000).toFixed(1)}万元`;
    return `${sign}${absolute.toFixed(0)}元`;
}

let toastTimer = null;
function showToast(message) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.classList.remove('hidden');
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => toast.classList.add('hidden'), 3500);
}

window.addEventListener('beforeunload', () => {
    window.clearTimeout(state.top10AutoRefreshTimer);
    window.clearTimeout(state.rankingLogAutoRefreshTimer);
});
