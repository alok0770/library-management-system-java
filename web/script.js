// ==========================================================
// CampusLib - Library Dashboard
// Talks to the Java backend through /api (lazy loaded per tab).
// Only the presentation layer changed; every endpoint,
// request body and function name is the same as before.
// ==========================================================

const API_BASE = window.location.protocol === 'file:' ? 'http://localhost:8080/api' : '/api';
let isBackendConnected = false;
let currentActiveTab = 'dashboard';

// In-memory cached state
let books = [];
let students = [];
let historyRecords = [];
let dashboardData = {
    totalBooks: 0,
    availableBooks: 0,
    issuedBooks: 0,
    totalStudents: 0,
    borrowedBooks: []
};

// Lazy loading tracker: a tab fetches its data only when first opened
const loadedTabs = {
    dashboard: false,
    books: false,
    students: false,
    history: false
};

// Muted avatar colors (white initials stay readable on all of them)
const AVATAR_COLORS = [
    "#5b7b6f", "#7a6a58", "#6b6f8f", "#8a5a4a",
    "#4f6b7a", "#7b6b8a", "#5f7a4f", "#8a7a4a"
];

document.addEventListener("DOMContentLoaded", async () => {
    initNavigation();
    initDateDisplay();
    setupLiveHints();
    setupModalKeybinds();

    // Only the lightweight dashboard summary is loaded up front
    await loadDashboardData();
});

// ==========================================================
// 0. API loaders (on demand, per tab)
// ==========================================================

async function loadDashboardData(force = false) {
    if (loadedTabs.dashboard && !force) return;

    try {
        const res = await fetch(`${API_BASE}/dashboard`);
        if (res.ok) {
            dashboardData = await res.json();
            isBackendConnected = true;
            updateConnectionUI(true);
            renderStatsFromDashboard(dashboardData);
            renderDashboardIssuedTable(dashboardData.borrowedBooks || []);
            loadedTabs.dashboard = true;
            return;
        }
    } catch (err) {
        console.warn("Backend not reachable for dashboard:", err);
        isBackendConnected = false;
        updateConnectionUI(false);
    }
}

async function loadBooksData(force = false) {
    if (loadedTabs.books && !force) {
        renderBooksTable();
        return;
    }

    const tbody = document.getElementById("booksTableBody");
    if (tbody && !books.length) {
        tbody.innerHTML = emptyRow(6, "Loading books…");
    }

    try {
        const res = await fetch(`${API_BASE}/books`);
        if (res.ok) {
            books = await res.json();
            isBackendConnected = true;
            updateConnectionUI(true);
            loadedTabs.books = true;
            renderBooksTable();
            const navB = document.getElementById("navBooksCount");
            if (navB) navB.innerText = books.length;
        }
    } catch (err) {
        console.warn("Failed to load books catalog:", err);
    }
}

async function loadStudentsData(force = false) {
    if (loadedTabs.students && !force) {
        renderStudentsTable();
        return;
    }

    const tbody = document.getElementById("studentsTableBody");
    if (tbody && !students.length) {
        tbody.innerHTML = emptyRow(7, "Loading students…");
    }

    try {
        const res = await fetch(`${API_BASE}/students`);
        if (res.ok) {
            students = await res.json();
            isBackendConnected = true;
            updateConnectionUI(true);
            loadedTabs.students = true;
            renderStudentsTable();
            const navS = document.getElementById("navStudentsCount");
            if (navS) navS.innerText = students.length;
        }
    } catch (err) {
        console.warn("Failed to load student directory:", err);
    }
}

async function loadHistoryData(force = false) {
    if (loadedTabs.history && !force) {
        renderHistoryTable();
        return;
    }

    const tbody = document.getElementById("historyTableBody");
    if (tbody && !historyRecords.length) {
        tbody.innerHTML = emptyRow(7, "Loading log…");
    }

    try {
        const res = await fetch(`${API_BASE}/history`);
        if (res.ok) {
            historyRecords = await res.json();
            isBackendConnected = true;
            updateConnectionUI(true);
            loadedTabs.history = true;
            renderHistoryTable();
        }
    } catch (err) {
        console.warn("Failed to load history records:", err);
    }
}

// Books and students are needed for the live lookups on the circulation desk
async function ensureCirculationDataLoaded() {
    const promises = [];
    if (!loadedTabs.books) promises.push(loadBooksData());
    if (!loadedTabs.students) promises.push(loadStudentsData());
    if (promises.length > 0) {
        await Promise.all(promises);
    }
}

function updateConnectionUI(connected) {
    const card = document.getElementById("systemCard");
    const statusText = document.querySelector(".sc-status");
    const engineName = document.querySelector(".sc-engine-name");

    if (card) card.dataset.state = connected ? "online" : "offline";
    if (statusText) statusText.innerText = connected ? "Connected" : "Server offline";
    if (engineName) engineName.innerText = connected ? "SQLite · port 8080" : "Start the Java server";
}

// ==========================================================
// 1. Navigation, drawer & tab switching
// ==========================================================
function initNavigation() {
    document.querySelectorAll(".nav-item").forEach(item => {
        item.addEventListener("click", () => {
            switchTab(item.getAttribute("data-tab"));
        });
    });
}

function openSidebar() {
    document.getElementById("sidebar")?.classList.add("open");
    document.getElementById("scrim")?.classList.add("show");
}

function closeSidebar() {
    document.getElementById("sidebar")?.classList.remove("open");
    document.getElementById("scrim")?.classList.remove("show");
}

async function switchTab(tabId) {
    currentActiveTab = tabId;
    closeSidebar();

    document.querySelectorAll(".nav-item").forEach(btn => {
        btn.classList.toggle("active", btn.getAttribute("data-tab") === tabId);
    });

    document.querySelectorAll(".tab-pane").forEach(pane => {
        pane.classList.toggle("active", pane.id === tabId);
    });

    const meta = {
        "dashboard": { breadcrumb: "Dashboard", heading: "Library Dashboard" },
        "books": { breadcrumb: "Books Catalog", heading: "Books Catalog" },
        "students": { breadcrumb: "Student Directory", heading: "Student Directory" },
        "issue-return": { breadcrumb: "Circulation Desk", heading: "Circulation Desk" },
        "history": { breadcrumb: "Audit History", heading: "Audit History" }
    };

    if (meta[tabId]) {
        const bc = document.getElementById("breadcrumbCurrent");
        const ph = document.getElementById("pageHeading");
        if (bc) bc.innerText = meta[tabId].breadcrumb;
        if (ph) ph.innerText = meta[tabId].heading;
    }

    window.scrollTo({ top: 0 });

    if (tabId === 'dashboard') {
        await loadDashboardData();
    } else if (tabId === 'books') {
        await loadBooksData();
    } else if (tabId === 'students') {
        await loadStudentsData();
    } else if (tabId === 'history') {
        await loadHistoryData();
    } else if (tabId === 'issue-return') {
        await ensureCirculationDataLoaded();
    }
}

// dd-MM-yyyy
function getTodayString() {
    const today = new Date();
    const d = String(today.getDate()).padStart(2, '0');
    const m = String(today.getMonth() + 1).padStart(2, '0');
    const y = today.getFullYear();
    return `${d}-${m}-${y}`;
}

function initDateDisplay() {
    const dateEl = document.getElementById("dateDisplay");
    if (!dateEl) return;
    const options = { weekday: 'long', day: 'numeric', month: 'short', year: 'numeric' };
    dateEl.innerText = new Date().toLocaleDateString('en-GB', options);
}

// ==========================================================
// 2. Helpers
// ==========================================================
function emptyRow(cols, html) {
    return `<tr><td colspan="${cols}" class="table-empty">${html}</td></tr>`;
}

function getAvatar(name) {
    name = name || "Student";
    const parts = name.trim().split(/\s+/);
    let initials = "";
    if (parts.length === 1) {
        initials = parts[0].substring(0, 2).toUpperCase();
    } else {
        initials = (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    }

    let hash = 0;
    for (let i = 0; i < name.length; i++) {
        hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    const color = AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];

    return `<div class="avatar" style="background: ${color};">${escapeHtml(initials)}</div>`;
}

function countBorrowedBooks(student) {
    return books.filter(b => b.isIssued && b.issuedTo &&
        b.issuedTo.branch.toUpperCase() === student.branch.toUpperCase() &&
        b.issuedTo.year === student.year &&
        b.issuedTo.rollNo === student.rollNo
    ).length;
}

function renderQuotaWidget(count) {
    const d1 = count >= 1 ? (count === 3 ? 'full' : 'filled') : '';
    const d2 = count >= 2 ? (count === 3 ? 'full' : 'filled') : '';
    const d3 = count >= 3 ? 'full' : '';

    return `
        <div class="quota-box" title="${count} of 3 books borrowed">
            <span class="quota-dot ${d1}"></span>
            <span class="quota-dot ${d2}"></span>
            <span class="quota-dot ${d3}"></span>
            <span class="quota-text">${count}/3</span>
        </div>
    `;
}

// ==========================================================
// 3. Render functions
// ==========================================================

function renderStatsFromDashboard(data) {
    const elTotal = document.getElementById("statTotalBooks");
    const elAvail = document.getElementById("statAvailableBooks");
    const elIssued = document.getElementById("statIssuedBooks");
    const elStudents = document.getElementById("statTotalStudents");

    if (elTotal) elTotal.innerText = data.totalBooks ?? 0;
    if (elAvail) elAvail.innerText = data.availableBooks ?? 0;
    if (elIssued) elIssued.innerText = data.issuedBooks ?? 0;
    if (elStudents) elStudents.innerText = data.totalStudents ?? 0;

    const navB = document.getElementById("navBooksCount");
    const navS = document.getElementById("navStudentsCount");
    if (navB) navB.innerText = data.totalBooks ?? 0;
    if (navS) navS.innerText = data.totalStudents ?? 0;
}

// 3.2 Dashboard: currently borrowed
function renderDashboardIssuedTable(issuedList) {
    const tbody = document.getElementById("dashboardIssuedTableBody");
    if (!tbody) return;

    if (!issuedList || issuedList.length === 0) {
        tbody.innerHTML = emptyRow(5,
            `<strong>No books are out right now</strong><small>Every copy is back on the shelf.</small>`);
        return;
    }

    tbody.innerHTML = issuedList.map(book => {
        const s = book.issuedTo;
        return `
            <tr>
                <td data-label="Book">
                    <div>
                        <strong>${escapeHtml(book.title)}</strong>
                        <div class="sub-row">
                            <span class="chip">#${book.id}</span>
                            <span>by ${escapeHtml(book.author)}</span>
                        </div>
                    </div>
                </td>
                <td data-label="Borrower">
                    ${s ? `
                    <div class="student-cell">
                        ${getAvatar(s.name)}
                        <div class="student-info">
                            <strong>${escapeHtml(s.name)}</strong>
                            <small>${escapeHtml(s.phone)}</small>
                        </div>
                    </div>` : '<span class="muted">Unknown</span>'}
                </td>
                <td data-label="Class">
                    ${s ? `<div><span class="chip chip-branch">${escapeHtml(s.branch)}</span>
                        <span class="sub">Year ${s.year} · Roll ${s.rollNo}</span></div>` : '<span class="muted">-</span>'}
                </td>
                <td data-label="Issued"><span class="date-text">${escapeHtml(book.issueDate || 'Today')}</span></td>
                <td class="cell-action-td">
                    <div class="cell-actions">
                        <button class="btn btn-sm btn-secondary" type="button" onclick="quickReturn(${book.id})" title="Return book to shelf">Check in</button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

// 3.3 Books table
function renderBooksTable() {
    const tbody = document.getElementById("booksTableBody");
    if (!tbody) return;

    const search = (document.getElementById("bookSearchInput")?.value || "").toLowerCase().trim();
    const filter = document.getElementById("bookFilterStatus")?.value || "all";

    const filtered = books.filter(book => {
        const matchesSearch = String(book.id).includes(search) ||
            book.title.toLowerCase().includes(search) ||
            book.author.toLowerCase().includes(search);

        if (!matchesSearch) return false;
        if (filter === "available") return !book.isIssued;
        if (filter === "issued") return book.isIssued;
        return true;
    });

    if (filtered.length === 0) {
        tbody.innerHTML = emptyRow(6,
            `<strong>No books found</strong><small>Try a different search or add a new book.</small>`);
        return;
    }

    tbody.innerHTML = filtered.map(book => {
        const statusBadge = book.isIssued
            ? `<span class="status status-issued">Issued</span>`
            : `<span class="status status-available">Available</span>`;

        let borrowerHtml = `<span class="muted">On the shelf</span>`;
        if (book.isIssued && book.issuedTo) {
            borrowerHtml = `
                <div class="student-cell">
                    ${getAvatar(book.issuedTo.name)}
                    <div class="student-info">
                        <strong>${escapeHtml(book.issuedTo.name)}</strong>
                        <small>${escapeHtml(book.issuedTo.branch)} · Yr ${book.issuedTo.year} · Roll ${book.issuedTo.rollNo}</small>
                    </div>
                </div>
            `;
        }

        const actionBtn = book.isIssued
            ? `<button class="btn btn-sm btn-secondary" type="button" onclick="quickReturn(${book.id})">Check in</button>`
            : `<button class="btn btn-sm btn-primary" type="button" onclick="quickIssue(${book.id})">Issue</button>`;

        return `
            <tr>
                <td data-label="ID"><span class="chip">#${book.id}</span></td>
                <td data-label="Book">
                    <div>
                        <strong>${escapeHtml(book.title)}</strong>
                        <span class="sub">${escapeHtml(book.author)}</span>
                    </div>
                </td>
                <td data-label="Status">${statusBadge}</td>
                <td data-label="Borrower">${borrowerHtml}</td>
                <td data-label="Issued on">
                    ${book.issueDate ? `<span class="date-text">${escapeHtml(book.issueDate)}</span>` : '<span class="muted">-</span>'}
                </td>
                <td class="cell-action-td">
                    <div class="cell-actions">
                        ${actionBtn}
                        <button class="btn-danger-outline" type="button" onclick="deleteBook(${book.id})" title="Delete book" aria-label="Delete book ${book.id}">&times;</button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

function filterBooks() {
    renderBooksTable();
}

// 3.4 Students table
function renderStudentsTable() {
    const tbody = document.getElementById("studentsTableBody");
    if (!tbody) return;

    const search = (document.getElementById("studentSearchInput")?.value || "").toLowerCase().trim();
    const branchFilter = document.getElementById("studentFilterBranch")?.value || "all";

    const filtered = students.filter(s => {
        const matchesSearch = s.name.toLowerCase().includes(search) ||
            s.branch.toLowerCase().includes(search) ||
            String(s.rollNo).includes(search) ||
            s.phone.includes(search);

        if (!matchesSearch) return false;
        if (branchFilter !== "all" && s.branch.toUpperCase() !== branchFilter.toUpperCase()) return false;
        return true;
    });

    if (filtered.length === 0) {
        tbody.innerHTML = emptyRow(7,
            `<strong>No students found</strong><small>Try a different search or register a student.</small>`);
        return;
    }

    tbody.innerHTML = filtered.map(s => {
        const borrowedCount = countBorrowedBooks(s);
        const branchAttr = escapeHtml(s.branch);

        return `
            <tr>
                <td data-label="Student">
                    <div class="student-cell">
                        ${getAvatar(s.name)}
                        <div class="student-info"><strong>${escapeHtml(s.name)}</strong></div>
                    </div>
                </td>
                <td data-label="Branch"><span class="chip chip-branch">${branchAttr}</span></td>
                <td data-label="Year">Year ${s.year}</td>
                <td data-label="Roll no."><strong>#${s.rollNo}</strong></td>
                <td data-label="Phone"><span class="mono">+91 ${escapeHtml(s.phone)}</span></td>
                <td data-label="Quota">${renderQuotaWidget(borrowedCount)}</td>
                <td class="cell-action-td">
                    <div class="cell-actions">
                        <button class="btn btn-sm btn-outline" type="button" onclick="issueToStudent('${branchAttr}', ${s.year}, ${s.rollNo})">Issue book</button>
                        <button class="btn-danger-outline" type="button" onclick="deleteStudent('${branchAttr}', ${s.year}, ${s.rollNo})" title="Delete student" aria-label="Delete student">&times;</button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

function filterStudents() {
    renderStudentsTable();
}

// 3.5 History table
function renderHistoryTable() {
    const tbody = document.getElementById("historyTableBody");
    if (!tbody) return;

    const search = (document.getElementById("historySearchInput")?.value || "").toLowerCase().trim();

    const filtered = historyRecords.filter(r => {
        return r.bookTitle.toLowerCase().includes(search) ||
            String(r.bookId).includes(search) ||
            r.studentName.toLowerCase().includes(search) ||
            r.branch.toLowerCase().includes(search) ||
            String(r.rollNo).includes(search) ||
            r.phone.includes(search);
    });

    if (filtered.length === 0) {
        tbody.innerHTML = emptyRow(7,
            `<strong>No log entries found</strong><small>Issued and returned books will appear here.</small>`);
        return;
    }

    tbody.innerHTML = filtered.slice().reverse().map(r => {
        const isStillIssued = r.returnDate === "Currently Issued";
        const badge = isStillIssued
            ? `<span class="status status-issued">Borrowed</span>`
            : `<span class="status status-available">Returned</span>`;

        return `
            <tr>
                <td data-label="Book">
                    <div>
                        <strong>${escapeHtml(r.bookTitle)}</strong>
                        <div class="sub-row"><span class="chip">#${r.bookId}</span></div>
                    </div>
                </td>
                <td data-label="Student"><strong>${escapeHtml(r.studentName)}</strong></td>
                <td data-label="Class &amp; roll">
                    <div>
                        <span class="chip chip-branch">${escapeHtml(r.branch)}</span>
                        <span class="sub">Yr ${r.year} · Roll ${r.rollNo}</span>
                    </div>
                </td>
                <td data-label="Phone"><span class="mono">${escapeHtml(r.phone)}</span></td>
                <td data-label="Issued"><span class="date-text">${escapeHtml(r.issueDate)}</span></td>
                <td data-label="Returned">
                    ${isStillIssued
                        ? `<span class="date-pending">Pending return</span>`
                        : `<span class="date-text">${escapeHtml(r.returnDate)}</span>`}
                </td>
                <td data-label="Status">${badge}</td>
            </tr>
        `;
    }).join("");
}

function filterHistory() {
    renderHistoryTable();
}

// ==========================================================
// 4. Live hints & shortcuts (circulation desk)
// ==========================================================
function setupLiveHints() {
    const issueBookInput = document.getElementById("issueBookId");
    const issueBookHint = document.getElementById("issueBookLiveHint");

    if (issueBookInput && issueBookHint) {
        issueBookInput.addEventListener("input", async () => {
            const val = issueBookInput.value.trim();
            if (!val) {
                issueBookHint.innerHTML = "";
                return;
            }
            await ensureCirculationDataLoaded();
            const id = parseInt(val);
            const book = books.find(b => b.id === id);
            if (!book) {
                issueBookHint.innerHTML = `<span class="hint-muted">No book with ID #${id} in the catalog.</span>`;
            } else if (book.isIssued) {
                issueBookHint.innerHTML = `<span class="hint-err">Already issued to ${book.issuedTo ? escapeHtml(book.issuedTo.name) : 'a student'}.</span>`;
            } else {
                issueBookHint.innerHTML = `<span class="hint-ok">Available: "${escapeHtml(book.title)}" by ${escapeHtml(book.author)}</span>`;
            }
        });
    }

    const branchInput = document.getElementById("issueBranch");
    const yearInput = document.getElementById("issueYear");
    const rollInput = document.getElementById("issueRollNo");
    const studentHint = document.getElementById("issueStudentLiveHint");

    async function checkStudentLive() {
        if (!studentHint) return;
        const branch = (branchInput?.value || "").toUpperCase().trim();
        const year = parseInt(yearInput?.value || "0");
        const roll = parseInt(rollInput?.value || "0");

        if (!branch || !year || !roll) {
            studentHint.innerHTML = "";
            return;
        }

        await ensureCirculationDataLoaded();
        const student = students.find(s => s.branch === branch && s.year === year && s.rollNo === roll);
        if (!student) {
            studentHint.innerHTML = `<span class="hint-err">No student found in ${escapeHtml(branch)}, year ${year}, roll ${roll}.</span>`;
        } else {
            const borrowed = countBorrowedBooks(student);
            if (borrowed >= 3) {
                studentHint.innerHTML = `<span class="hint-err">Quota full: ${escapeHtml(student.name)} already has 3 of 3 books.</span>`;
            } else {
                studentHint.innerHTML = `<span class="hint-ok">${escapeHtml(student.name)} · ${escapeHtml(student.phone)} · Quota ${borrowed}/3</span>`;
            }
        }
    }

    [branchInput, yearInput, rollInput].forEach(inp => {
        if (inp) inp.addEventListener("input", checkStudentLive);
    });

    const returnInput = document.getElementById("returnBookId");
    const returnCard = document.getElementById("returnCardPreview");

    if (returnInput && returnCard) {
        returnInput.addEventListener("input", async () => {
            const val = returnInput.value.trim();
            if (!val) {
                returnCard.style.display = "none";
                return;
            }
            await ensureCirculationDataLoaded();
            const id = parseInt(val);
            const book = books.find(b => b.id === id);

            if (book && book.isIssued && book.issuedTo) {
                document.getElementById("previewBookTitle").innerText = book.title;
                document.getElementById("previewBookId").innerText = `#${book.id} · ${book.author}`;
                document.getElementById("previewBorrower").innerText = book.issuedTo.name;
                document.getElementById("previewClass").innerText = `${book.issuedTo.branch} · Yr ${book.issuedTo.year} · Roll ${book.issuedTo.rollNo}`;
                document.getElementById("previewDate").innerText = book.issueDate || "Today";
                returnCard.style.display = "block";
            } else {
                returnCard.style.display = "none";
            }
        });
    }
}

// Shortcuts
async function quickIssue(bookId) {
    switchTab("issue-return");
    const issueInput = document.getElementById("issueBookId");
    if (issueInput) {
        issueInput.value = bookId;
        issueInput.dispatchEvent(new Event("input"));
    }
    const branchInput = document.getElementById("issueBranch");
    if (branchInput) branchInput.focus();
}

async function issueToStudent(branch, year, rollNo) {
    switchTab("issue-return");
    const bInp = document.getElementById("issueBranch");
    const yInp = document.getElementById("issueYear");
    const rInp = document.getElementById("issueRollNo");
    if (bInp) bInp.value = branch;
    if (yInp) yInp.value = year;
    if (rInp) rInp.value = rollNo;

    if (rInp) rInp.dispatchEvent(new Event("input"));

    const issueInput = document.getElementById("issueBookId");
    if (issueInput) issueInput.focus();
}

function quickReturn(bookId) {
    processReturn(bookId);
}

// ==========================================================
// 5. Actions: add, issue, return, delete
// ==========================================================

async function handleAddBook(e) {
    e.preventDefault();
    const idInput = document.getElementById("newBookId");
    const titleInput = document.getElementById("newBookTitle");
    const authorInput = document.getElementById("newBookAuthor");

    const id = parseInt(idInput.value.trim());
    const title = titleInput.value.trim();
    const author = authorInput.value.trim();

    try {
        const res = await fetch(`${API_BASE}/books`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id, title, author })
        });
        const data = await res.json();
        if (res.ok && data.success) {
            closeModal("bookModal");
            document.getElementById("addBookForm").reset();
            loadedTabs.dashboard = false;
            loadedTabs.books = false;
            await refreshActiveTab();
            showToast(data.message || `"${title}" added to the catalog.`, "success");
        } else {
            showToast(data.message || "Failed to add book.", "error");
        }
    } catch (err) {
        showToast("Server communication error: " + err.message, "error");
    }
}

async function handleAddStudent(e) {
    e.preventDefault();
    const name = document.getElementById("newStudentName").value.toUpperCase().trim();
    const branch = document.getElementById("newStudentBranch").value.toUpperCase().trim();
    const year = parseInt(document.getElementById("newStudentYear").value);
    const rollNo = parseInt(document.getElementById("newStudentRollNo").value);
    const phone = document.getElementById("newStudentPhone").value.trim();

    if (phone.length !== 10 || !/^\d+$/.test(phone)) {
        showToast("Mobile number must be exactly 10 digits.", "error");
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/students`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, branch, year, rollNo, phone })
        });
        const data = await res.json();
        if (res.ok && data.success) {
            closeModal("studentModal");
            document.getElementById("addStudentForm").reset();
            loadedTabs.dashboard = false;
            loadedTabs.students = false;
            await refreshActiveTab();
            showToast(data.message || `${name} registered successfully.`, "success");
        } else {
            showToast(data.message || "Failed to register student.", "error");
        }
    } catch (err) {
        showToast("Server communication error: " + err.message, "error");
    }
}

async function handleIssueBook(e) {
    e.preventDefault();
    const bookId = parseInt(document.getElementById("issueBookId").value);
    const branch = document.getElementById("issueBranch").value.toUpperCase().trim();
    const year = parseInt(document.getElementById("issueYear").value);
    const rollNo = parseInt(document.getElementById("issueRollNo").value);

    try {
        const res = await fetch(`${API_BASE}/issue`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ bookId, branch, year, rollNo })
        });
        const data = await res.json();
        if (res.ok && data.success) {
            document.getElementById("issueForm").reset();
            const hintB = document.getElementById("issueBookLiveHint");
            const hintS = document.getElementById("issueStudentLiveHint");
            if (hintB) hintB.innerHTML = "";
            if (hintS) hintS.innerHTML = "";
            loadedTabs.dashboard = false;
            loadedTabs.books = false;
            loadedTabs.students = false;
            loadedTabs.history = false;
            await refreshActiveTab();
            showToast(data.message || `Book #${bookId} checked out.`, "success");
        } else {
            showToast(data.message || "Failed to issue book.", "error");
        }
    } catch (err) {
        showToast("Server error: " + err.message, "error");
    }
}

function handleReturnBook(e) {
    e.preventDefault();
    const bookId = parseInt(document.getElementById("returnBookId").value);
    processReturn(bookId);
}

async function processReturn(bookId) {
    try {
        const res = await fetch(`${API_BASE}/return`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ bookId })
        });
        const data = await res.json();
        if (res.ok && data.success) {
            const returnForm = document.getElementById("returnForm");
            if (returnForm) returnForm.reset();
            const returnCard = document.getElementById("returnCardPreview");
            if (returnCard) returnCard.style.display = "none";
            loadedTabs.dashboard = false;
            loadedTabs.books = false;
            loadedTabs.students = false;
            loadedTabs.history = false;
            await refreshActiveTab();
            showToast(data.message || `Book #${bookId} returned.`, "success");
        } else {
            showToast(data.message || "Failed to return book.", "error");
        }
    } catch (err) {
        showToast("Server error: " + err.message, "error");
    }
}

async function deleteBook(id) {
    if (!confirm(`Delete Book #${id} from the catalog?`)) {
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/books?id=${id}`, {
            method: "DELETE"
        });
        const data = await res.json();
        if (res.ok && data.success) {
            loadedTabs.dashboard = false;
            loadedTabs.books = false;
            await refreshActiveTab();
            showToast(data.message || `Book #${id} removed.`, "success");
        } else {
            showToast(data.message || "Failed to delete book.", "error");
        }
    } catch (err) {
        showToast("Server error: " + err.message, "error");
    }
}

async function deleteStudent(branch, year, rollNo) {
    if (!confirm(`Delete student ${branch}, year ${year}, roll ${rollNo}?`)) {
        return;
    }

    try {
        const url = `${API_BASE}/students?branch=${encodeURIComponent(branch)}&year=${year}&rollNo=${rollNo}`;
        const res = await fetch(url, {
            method: "DELETE"
        });
        const data = await res.json();
        if (res.ok && data.success) {
            loadedTabs.dashboard = false;
            loadedTabs.students = false;
            await refreshActiveTab();
            showToast(data.message || "Student removed.", "success");
        } else {
            showToast(data.message || "Failed to delete student.", "error");
        }
    } catch (err) {
        showToast("Server error: " + err.message, "error");
    }
}

// Refresh whichever tab is currently open
async function refreshActiveTab() {
    if (currentActiveTab === 'dashboard') {
        await loadDashboardData(true);
    } else if (currentActiveTab === 'books') {
        await loadBooksData(true);
    } else if (currentActiveTab === 'students') {
        await loadStudentsData(true);
    } else if (currentActiveTab === 'history') {
        await loadHistoryData(true);
    } else if (currentActiveTab === 'issue-return') {
        await loadDashboardData(true);
    }
}

// ==========================================================
// 6. Modals & toasts
// ==========================================================
function openModal(id) {
    const modal = document.getElementById(id);
    if (!modal) return;
    modal.classList.add("active");
    const firstInput = modal.querySelector("input");
    if (firstInput && window.matchMedia("(min-width: 721px)").matches) {
        setTimeout(() => firstInput.focus(), 50);
    }
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) modal.classList.remove("active");
}

function setupModalKeybinds() {
    window.addEventListener("click", (e) => {
        if (e.target.classList.contains("modal-overlay")) {
            e.target.classList.remove("active");
        }
    });

    window.addEventListener("keydown", (e) => {
        if (e.key === "Escape") {
            document.querySelectorAll(".modal-overlay.active").forEach(m => m.classList.remove("active"));
            closeSidebar();
        }
    });

    // Reset the drawer if the window is resized to desktop width
    window.addEventListener("resize", () => {
        if (window.innerWidth > 900) closeSidebar();
    });
}

function showToast(message, type = "info") {
    const container = document.getElementById("toastContainer");
    if (!container) return;

    const toast = document.createElement("div");
    toast.className = `toast toast-${type === "error" ? "error" : "success"}`;
    toast.textContent = message;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = "0";
        toast.style.transform = "translateY(8px)";
        toast.style.transition = "all 0.25s ease";
        setTimeout(() => toast.remove(), 250);
    }, 3800);
}

function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str).replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
    })[m]);
}
