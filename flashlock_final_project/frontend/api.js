const API_BASE_URL = 'http://localhost:8080/api';

const api = {
    async fetchProducts() {
        const res = await fetch(`${API_BASE_URL}/products`);
        if (!res.ok) throw new Error('Failed to fetch products');
        return await res.json();
    },

    async submitPurchase(productId, quantity = 1, priority = 3) {
        const res = await fetch(`${API_BASE_URL}/purchase`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ productId, quantity, priority })
        });
        return await res.json();
    },

    async startSimulation(payload) {
        const res = await fetch(`${API_BASE_URL}/simulation/run`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        return await res.json();
    },

    async fetchSimulationStats(runId) {
        const res = await fetch(`${API_BASE_URL}/simulation/stats?runId=${runId}`);
        return await res.json();
    }
};
