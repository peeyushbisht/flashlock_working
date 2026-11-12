# FlashLock — Database Concurrency & OS Synchronization Lab

**Team CodeSense:** Ayush Pilangwal | Shivansh Bijalwan | Peeyush Bisht | Kunj Tyagi  
**Course:** Project-Based Learning (Operating Systems + Database Management Systems)

---

## Architecture Overview

FlashLock demonstrates how concurrent flash-sale traffic behaves across three genuine database concurrency control strategies in PostgreSQL, paired with a C POSIX worker-thread engine:

1. **Atomic Update (`AtomicStrategy`)**
   Executes a single conditional `UPDATE` statement (`UPDATE products SET stock = stock - :qty, version = version + 1 WHERE id = :id AND stock >= :qty`).
2. **Pessimistic Locking (`PessimisticStrategy`)**
   Acquires an exclusive database row lock (`SELECT ... FROM products WHERE id = :id FOR UPDATE`) inside a transaction boundary before checking and decrementing stock.
3. **Optimistic Locking (`OptimisticStrategy`)**
   Reads the current row `version` without blocking readers and verifies `WHERE id = :id AND version = :expectedVersion AND stock >= :qty` on write, explicitly detecting version collisions under concurrent contention.
4. **C POSIX Worker Engine (`engine/`)**
   Handles bounded circular queue buffering, mutexes, counting semaphores, and request scheduling (`FIFO`, `Priority`, `Priority + Aging`) over Unix Domain Sockets (`/tmp/flashlock.sock`).

---

## Setup & Execution

### 1. Apply Updated PostgreSQL Schema & Seed Data
```bash
sudo service postgresql start
sudo -u postgres psql -d flashlock -f db/schema.sql
sudo -u postgres psql -d flashlock -f db/seed.sql
```

### 2. Compile & Start C Engine (Terminal 1)
```bash
cd engine
make clean && make
./bin/flashlock_engine
```

### 3. Start Spring Boot Backend (Terminal 2)
```bash
cd backend
mvn spring-boot:run
```

### 4. Serve Frontend Dashboard (Terminal 3)
```bash
cd frontend
python3 -m http.server 3000
```
Open `http://localhost:3000` in your browser.

### 5. CLI Concurrency Stress Test (Optional, Terminal 4)
```bash
python3 scripts/concurrency_test.py --strategy ATOMIC --requests 1000 --product 4 --reset-stock 100
python3 scripts/concurrency_test.py --strategy PESSIMISTIC --requests 1000 --product 4 --reset-stock 100
python3 scripts/concurrency_test.py --strategy OPTIMISTIC --requests 1000 --product 4 --reset-stock 100
```
