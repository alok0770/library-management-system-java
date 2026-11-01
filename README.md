# 📚 CampusLib — College Library Management System

<p align="center">
  <img src="https://img.shields.io/badge/Java-8%20%7C%2017%20%7C%2021-0969DA?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java Version"/>
  <img src="https://img.shields.io/badge/Server-Native%20HttpServer-00E5FF?style=for-the-badge&logo=java&logoColor=black" alt="Native HttpServer"/>
  <img src="https://img.shields.io/badge/Database-SQLite%20+%20JDBC-003B57?style=for-the-badge&logo=sqlite&logoColor=white" alt="SQLite JDBC"/>
  <img src="https://img.shields.io/badge/Frontend-HTML5%20%7C%20CSS3%20%7C%20JS-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black" alt="Web Frontend"/>
  <img src="https://img.shields.io/badge/Deployment-Institutional%20Local-2EA44F?style=for-the-badge" alt="Local Deployment"/>
</p>

---

## 📖 Overview

**CampusLib** is a complete, full-featured **College Library Management System** developed in pure Core Java using Object-Oriented Programming (OOP) principles, SQLite database persistence via JDBC, and an embedded Java HTTP server (`com.sun.net.httpserver.HttpServer`). 

Engineered and delivered for institutional campus administration, the application operates in **Dual Mode**:
1. **Interactive Console CLI**: For high-speed terminal operations and system administration.
2. **Modern Web Dashboard**: Accessible via browser on `http://localhost:8081` (or custom port like `8080`) providing visual circulation, inventory management, and live audit tracking.

---

## 🏛️ System Architecture

```
                      ┌─────────────────────────────────────────┐
                      │            CampusLib Clients            │
                      ├────────────────────┬────────────────────┤
                      │  Terminal CLI UI   │ Web Dashboard (UI) │
                      └─────────┬──────────┴─────────┬──────────┘
                                │                    │ HTTP (JSON REST)
                                │                    ▼
                                │         ┌─────────────────────┐
                                │         │  LibraryHttpServer  │
                                │         │ (Native HttpServer) │
                                │         └──────────┬──────────┘
                                ▼                    ▼
                      ┌─────────────────────────────────────────┐
                      │              Service Layer              │
                      │  BookService  │     StudentService      │
                      └────────────────────┬────────────────────┘
                                           │
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │         DatabaseHelper (JDBC)           │
                      │       Thread-Safe & ACID Compliant      │
                      └────────────────────┬────────────────────┘
                                           │
                                           ▼
                                ┌─────────────────────┐
                                │  SQLite Storage     │
                                │    (library.db)     │
                                └─────────────────────┘
```

---

## ✨ Key Features

### 1. 🌐 Modern Web Circulation Dashboard
- **Circulation Desk**: Real-time book issue and return workflows with live student quota badges (`[●][●][○] 2/3`).
- **Inventory Metrics**: Instant counters for Total Cataloged Books, Books on Shelf, Active Borrowings, and Registered Students.
- **Instant Search**: Search records on-the-fly by Book Title, Author, Student Roll Number, Branch, or Contact ID.
- **Audit Logs**: Complete, immutable historical log of all book checkouts and check-ins.

### 2. ⚡ Embedded Core Java REST API (Zero Framework Bloat)
- Built natively using Java's built-in `com.sun.net.httpserver.HttpServer` with a fixed thread pool (`Executors.newFixedThreadPool(10)`) for non-blocking concurrent request handling.
- **RESTful Endpoints**:
  - `GET /api/health` — System status and inventory counters
  - `GET /api/books` | `POST /api/books` | `DELETE /api/books` — Book management
  - `GET /api/students` | `POST /api/students` | `DELETE /api/students` — Student directory
  - `POST /api/issue` — Transactional book checkout with validation
  - `POST /api/return` — Transactional book return
  - `GET /api/history` — Permanent circulation audit log

### 3. 🛡️ Strict Institutional Business Rules
- **Composite Student Key**: Students are isolated by `Branch + Year (1–4) + Roll Number`, preventing roll-number collisions across different departments.
- **Unique Contact Enforcement**: Enforces validated 10-digit mobile contact numbers per member.
- **Borrowing Quota Enforcer**: Strict limit of maximum **3 books** checked out per student simultaneously.
- **Referential Integrity**: Prohibits deletion of books that are currently issued out to students.

### 4. 💾 Robust SQLite Persistence with ACID Transactions
- All records persist locally in `library.db` via JDBC (`sqlite-jdbc`).
- Transactional integrity enforced via `conn.setAutoCommit(false)`, `commit()`, and `rollback()` to ensure zero data corruption during checkout/check-in operations.

---

## 📁 Project Directory Structure

```
library-management-system-java/
├── lib/                             # SQLite JDBC Driver dependencies
│   └── sqlite-jdbc-3.45.1.0.jar
├── src/
│   └── LibraryManagementSystem/     # Core Java Backend Architecture
│       ├── Main.java                # Application Entry Point (Dual Mode launcher)
│       ├── LibraryHttpServer.java   # Embedded HTTP Server & REST Controller
│       ├── DatabaseHelper.java      # JDBC SQLite persistence & queries
│       ├── BookService.java         # Book business logic & caching
│       ├── StudentService.java      # Student management & quota rules
│       ├── Book.java                # Book entity model
│       ├── Student.java             # Student entity model
│       ├── IssueRecord.java         # Checkout/Return transaction model
│       └── StudentCondition.java    # Search & validation predicate
├── web/                             # Web Frontend Assets
│   ├── index.html                   # Circulation dashboard layout
│   ├── style.css                    # Responsive styling & design system
│   └── script.js                    # Dynamic DOM rendering & REST API client
├── CampusLib_Presentation.pptx       # Institutional project presentation slides
├── CampusLib_Presentation.key        # Apple Keynote presentation slides
├── run.sh                           # One-click startup script (macOS/Linux)
├── run.bat                          # One-click startup script (Windows)
├── run.command                      # Double-click macOS launcher
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites
- **Java Development Kit (JDK 8 or higher)** installed (`javac -version`).
- Terminal or Command Prompt.

### Option A: One-Click Launch (Recommended)
- **macOS / Linux**:
  ```bash
  chmod +x run.sh
  ./run.sh
  ```
- **Windows**:
  Double-click `run.bat` or run in CMD:
  ```cmd
  run.bat
  ```

### Option B: Manual Compilation & Execution
1. **Clone the repository:**
   ```bash
   git clone https://github.com/alok0770/library-management-system-java.git
   cd library-management-system-java
   ```

2. **Compile the Java source files:**
   ```bash
   mkdir -p out
   javac -cp "lib/*:src" -d out src/LibraryManagementSystem/*.java
   ```

3. **Start the application:**
   ```bash
   java -cp "out:lib/*" LibraryManagementSystem.Main
   ```

4. **Access the Web Dashboard:**
   Open your browser and navigate to:
   👉 **http://localhost:8081** (or custom port passed as argument)

---

## 📊 Presentation & Documentation
The repository includes the official project presentation slides used for institutional demonstration:
- 📄 `CampusLib_Presentation.pptx` (Microsoft PowerPoint)
- 📄 `CampusLib_Presentation.key` (Apple Keynote)

---

## 👨‍💻 Author
**Alok Rana**  
- GitHub: [@alok0770](https://github.com/alok0770)  
- Portfolio: [portfolio-alok-2b96.vercel.app](https://portfolio-alok-2b96.vercel.app/)  
- LeetCode: [@Alok_0770](https://leetcode.com/u/Alok_0770/)  
