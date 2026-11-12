DROP VIEW IF EXISTS sales_per_product;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    category VARCHAR(60),
    image_url TEXT,
    price NUMERIC(10, 2) NOT NULL,
    stock INT NOT NULL CHECK (stock >= 0),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT UNIQUE NOT NULL,
    product_id BIGINT REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    strategy VARCHAR(30) NOT NULL DEFAULT 'ATOMIC',
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_stock ON products(id, stock);
CREATE INDEX idx_orders_request_id ON orders(request_id);

CREATE OR REPLACE VIEW sales_per_product AS
SELECT 
    p.id AS product_id,
    p.name AS product_name,
    COUNT(o.id) AS total_orders,
    COALESCE(SUM(o.quantity), 0) AS total_units_sold,
    p.stock AS remaining_stock,
    p.version AS current_version
FROM products p
LEFT JOIN orders o ON p.id = o.product_id AND o.status = 'SUCCESS'
GROUP BY p.id, p.name, p.stock, p.version;
