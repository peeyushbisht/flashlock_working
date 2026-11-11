document.addEventListener('DOMContentLoaded', () => {
    loadProducts();
    document.getElementById('sim-form').addEventListener('submit', handleSimulationSubmit);
});

async function loadProducts() {
    const grid = document.getElementById('products-grid');
    try {
        const products = await api.fetchProducts();
        grid.innerHTML = '';
        products.forEach(p => {
            const card = document.createElement('div');
            card.className = 'product-card';
            card.innerHTML = `
                <div>
                    <div class="product-title">${p.name}</div>
                    <div class="product-price">$${p.price.toFixed(2)}</div>
                    <div class="product-stock">In Stock: <strong id="stock-${p.id}">${p.stock}</strong></div>
                </div>
                <button class="btn btn-buy" onclick="triggerPurchase(${p.id})">Buy Now</button>
            `;
            grid.appendChild(card);
        });
    } catch (err) {
        grid.innerHTML = `<p style="color: #f87171;">Error connecting to Spring Boot backend.</p>`;
    }
}

async function triggerPurchase(productId) {
    const banner = document.getElementById('purchase-result');
    banner.className = 'result-banner hidden';
    try {
        const res = await api.submitPurchase(productId, 1, 3);
        banner.classList.remove('hidden');
        if (res.status === 'SUCCESS') {
            banner.className = 'result-banner success';
            banner.innerText = `Success! Order #${res.orderId} placed. Remaining Stock: ${res.remainingStock}`;
            document.getElementById(`stock-${productId}`).innerText = res.remainingStock;
        } else {
            banner.className = 'result-banner error';
            banner.innerText = `Purchase Rejected: ${res.message}`;
        }
    } catch (err) {
        banner.classList.remove('hidden');
        banner.className = 'result-banner error';
        banner.innerText = `System Error: Unable to process request.`;
    }
}

async function handleSimulationSubmit(e) {
    e.preventDefault();
    const btn = document.getElementById('start-sim-btn');
    btn.disabled = true;
    btn.innerText = 'Simulation Running...';

    const payload = {
        requestCount: parseInt(document.getElementById('sim-requests').value),
        workerThreads: parseInt(document.getElementById('sim-workers').value),
        queueCapacity: parseInt(document.getElementById('sim-capacity').value),
        policy: document.getElementById('sim-policy').value,
        agingIntervalMs: parseInt(document.getElementById('sim-aging').value),
        persistOrders: false
    };

    try {
        const res = await api.startSimulation(payload);
        pollMetrics(res.runId, btn);
    } catch (err) {
        btn.disabled = false;
        btn.innerText = 'Start 1000-Request Benchmark';
    }
}

function pollMetrics(runId, btn) {
    const interval = setInterval(async () => {
        try {
            const stats = await api.fetchSimulationStats(runId);
            document.getElementById('metric-submitted').innerText = stats.submitted;
            document.getElementById('metric-completed').innerText = stats.completed;
            document.getElementById('metric-success').innerText = stats.success;
            document.getElementById('metric-rejected').innerText = stats.rejected;
            document.getElementById('metric-avg-wait').innerText = `${stats.avgWaitMs.toFixed(1)} ms`;
            document.getElementById('metric-max-wait').innerText = `${stats.maxWaitMs.toFixed(1)} ms`;

            if (stats.completed >= stats.submitted) {
                clearInterval(interval);
                btn.disabled = false;
                btn.innerText = 'Start 1000-Request Benchmark';
                loadProducts();
            }
        } catch (err) {
            clearInterval(interval);
            btn.disabled = false;
            btn.innerText = 'Start 1000-Request Benchmark';
        }
    }, 500);
}
