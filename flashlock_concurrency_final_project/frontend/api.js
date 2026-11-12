const API_BASE_URL = 'http://localhost:8080/api';

const api = {
    async fetchProducts() {
        const res = await fetch(`${API_BASE_URL}/products`);
        if (!res.ok) {
            throw new Error(`Failed to fetch products (HTTP ${res.status})`);
        }
        return await res.json();
    },

    async submitPurchase(productId, quantity = 1, strategy = 'ATOMIC', priority = 3) {
        const res = await fetch(`${API_BASE_URL}/purchase`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ productId, quantity, priority, strategy })
        });

        let data = null;
        try {
            data = await res.json();
        } catch (e) {
            data = {
                status: 'ERROR',
                message: `Server returned non-JSON response (HTTP ${res.status})`
            };
        }
        return { httpStatus: res.status, ...data };
    },

    async resetProductStock(productId, stock = 100) {
        const res = await fetch(`${API_BASE_URL}/products/${productId}/reset`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ stock })
        });
        if (!res.ok) {
            throw new Error(`Failed to reset stock for product #${productId}`);
        }
        return await res.json();
    },

    async resetAllProducts() {
        const res = await fetch(`${API_BASE_URL}/products/reset-all`, {
            method: 'POST'
        });
        if (!res.ok) {
            throw new Error('Failed to reset catalog stock');
        }
        return await res.json();
    },

    async setSchedulerPolicy(policy, agingIntervalMs) {
        const res = await fetch(`${API_BASE_URL}/scheduler/policy`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ policy, agingIntervalMs })
        });
        if (!res.ok) {
            throw new Error(`Failed to set scheduling policy (HTTP ${res.status})`);
        }
        return await res.json();
    }
};
