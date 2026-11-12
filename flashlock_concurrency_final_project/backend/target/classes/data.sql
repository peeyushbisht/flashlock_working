TRUNCATE TABLE orders, products RESTART IDENTITY CASCADE;

INSERT INTO products (name, description, category, image_url, price, stock, version) VALUES
(
    'Flagship Gaming Laptop RTX 4080',
    '16-inch QHD 240Hz display, Core i9-14900HX, 32GB DDR5 RAM, 1TB PCIe Gen4 SSD',
    'Laptops',
    'https://images.unsplash.com/photo-1603302576837-37561b2e2302?auto=format&fit=crop&w=800&q=80',
    124999.00,
    10,
    0
),
(
    'Wireless Mechanical Keyboard',
    '75% CNC aluminum chassis, hot-swappable tactile switches, 2.4GHz and Bluetooth',
    'Peripherals',
    'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=800&q=80',
    7499.00,
    50,
    0
),
(
    'Ultra-fast NVMe SSD 2TB',
    'M.2 2280 PCIe Gen4 x4 solid-state drive, up to 7,450 MB/s sequential read',
    'Storage',
    'https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=800&q=80',
    10999.00,
    30,
    0
),
(
    'Flash Sale Studio Headphones',
    'Closed-back planar magnetic wireless headphones with lossless USB-C audio',
    'Audio',
    'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=80',
    4999.00,
    100,
    0
);
