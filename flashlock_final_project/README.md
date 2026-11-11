# ⚡ FlashLock: OS & DBMS Flash-Sale Engine

**Team CodeSense:** Ayush Pilangwal | Shivansh Bijalwan | Peeyush Bisht | Kunj Tyagi  
**Course:** Project-Based Learning (OS + DBMS)

---

## 📌 Project Architecture & Highlights
FlashLock deliberately decouples the **Operating System concurrency layer** from the **Database transaction integrity layer**:
- **OS Layer (C Engine):** Handles incoming request concurrency via Pthreads, circular bounded queue, mutexes, counting semaphores (`sem_t`), and starvation-free request scheduling (**FIFO**, **Priority**, **Priority + Aging**).
- **DBMS Layer (Spring Boot + PostgreSQL):** Serves as the single authoritative source of truth. Prevents stock overselling using atomic conditional SQL execution (`UPDATE products SET stock = stock - :qty WHERE id = :id AND stock >= :qty`).
- **Frontend Dashboard:** Provides live stock updates, order placement, and load simulation visualization.

---

## 🚀 How to Run the Project

### 1. Database Setup (PostgreSQL)
```bash
psql -U postgres -c "CREATE DATABASE flashlock;"
psql -U postgres -d flashlock -f db/schema.sql
psql -U postgres -d flashlock -f db/seed.sql
```

### 2. Compile & Launch C Engine
```bash
cd engine
make clean && make
./bin/flashlock_engine
```

### 3. Launch Spring Boot Backend
```bash
cd backend
./mvnw spring-boot:run
```

### 4. Launch Frontend
Open `frontend/index.html` in your web browser.

---

## 🧪 Running Concurrency & Stress Tests
To execute the 20+ concurrent purchase stress test:
```bash
python3 scripts/concurrency_test.py
```
