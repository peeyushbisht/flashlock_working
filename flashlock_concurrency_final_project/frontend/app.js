(() => {
    const STRATEGY_META = {
        ATOMIC: {
            label: 'Atomic Update',
            short: 'Atomic',
            explanation:
                'The database performs the stock check and stock decrement as one atomic SQL operation. This minimizes application-level coordination and avoids holding a row lock across a multi-step transaction, providing high throughput for single-row flash-sale updates.',
            sql: 'UPDATE products\nSET stock = stock - :qty, version = version + 1\nWHERE id = :productId AND stock >= :qty;'
        },
        PESSIMISTIC: {
            label: 'Pessimistic Locking',
            short: 'Pessimistic',
            explanation:
                'Requests acquire an exclusive database row lock (SELECT ... FOR UPDATE) before reading or modifying stock. Only one transaction can modify the locked row at a time, guaranteeing strict serialization while increasing lock wait times when hundreds of requests target the same product.',
            sql: 'BEGIN;\nSELECT stock FROM products WHERE id = :productId FOR UPDATE;\nUPDATE products SET stock = stock - :qty, version = version + 1 WHERE id = :productId;\nCOMMIT;'
        },
        OPTIMISTIC: {
            label: 'Optimistic Locking',
            short: 'Optimistic',
            explanation:
                'Requests read the current product version without acquiring a row lock, then verify that the version has not changed when writing. When many concurrent requests target the same stock record simultaneously, version collisions occur and conflicting writes are rejected for retry.',
            sql: 'UPDATE products\nSET stock = stock - :qty, version = version + 1\nWHERE id = :productId AND version = :expectedVersion AND stock >= :qty;'
        }
    };

    const POLICY_META = {
        FIFO: {
            label: 'FIFO',
            explanation:
                'Requests are served strictly in arrival order, so every request waits about the same time no matter its priority.'
        },
        PRIORITY: {
            label: 'Priority',
            explanation:
                'The scheduler always serves the highest-priority waiting request first. High-priority requests wait the least, while normal ones can be delayed as long as higher-priority requests keep arriving.'
        },
        PRIORITY_AGING: {
            label: 'Priority + Aging',
            explanation:
                'Works like Priority, but a waiting request gains one priority level for every aging interval, so low-priority requests are not starved.'
        }
    };

    const FALLBACK_SVG =
        'data:image/svg+xml;utf8,' +
        encodeURIComponent(
            '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="400" viewBox="0 0 640 400">' +
            '<rect width="640" height="400" fill="#f1f5f9"/>' +
            '<rect x="220" y="130" width="200" height="120" rx="8" fill="#cbd5e1"/>' +
            '<text x="320" y="285" text-anchor="middle" font-family="sans-serif" font-size="16" fill="#475569">Hardware Product</text>' +
            '</svg>'
        );

    const state = {
        products: [],
        selectedStrategy: 'ATOMIC',
        selectedPolicy: 'PRIORITY_AGING',
        isRunning: false,
        abortRequested: false,
        lastConfig: null,
        latestByStrategy: {
            ATOMIC: null,
            PESSIMISTIC: null,
            OPTIMISTIC: null
        },
        history: []
    };

    const inrFormatter = new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 0
    });

    function formatINR(value) {
        const num = Number(value);
        if (Number.isNaN(num)) return '₹0';
        return inrFormatter.format(num);
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    // =========================================================================
    // Strategy Synchronization
    // =========================================================================
    function setStrategy(strategyKey) {
        if (!STRATEGY_META[strategyKey]) return;
        state.selectedStrategy = strategyKey;
        const meta = STRATEGY_META[strategyKey];

        document.querySelectorAll('#global-strategy-selector .segment-btn, #lab-strategy-selector .segment-btn').forEach(btn => {
            const isMatch = btn.dataset.strategy === strategyKey;
            btn.classList.toggle('active', isMatch);
            if (btn.hasAttribute('aria-pressed')) {
                btn.setAttribute('aria-pressed', String(isMatch));
            }
        });

        document.getElementById('header-strategy-pill').innerHTML = `Strategy: <strong>${meta.label}</strong>`;
        document.getElementById('selected-strategy-label').textContent = meta.label;
        updateResultBadge();

        document.getElementById('explain-strategy-name').textContent = meta.label;
        document.getElementById('explain-strategy-body').textContent = meta.explanation;
        document.getElementById('explain-strategy-sql').textContent = meta.sql;
    }

    function updateResultBadge() {
        document.getElementById('result-strategy-badge').textContent =
            `${state.selectedStrategy} · ${POLICY_META[state.selectedPolicy].label}`;
    }

    function setPolicy(policyKey) {
        if (!POLICY_META[policyKey]) return;
        state.selectedPolicy = policyKey;
        const meta = POLICY_META[policyKey];

        document.querySelectorAll('#lab-policy-selector .segment-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.policy === policyKey);
        });
        document.getElementById('burst-aging').disabled = policyKey !== 'PRIORITY_AGING';
        document.getElementById('explain-policy-name').textContent = meta.label;
        document.getElementById('explain-policy-body').textContent = meta.explanation;
        updateResultBadge();
    }

    function schedulingConfig() {
        return {
            policy: state.selectedPolicy,
            agingIntervalMs: Math.max(1, Math.min(600000, Number(document.getElementById('burst-aging').value) || 500)),
            priorityMix: document.getElementById('burst-priority-mix').value
        };
    }

    // =========================================================================
    // Feedback Banner
    // =========================================================================
    function showFeedback(type, message) {
        const el = document.getElementById('purchase-feedback');
        el.className = `feedback-banner ${type}`;
        el.innerHTML = `<span>${escapeHtml(message)}</span><button type="button" class="btn btn-tertiary btn-sm" id="dismiss-feedback-btn">Dismiss</button>`;
        el.classList.remove('hidden');
        document.getElementById('dismiss-feedback-btn').addEventListener('click', () => {
            el.classList.add('hidden');
        });
    }

    // =========================================================================
    // Product Catalog & Stock Rendering
    // =========================================================================
    async function loadProducts() {
        const grid = document.getElementById('products-grid');
        try {
            const products = await api.fetchProducts();
            state.products = products;
            renderProductCards(products);
            syncProductDropdown(products);
        } catch (err) {
            grid.innerHTML = `<div class="empty-state">Unable to load inventory from backend (${escapeHtml(err.message)}). Verify Spring Boot is running on port 8080.</div>`;
        }
    }

    function renderProductCards(products) {
        const grid = document.getElementById('products-grid');
        if (!products || products.length === 0) {
            grid.innerHTML = '<div class="empty-state">No products found in PostgreSQL database.</div>';
            return;
        }

        grid.innerHTML = '';
        products.forEach(p => {
            const stock = Number(p.stock) || 0;
            const version = Number(p.version) || 0;

            let stockClass = 'stock-available';
            let stockText = `Stock: ${stock} available`;
            if (stock === 0) {
                stockClass = 'stock-empty';
                stockText = 'Out of stock';
            } else if (stock <= 9) {
                stockClass = 'stock-low';
                stockText = `Only ${stock} left`;
            }

            const card = document.createElement('article');
            card.className = 'product-card';
            card.innerHTML = `
                <div class="product-media">
                    <img src="${escapeHtml(p.imageUrl || FALLBACK_SVG)}" alt="${escapeHtml(p.name)}" loading="lazy" onerror="this.onerror=null;this.src='${FALLBACK_SVG}';">
                    <span class="product-version-tag" title="Row version for optimistic locking">v${version}</span>
                </div>
                <div class="product-body">
                    <span class="product-category">${escapeHtml(p.category || 'Hardware')}</span>
                    <h3 class="product-name">${escapeHtml(p.name)}</h3>
                    <p class="product-desc">${escapeHtml(p.description || '')}</p>
                    <div class="product-price-stock">
                        <span class="product-price">${formatINR(p.price)}</span>
                        <span class="stock-indicator ${stockClass}">
                            <span class="stock-dot" aria-hidden="true"></span>
                            <span>${stockText}</span>
                        </span>
                    </div>
                    <div class="product-actions">
                        <label class="sr-only" for="qty-${p.id}" style="position:absolute;width:1px;height:1px;overflow:hidden;">Quantity for ${escapeHtml(p.name)}</label>
                        <select id="qty-${p.id}" class="qty-select" ${stock === 0 ? 'disabled' : ''}>
                            <option value="1">1</option>
                            <option value="2">2</option>
                            <option value="3">3</option>
                            <option value="5">5</option>
                        </select>
                        <button type="button" class="btn btn-primary btn-buy" data-product-id="${p.id}" ${stock === 0 ? 'disabled' : ''}>
                            ${stock === 0 ? 'Out of stock' : 'Buy Now'}
                        </button>
                    </div>
                </div>
            `;
            grid.appendChild(card);
        });

        grid.querySelectorAll('.btn-buy').forEach(btn => {
            btn.addEventListener('click', () => {
                const productId = Number(btn.dataset.productId);
                const qtyEl = document.getElementById(`qty-${productId}`);
                const quantity = qtyEl ? Number(qtyEl.value) : 1;
                handleSinglePurchase(productId, quantity, btn);
            });
        });
    }

    function syncProductDropdown(products) {
        const select = document.getElementById('burst-product');
        const previousVal = select.value;

        select.innerHTML = '';
        products.forEach(p => {
            const opt = document.createElement('option');
            opt.value = p.id;
            opt.textContent = `${p.name} (Stock: ${p.stock})`;
            select.appendChild(opt);
        });

        if (previousVal && products.some(p => String(p.id) === previousVal)) {
            select.value = previousVal;
        } else if (products.some(p => p.id === 4)) {
            select.value = '4';
        }
        updateSelectedProductMeta();
    }

    function updateSelectedProductMeta() {
        const select = document.getElementById('burst-product');
        const prod = state.products.find(p => String(p.id) === select.value);
        const metaEl = document.getElementById('burst-product-meta');
        if (prod) {
            metaEl.textContent = `Current stock: ${prod.stock} units (version v${prod.version || 0})`;
        } else {
            metaEl.textContent = 'Current stock: —';
        }
    }

    async function handleSinglePurchase(productId, quantity, btn) {
        const originalText = btn.textContent;
        btn.disabled = true;
        btn.textContent = 'Processing...';

        try {
            const res = await api.submitPurchase(productId, quantity, state.selectedStrategy, 3);
            if (res.status === 'SUCCESS') {
                showFeedback(
                    'success',
                    `${res.message} Order #${res.orderId} recorded. Remaining stock: ${res.remainingStock}.`
                );
            } else if (res.status === 'CONFLICT') {
                showFeedback('conflict', res.message);
            } else {
                showFeedback('error', res.message || 'Purchase failed. The requested stock is no longer available.');
            }
            await loadProducts();
        } catch (err) {
            showFeedback('error', `Request failed: ${err.message}`);
            btn.disabled = false;
            btn.textContent = originalText;
        }
    }

    // =========================================================================
    // Genuine Concurrent Burst Request Runner (Controlled Worker Pool)
    // =========================================================================
    async function runBurstTest(config) {
        if (state.isRunning) return;
        state.isRunning = true;
        state.abortRequested = false;
        state.lastConfig = { ...config };

        const startBtn = document.getElementById('start-burst-btn');
        const abortBtn = document.getElementById('abort-burst-btn');
        const progressBox = document.getElementById('burst-progress-box');
        const emptyState = document.getElementById('burst-empty-state');
        const resultsContent = document.getElementById('burst-results-content');

        startBtn.disabled = true;
        startBtn.textContent = 'Running Burst Test...';
        abortBtn.classList.remove('hidden');
        emptyState.classList.add('hidden');
        progressBox.classList.remove('hidden');
        resultsContent.classList.add('hidden');

        // Apply the chosen scheduling policy in the C engine before the burst starts
        try {
            await api.setSchedulerPolicy(config.policy, config.agingIntervalMs);
        } catch (err) {
            // Backend without scheduler support: the burst still runs, just unscheduled.
        }

        // Refresh products right before the burst test to capture exact initial stock
        await loadProducts();
        const targetBefore = state.products.find(p => p.id === config.productId);
        const initialStock = targetBefore ? Number(targetBefore.stock) : 0;

        let nextIndex = 0;
        let completed = 0;
        let successCount = 0;
        let outOfStockCount = 0;
        let conflictCount = 0;
        let errorCount = 0;
        let totalLatencyMs = 0;
        let waitSum = 0;
        let waitCount = 0;
        let waitMax = 0;
        const waitByPriority = { 1: { sum: 0, count: 0 }, 3: { sum: 0, count: 0 } };

        const updateProgressUI = () => {
            const pct = Math.min(100, Math.round((completed / config.requestCount) * 100));
            document.getElementById('progress-status-text').textContent = state.abortRequested ? 'Stopping test...' : 'Test Running...';
            document.getElementById('progress-percent').textContent = `${pct}%`;
            document.getElementById('progress-fill').style.width = `${pct}%`;
            document.getElementById('progress-bar-role').setAttribute('aria-valuenow', String(pct));
            document.getElementById('progress-counts').textContent = `Requests completed: ${completed} / ${config.requestCount}`;
            document.getElementById('progress-live-tally').textContent =
                `Success: ${successCount} | Out of stock: ${outOfStockCount} | Conflicts: ${conflictCount}`;
        };

        updateProgressUI();
        const progressTimer = setInterval(updateProgressUI, 60);

        const wallStart = performance.now();
        const workerCount = Math.min(config.concurrency, config.requestCount);

        async function workerLoop() {
            while (!state.abortRequested) {
                const idx = nextIndex++;
                if (idx >= config.requestCount) break;

                const reqStart = performance.now();
                const priority = config.priorityMix === 'mixed' ? (idx % 3) + 1 : 3;
                try {
                    const res = await api.submitPurchase(config.productId, config.quantity, config.strategy, priority);
                    if (res.scheduled && typeof res.queueWaitMs === 'number') {
                        waitSum += res.queueWaitMs;
                        waitCount++;
                        waitMax = Math.max(waitMax, res.queueWaitMs);
                        if (waitByPriority[priority]) {
                            waitByPriority[priority].sum += res.queueWaitMs;
                            waitByPriority[priority].count++;
                        }
                    }
                    const elapsed = performance.now() - reqStart;
                    totalLatencyMs += elapsed;

                    if (res.status === 'SUCCESS') {
                        successCount++;
                    } else if (res.status === 'CONFLICT' || res.conflictDetected) {
                        conflictCount++;
                    } else if (res.status === 'OUT_OF_STOCK') {
                        outOfStockCount++;
                    } else {
                        errorCount++;
                    }
                } catch (err) {
                    totalLatencyMs += (performance.now() - reqStart);
                    errorCount++;
                } finally {
                    completed++;
                }
            }
        }

        const workers = Array.from({ length: workerCount }, () => workerLoop());
        await Promise.all(workers);

        const wallEnd = performance.now();
        clearInterval(progressTimer);
        updateProgressUI();

        // Fetch authoritative post-test database state
        await loadProducts();
        const targetAfter = state.products.find(p => p.id === config.productId);
        const finalStock = targetAfter ? Number(targetAfter.stock) : 0;

        const totalTimeMs = Math.max(1, Math.round(wallEnd - wallStart));
        const avgResponseMs = completed > 0 ? (totalLatencyMs / completed) : 0;
        const throughput = Math.round(completed / (totalTimeMs / 1000));
        const failedTotal = outOfStockCount + conflictCount + errorCount;
        const purchasedUnits = successCount * config.quantity;
        const oversellingDetected = finalStock < 0 || purchasedUnits > initialStock;

        const record = {
            id: Date.now(),
            strategy: config.strategy,
            strategyLabel: STRATEGY_META[config.strategy].label,
            productId: config.productId,
            productName: targetBefore ? targetBefore.name : `Product #${config.productId}`,
            requests: completed,
            quantityPerRequest: config.quantity,
            concurrency: workerCount,
            success: successCount,
            purchasedUnits,
            outOfStock: outOfStockCount,
            conflicts: conflictCount,
            failedTotal,
            totalTimeMs,
            avgResponseMs,
            throughput,
            initialStock,
            finalStock,
            oversellingDetected,
            policy: config.policy,
            policyLabel: POLICY_META[config.policy].label,
            scheduled: waitCount > 0,
            avgQueueWaitMs: waitCount > 0 ? waitSum / waitCount : null,
            maxQueueWaitMs: waitCount > 0 ? waitMax : null,
            highWaitMs: config.priorityMix === 'mixed' && waitByPriority[3].count > 0
                ? waitByPriority[3].sum / waitByPriority[3].count : null,
            normalWaitMs: config.priorityMix === 'mixed' && waitByPriority[1].count > 0
                ? waitByPriority[1].sum / waitByPriority[1].count : null
        };

        state.latestByStrategy[config.strategy] = record;
        state.history.unshift(record);

        renderBurstResults(record);
        renderComparisonTable();
        renderPerformanceChart();
        renderHistoryList();

        if (!record.scheduled && completed > 0) {
            showFeedback('conflict', 'Scheduler engine is offline, so requests ran directly without queue scheduling. Start the C engine to measure scheduling policies.');
        }

        state.isRunning = false;
        startBtn.disabled = false;
        startBtn.textContent = 'Start Burst Test';
        abortBtn.classList.add('hidden');
        document.getElementById('run-again-btn').disabled = false;
        setTimeout(() => {
            if (!state.isRunning) {
                progressBox.classList.add('hidden');
            }
        }, 600);
    }

    function fmtMs(value) {
        return value === null || value === undefined ? '—' : `${Number(value).toFixed(1)} ms`;
    }

    function renderBurstResults(rec) {
        document.getElementById('burst-empty-state').classList.add('hidden');
        document.getElementById('burst-results-content').classList.remove('hidden');

        document.getElementById('result-strategy-badge').textContent = `${rec.strategy} · ${rec.policyLabel}`;
        document.getElementById('res-total-requests').textContent = rec.requests.toLocaleString('en-IN');
        document.getElementById('res-success').textContent = rec.success.toLocaleString('en-IN');
        document.getElementById('res-failed').textContent = rec.failedTotal.toLocaleString('en-IN');
        document.getElementById('res-conflicts').textContent = rec.conflicts.toLocaleString('en-IN');
        document.getElementById('res-total-time').textContent = `${rec.totalTimeMs.toLocaleString('en-IN')} ms`;
        document.getElementById('res-avg-time').textContent = `${rec.avgResponseMs.toFixed(1)} ms`;
        document.getElementById('res-throughput').textContent = `${rec.throughput.toLocaleString('en-IN')} req/s`;
        document.getElementById('res-overselling').textContent = rec.oversellingDetected ? 'YES' : 'NO';
        document.getElementById('res-queue-avg').textContent = fmtMs(rec.avgQueueWaitMs);
        document.getElementById('res-queue-max').textContent = fmtMs(rec.maxQueueWaitMs);
        document.getElementById('res-queue-high').textContent = fmtMs(rec.highWaitMs);
        document.getElementById('res-queue-normal').textContent = fmtMs(rec.normalWaitMs);

        const safetyBox = document.getElementById('safety-verification-box');
        const safetyBadge = document.getElementById('safety-status-badge');
        safetyBox.classList.toggle('safety-fail', rec.oversellingDetected);
        safetyBadge.textContent = `Overselling Detected: ${rec.oversellingDetected ? 'YES' : 'NO'}`;

        document.getElementById('safe-initial-stock').textContent = `${rec.initialStock}`;
        document.getElementById('safe-purchased-units').textContent = `${rec.purchasedUnits}`;
        document.getElementById('safe-final-stock').textContent = `${rec.finalStock}`;
        document.getElementById('safe-constraint-check').textContent = rec.oversellingDetected
            ? 'Constraint Violation'
            : 'Database Constraint Passed';
    }

    function renderComparisonTable() {
        const strategies = ['ATOMIC', 'PESSIMISTIC', 'OPTIMISTIC'];
        strategies.forEach(key => {
            const rec = state.latestByStrategy[key];
            document.getElementById(`cmp-${key}-requests`).textContent = rec ? rec.requests.toLocaleString('en-IN') : '—';
            document.getElementById(`cmp-${key}-success`).textContent = rec ? rec.success.toLocaleString('en-IN') : '—';
            document.getElementById(`cmp-${key}-failed`).textContent = rec ? rec.outOfStock.toLocaleString('en-IN') : '—';
            document.getElementById(`cmp-${key}-conflicts`).textContent = rec ? rec.conflicts.toLocaleString('en-IN') : '—';
            document.getElementById(`cmp-${key}-time`).textContent = rec ? `${rec.totalTimeMs.toLocaleString('en-IN')} ms` : '—';
            document.getElementById(`cmp-${key}-avg`).textContent = rec ? `${rec.avgResponseMs.toFixed(1)} ms` : '—';
            document.getElementById(`cmp-${key}-throughput`).textContent = rec ? `${rec.throughput.toLocaleString('en-IN')} req/s` : '—';
            document.getElementById(`cmp-${key}-policy`).textContent = rec ? rec.policyLabel : '—';
            document.getElementById(`cmp-${key}-qwait`).textContent = rec ? fmtMs(rec.avgQueueWaitMs) : '—';
            document.getElementById(`cmp-${key}-oversell`).textContent = rec ? (rec.oversellingDetected ? 'YES' : 'NO') : '—';
        });

        const tested = strategies.map(k => state.latestByStrategy[k]).filter(Boolean);
        const obsEl = document.getElementById('observed-performance-text');
        if (tested.length === 0) {
            obsEl.textContent =
                'Run tests across the strategies to compare observed throughput and latency. Performance depends on database engine, hardware, network, request volume, initial stock, contention level, transaction duration, and connection pool size.';
        } else {
            const fastest = [...tested].sort((a, b) => b.throughput - a.throughput)[0];
            obsEl.textContent =
                `Based on the recorded tests in this session, ${fastest.strategyLabel} achieved the highest observed throughput (${fastest.throughput.toLocaleString('en-IN')} req/s over ${fastest.requests.toLocaleString('en-IN')} requests in ${fastest.totalTimeMs.toLocaleString('en-IN')} ms). Performance depends on database engine, hardware, network, request volume, initial stock, contention level, transaction duration, and connection pool size.`;
        }
    }

    function renderPerformanceChart() {
        const emptyEl = document.getElementById('chart-empty-state');
        const barsEl = document.getElementById('chart-bars-area');
        const strategies = ['ATOMIC', 'PESSIMISTIC', 'OPTIMISTIC'];
        const tested = strategies.map(k => state.latestByStrategy[k]).filter(Boolean);

        if (tested.length === 0) {
            emptyEl.classList.remove('hidden');
            barsEl.classList.add('hidden');
            return;
        }

        emptyEl.classList.add('hidden');
        barsEl.classList.remove('hidden');

        const maxThroughput = Math.max(...tested.map(r => r.throughput), 1);
        const maxTime = Math.max(...tested.map(r => r.totalTimeMs), 1);

        const throughputBars = tested
            .map(r => {
                const pct = Math.max(4, Math.round((r.throughput / maxThroughput) * 100));
                const cls = r.strategy.toLowerCase();
                return `
                    <div class="bar-row">
                        <span class="bar-label">${STRATEGY_META[r.strategy].short}</span>
                        <div class="bar-track"><div class="bar-fill ${cls}" style="width: ${pct}%;"></div></div>
                        <span class="bar-value">${r.throughput.toLocaleString('en-IN')} req/s</span>
                    </div>
                `;
            })
            .join('');

        const timeBars = tested
            .map(r => {
                const pct = Math.max(4, Math.round((r.totalTimeMs / maxTime) * 100));
                const cls = r.strategy.toLowerCase();
                return `
                    <div class="bar-row">
                        <span class="bar-label">${STRATEGY_META[r.strategy].short}</span>
                        <div class="bar-track"><div class="bar-fill ${cls}" style="width: ${pct}%;"></div></div>
                        <span class="bar-value">${r.totalTimeMs.toLocaleString('en-IN')} ms</span>
                    </div>
                `;
            })
            .join('');

        barsEl.innerHTML = `
            <div>
                <div class="chart-group-title">Throughput (Higher is faster)</div>
                ${throughputBars}
            </div>
            <div>
                <div class="chart-group-title">Total Execution Time (Lower is faster)</div>
                ${timeBars}
            </div>
        `;
    }

    function renderHistoryList() {
        const listEl = document.getElementById('test-history-list');
        const countEl = document.getElementById('history-count-label');
        countEl.textContent = `${state.history.length} run${state.history.length === 1 ? '' : 's'} recorded`;

        if (state.history.length === 0) {
            listEl.innerHTML = '<div class="history-empty">No completed benchmark runs in this session.</div>';
            return;
        }

        listEl.innerHTML = state.history
            .map(
                item => `
                <div class="history-row">
                    <div class="history-meta">
                        <span class="history-strategy">${escapeHtml(item.strategyLabel)}</span>
                        <span class="mono-text">${escapeHtml(item.policyLabel)}</span>
                        <span class="mono-text">Queue wait: ${fmtMs(item.avgQueueWaitMs)}</span>
                        <span class="mono-text">${item.requests.toLocaleString('en-IN')} requests</span>
                        <span class="mono-text">${item.totalTimeMs.toLocaleString('en-IN')} ms</span>
                        <span class="mono-text">${item.throughput.toLocaleString('en-IN')} req/s</span>
                        <span class="mono-text">Success: ${item.success} | Conflicts: ${item.conflicts}</span>
                        <span class="mono-text">Overselling: ${item.oversellingDetected ? 'YES' : 'NO'}</span>
                    </div>
                    <span class="field-hint">${escapeHtml(item.productName)}</span>
                </div>
            `
            )
            .join('');
    }

    // =========================================================================
    // Event Wiring
    // =========================================================================
    document.addEventListener('DOMContentLoaded', () => {
        setStrategy('ATOMIC');
        setPolicy('PRIORITY_AGING');
        loadProducts();

        document.querySelectorAll('#lab-policy-selector .segment-btn').forEach(btn => {
            btn.addEventListener('click', () => setPolicy(btn.dataset.policy));
        });

        document.querySelectorAll('#global-strategy-selector .segment-btn, #lab-strategy-selector .segment-btn').forEach(btn => {
            btn.addEventListener('click', () => setStrategy(btn.dataset.strategy));
        });

        document.getElementById('refresh-products-btn').addEventListener('click', loadProducts);

        document.getElementById('reset-all-btn').addEventListener('click', async () => {
            try {
                await api.resetAllProducts();
                await loadProducts();
                showFeedback('success', 'All catalog products have been reset to their initial stock levels.');
            } catch (err) {
                showFeedback('error', err.message);
            }
        });

        document.getElementById('burst-product').addEventListener('change', updateSelectedProductMeta);

        document.getElementById('reset-target-100-btn').addEventListener('click', async () => {
            const productId = Number(document.getElementById('burst-product').value);
            if (!productId) return;
            try {
                await api.resetProductStock(productId, 100);
                await loadProducts();
                showFeedback('success', `Target product #${productId} stock reset to 100 units (version v0).`);
            } catch (err) {
                showFeedback('error', err.message);
            }
        });

        document.querySelectorAll('.preset-chip').forEach(chip => {
            chip.addEventListener('click', () => {
                document.querySelectorAll('.preset-chip').forEach(c => c.classList.remove('active'));
                chip.classList.add('active');
                document.getElementById('burst-requests').value = chip.dataset.count;
            });
        });

        document.getElementById('burst-requests').addEventListener('input', e => {
            const val = e.target.value;
            document.querySelectorAll('.preset-chip').forEach(c => {
                c.classList.toggle('active', c.dataset.count === val);
            });
        });

        document.getElementById('burst-form').addEventListener('submit', e => {
            e.preventDefault();
            const productId = Number(document.getElementById('burst-product').value);
            const requestCount = Math.max(1, Math.min(20000, Number(document.getElementById('burst-requests').value) || 1000));
            const quantity = Math.max(1, Math.min(50, Number(document.getElementById('burst-quantity').value) || 1));
            const concurrency = Math.max(1, Math.min(100, Number(document.getElementById('burst-concurrency').value) || 40));

            runBurstTest({
                productId,
                requestCount,
                quantity,
                concurrency,
                strategy: state.selectedStrategy,
                ...schedulingConfig()
            });
        });

        document.getElementById('abort-burst-btn').addEventListener('click', () => {
            state.abortRequested = true;
        });

        document.querySelectorAll('.table-run-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const strat = btn.dataset.runStrategy;
                setStrategy(strat);
                const productId = Number(document.getElementById('burst-product').value);
                const requestCount = Number(document.getElementById('burst-requests').value) || 1000;
                const quantity = Number(document.getElementById('burst-quantity').value) || 1;
                const concurrency = Number(document.getElementById('burst-concurrency').value) || 40;

                runBurstTest({
                    productId,
                    requestCount,
                    quantity,
                    concurrency,
                    strategy: strat,
                    ...schedulingConfig()
                });
            });
        });

        document.getElementById('run-again-btn').addEventListener('click', () => {
            if (state.lastConfig && !state.isRunning) {
                runBurstTest(state.lastConfig);
            }
        });

        document.getElementById('clear-results-btn').addEventListener('click', () => {
            state.latestByStrategy = { ATOMIC: null, PESSIMISTIC: null, OPTIMISTIC: null };
            state.history = [];
            document.getElementById('burst-results-content').classList.add('hidden');
            document.getElementById('burst-empty-state').classList.remove('hidden');
            document.getElementById('run-again-btn').disabled = true;
            renderComparisonTable();
            renderPerformanceChart();
            renderHistoryList();
        });
    });
})();
