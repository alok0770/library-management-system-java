# Library Management System

A full-featured College Library Management System built in pure Core Java using Object-Oriented Programming (OOP) principles, SQLite database persistence via JDBC, an embedded Java HTTP server (`com.sun.net.httpserver.HttpServer`), and a modern, high-end web dashboard.

---

## 🚀 Features & Architecture

### 1. 🌐 Modern Web Dashboard (Port 8081)
- **Live Circulation Desk**: Rapid check-out and check-in interface with real-time student quota badges and live book return previews.
- **Executive Metrics**: Live statistics for Total Cataloged, Books on Shelf, Active Borrowings, and Registered Members.
- **Dynamic Search & Filters**: Instant search across titles, authors, student branches, roll numbers, and contact IDs.
- **Design System**: Premium dark-slate sidebar, glowing status indicators, visual quota trackers (`[●][●][○] 2/3`), and animated toast feedback.

### 2. ⚡ Embedded Core Java REST API (No External Frameworks)
- **Lightweight Built-in Server**: Powered by Java's native `com.sun.net.httpserver.HttpServer` on port `8081`.
- **Dual Mode**: Interactive console CLI and modern web browser UI run concurrently using the same live database and service instances.
- **RESTful Endpoints**:
  - `GET /api/health` — Health check & inventory stats
  - `GET /api/books` & `POST /api/books` & `DELETE /api/books`
  - `GET /api/students` & `POST /api/students` & `DELETE /api/students`
  - `POST /api/issue` — Transactional book checkout
  - `POST /api/return` — Transactional book check-in
  - `GET /api/history` — Permanent audit log

### 3. 📚 Book Inventory Management
- **Add Book**: Unique integer identifier, Title, and Author.
- **Availability Tracking**: Instant status check (*Available* / *Issued* with borrower profile).
- **Deletion Protection**: Prohibits deletion of books that are currently checked out.

### 4. 🧑‍🎓 Student / Member Management
- **Composite Key Isolation**: Tracks students by `Branch + Year (1–4) + Roll Number` to allow identical roll numbers in different departments/years without collision.
- **Contact ID Uniqueness**: Enforces exact 10-digit unique mobile numbers across all students.
- **Quota Enforcer**: Strict rule limiting maximum **3 books** per student simultaneously.

### 5. 🔄 Issue & Return Engine (Audit History)
- **Automatic Timestamps**: Auto-records Issue Date and Return Date (`dd-MM-yyyy`).
- **Permanent Audit Trail**: Immutable transaction history preserving full borrow/return lifecycle records.

### 6. 💾 Persistent Database (SQLite via JDBC)
- **Single File Storage**: All data persists in local `library.db`.
- **ACID Transactions**: Atomic multi-table updates (`conn.setAutoCommit(false)`, `commit()`, `rollback()`) guarantee zero data corruption.
- **Clean Slate Ready**: Ready for high-volume automated batch CSV imports.

---

## 🛠️ Tech Stack & Concepts

- **Language**: Core Java (Java 8 / Java 17 / Java 21 / Java 25 LTS compatible)
- **Web Server**: `com.sun.net.httpserver.HttpServer` (zero external frameworks)
- **Frontend**: Vanilla HTML5, Modern CSS3 (Grid & Flexbox), Vanilla JavaScript (ES6+ `fetch` API)
- **Database**: SQLite with JDBC (`sqlite-jdbc` 3.45+)
- **OOP Principles**: Encapsulation, Composition, Separation of Concerns, In-Memory Caching with DB Sync
- **Concurrency**: `Executors.newFixedThreadPool(10)` for non-blocking HTTP requests

---

## 🏃 How to Run

1. **Clone the repository:**
   ```bash
   git clone https://github.com/alok0770/library-management-system-java.git
   cd library-management-system-java
   ```

2. **Compile the project:**
   ```bash
   javac -cp "lib/*:src" -d out/production/Library-management-system-java src/LibraryManagementSystem/*.java
   ```

3. **Run the Application:**
   ```bash
   java -cp "out/production/Library-management-system-java:lib/*" LibraryManagementSystem.Main
   ```

4. **Access the Web Dashboard:**
   Open your browser at:
   👉 **http://localhost:8081**