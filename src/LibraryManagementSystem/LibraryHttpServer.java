package LibraryManagementSystem;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibraryHttpServer {

    private static final int DEFAULT_PORT = 8081;
    private final int port;
    private final StudentService studentService;
    private final BookService bookService;
    private HttpServer server;

    public LibraryHttpServer(int port, StudentService studentService, BookService bookService) {
        this.port = port;
        this.studentService = studentService;
        this.bookService = bookService;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));

        // REST API Contexts
        server.createContext("/api/dashboard", new DashboardHandler(bookService, studentService));
        server.createContext("/api/books", new BooksHandler(bookService));
        server.createContext("/api/students", new StudentsHandler(studentService));
        server.createContext("/api/issue", new IssueHandler(bookService, studentService));
        server.createContext("/api/return", new ReturnHandler(bookService));
        server.createContext("/api/history", new HistoryHandler(bookService));
        server.createContext("/api/health", new HealthHandler(bookService, studentService));

        // Static Web UI File Handler (serves web/ directory)
        server.createContext("/", new StaticFileHandler());

        server.start();
        System.out.println("==========================================================");
        System.out.println("🚀 CampusLib HTTP Server is RUNNING at:");
        System.out.println("   👉 Web Dashboard: http://localhost:" + port);
        System.out.println("   👉 REST APIs:     http://localhost:" + port + "/api/health");
        System.out.println("   Connected to:     library.db (SQLite)");
        System.out.println("==========================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("CampusLib HTTP Server stopped.");
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        // Initialize SQLite Database
        DatabaseHelper.initializeDatabase();

        StudentService studentService = new StudentService();
        BookService bookService = new BookService(studentService);

        try {
            LibraryHttpServer server = new LibraryHttpServer(port, studentService, bookService);
            server.start();

            // Keep alive
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        } catch (IOException e) {
            System.err.println("Failed to start server on port " + port + ": " + e.getMessage());
        }
    }

    // ==========================================================
    // 1. Static Web Files Handler
    // ==========================================================
    static class StaticFileHandler implements HttpHandler {
        private final File webRoot;

        public StaticFileHandler() {
            // Locate web/ folder relative to project root or user working directory
            File candidate = new File("web");
            if (!candidate.exists() || !candidate.isDirectory()) {
                candidate = new File(System.getProperty("user.dir"), "web");
            }
            this.webRoot = candidate;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Prevent path traversal
            if (path.contains("..")) {
                sendJsonResponse(exchange, 403, "{\"error\":\"Access Denied\"}");
                return;
            }

            File targetFile = new File(webRoot, path.substring(1));
            if (!targetFile.exists() || targetFile.isDirectory()) {
                // If not found, fallback to index.html for SPA-style routing
                targetFile = new File(webRoot, "index.html");
            }

            if (!targetFile.exists()) {
                String notFound = "<h1>404 Not Found</h1><p>File not found: " + escapeJson(path) + "</p>";
                byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            String mimeType = getMimeType(targetFile.getName());
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.sendResponseHeaders(200, targetFile.length());

            try (InputStream is = new FileInputStream(targetFile);
                 OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
        }

        private String getMimeType(String filename) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".ico")) return "image/x-icon";
            return "text/plain; charset=UTF-8";
        }
    }

    // ==========================================================
    // 2. Dashboard Endpoint (Lightweight On-Demand Stats & Active Loans)
    // ==========================================================
    static class DashboardHandler implements HttpHandler {
        private final BookService bookService;
        private final StudentService studentService;

        public DashboardHandler(BookService bookService, StudentService studentService) {
            this.bookService = bookService;
            this.studentService = studentService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            ArrayList<Book> allBooks = bookService.getAllBooks();
            ArrayList<Book> borrowedBooks = new ArrayList<>();
            for (Book b : allBooks) {
                if (b.isIssued()) {
                    borrowedBooks.add(b);
                }
            }

            int totalBooks = allBooks.size();
            int totalIssued = borrowedBooks.size();
            int available = totalBooks - totalIssued;
            int totalStudents = studentService.getAllStudents().size();

            StringBuilder json = new StringBuilder("{");
            json.append("\"totalBooks\":").append(totalBooks).append(",")
                .append("\"availableBooks\":").append(available).append(",")
                .append("\"issuedBooks\":").append(totalIssued).append(",")
                .append("\"totalStudents\":").append(totalStudents).append(",")
                .append("\"borrowedBooks\":[");

            for (int i = 0; i < borrowedBooks.size(); i++) {
                Book b = borrowedBooks.get(i);
                json.append("{")
                        .append("\"id\":").append(b.getBookId()).append(",")
                        .append("\"title\":\"").append(escapeJson(b.getTitle())).append("\",")
                        .append("\"author\":\"").append(escapeJson(b.getAuthor())).append("\",")
                        .append("\"isIssued\":true,");

                if (b.getIssuedTo() != null) {
                    Student s = b.getIssuedTo();
                    json.append("\"issuedTo\":{")
                            .append("\"name\":\"").append(escapeJson(s.getName())).append("\",")
                            .append("\"branch\":\"").append(escapeJson(s.getBranch())).append("\",")
                            .append("\"year\":").append(s.getYear()).append(",")
                            .append("\"rollNo\":").append(s.getRollNo()).append(",")
                            .append("\"phone\":\"").append(escapeJson(s.getPhoneNumber())).append("\"")
                            .append("},");
                } else {
                    json.append("\"issuedTo\":null,");
                }

                json.append("\"issueDate\":\"").append(escapeJson(b.getIssueDate() != null ? b.getIssueDate() : "")).append("\"");
                json.append("}");
                if (i < borrowedBooks.size() - 1) json.append(",");
            }

            json.append("]}");
            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    // ==========================================================
    // 3. Health Endpoint
    // ==========================================================
    static class HealthHandler implements HttpHandler {
        private final BookService bookService;
        private final StudentService studentService;

        public HealthHandler(BookService bookService, StudentService studentService) {
            this.bookService = bookService;
            this.studentService = studentService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String json = "{"
                    + "\"status\":\"ok\","
                    + "\"database\":\"SQLite\","
                    + "\"totalBooks\":" + bookService.getAllBooks().size() + ","
                    + "\"totalStudents\":" + studentService.getAllStudents().size()
                    + "}";
            sendJsonResponse(exchange, 200, json);
        }
    }

    // ==========================================================
    // 3. Books API: GET, POST, DELETE
    // ==========================================================
    static class BooksHandler implements HttpHandler {
        private final BookService bookService;

        public BooksHandler(BookService bookService) {
            this.bookService = bookService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod().toUpperCase();

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equals(method)) {
                ArrayList<Book> books = bookService.getAllBooks();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < books.size(); i++) {
                    Book b = books.get(i);
                    json.append("{")
                            .append("\"id\":").append(b.getBookId()).append(",")
                            .append("\"title\":\"").append(escapeJson(b.getTitle())).append("\",")
                            .append("\"author\":\"").append(escapeJson(b.getAuthor())).append("\",")
                            .append("\"isIssued\":").append(b.isIssued()).append(",");

                    if (b.isIssued() && b.getIssuedTo() != null) {
                        Student s = b.getIssuedTo();
                        json.append("\"issuedTo\":{")
                                .append("\"name\":\"").append(escapeJson(s.getName())).append("\",")
                                .append("\"branch\":\"").append(escapeJson(s.getBranch())).append("\",")
                                .append("\"year\":").append(s.getYear()).append(",")
                                .append("\"rollNo\":").append(s.getRollNo()).append(",")
                                .append("\"phone\":\"").append(escapeJson(s.getPhoneNumber())).append("\"")
                                .append("},");
                    } else {
                        json.append("\"issuedTo\":null,");
                    }

                    if (b.getIssueDate() != null) {
                        json.append("\"issueDate\":\"").append(escapeJson(b.getIssueDate())).append("\"");
                    } else {
                        json.append("\"issueDate\":null");
                    }
                    json.append("}");
                    if (i < books.size() - 1) json.append(",");
                }
                json.append("]");
                sendJsonResponse(exchange, 200, json.toString());
                return;
            }

            if ("POST".equals(method)) {
                String body = readRequestBody(exchange);
                Map<String, String> data = parseJson(body);

                try {
                    int id = Integer.parseInt(data.getOrDefault("id", "0"));
                    String title = data.getOrDefault("title", "");
                    String author = data.getOrDefault("author", "");

                    if (id <= 0) {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Book ID must be a positive integer!\"}");
                        return;
                    }

                    StringBuilder error = new StringBuilder();
                    if (bookService.registerBook(id, title, author, error)) {
                        sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Book added successfully to catalog!\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(error.toString()) + "\"}");
                    }
                } catch (NumberFormatException e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid Book ID!\"}");
                }
                return;
            }

            if ("DELETE".equals(method)) {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                String idStr = query.get("id");
                if (idStr == null) {
                    // Try body
                    Map<String, String> bodyMap = parseJson(readRequestBody(exchange));
                    idStr = bodyMap.get("id");
                }

                if (idStr == null) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Book ID parameter required!\"}");
                    return;
                }

                try {
                    int id = Integer.parseInt(idStr);
                    StringBuilder error = new StringBuilder();
                    if (bookService.removeBook(id, error)) {
                        sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Book deleted successfully!\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(error.toString()) + "\"}");
                    }
                } catch (NumberFormatException e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid Book ID!\"}");
                }
                return;
            }

            sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
        }
    }

    // ==========================================================
    // 4. Students API: GET, POST, DELETE
    // ==========================================================
    static class StudentsHandler implements HttpHandler {
        private final StudentService studentService;

        public StudentsHandler(StudentService studentService) {
            this.studentService = studentService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod().toUpperCase();

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equals(method)) {
                ArrayList<Student> students = studentService.getAllStudents();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < students.size(); i++) {
                    Student s = students.get(i);
                    json.append("{")
                            .append("\"name\":\"").append(escapeJson(s.getName())).append("\",")
                            .append("\"branch\":\"").append(escapeJson(s.getBranch())).append("\",")
                            .append("\"year\":").append(s.getYear()).append(",")
                            .append("\"rollNo\":").append(s.getRollNo()).append(",")
                            .append("\"phone\":\"").append(escapeJson(s.getPhoneNumber())).append("\"")
                            .append("}");
                    if (i < students.size() - 1) json.append(",");
                }
                json.append("]");
                sendJsonResponse(exchange, 200, json.toString());
                return;
            }

            if ("POST".equals(method)) {
                String body = readRequestBody(exchange);
                Map<String, String> data = parseJson(body);

                try {
                    String name = data.getOrDefault("name", "");
                    String branch = data.getOrDefault("branch", "");
                    int year = Integer.parseInt(data.getOrDefault("year", "0"));
                    int rollNo = Integer.parseInt(data.getOrDefault("rollNo", "0"));
                    String phone = data.getOrDefault("phone", "");

                    StringBuilder error = new StringBuilder();
                    if (studentService.registerStudent(name, branch, year, rollNo, phone, error)) {
                        sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Student registered successfully!\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(error.toString()) + "\"}");
                    }
                } catch (NumberFormatException e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid Year or Roll Number!\"}");
                }
                return;
            }

            if ("DELETE".equals(method)) {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                String branch = query.get("branch");
                String yearStr = query.get("year");
                String rollNoStr = query.get("rollNo");

                if (branch == null || yearStr == null || rollNoStr == null) {
                    Map<String, String> bodyMap = parseJson(readRequestBody(exchange));
                    branch = bodyMap.get("branch");
                    yearStr = bodyMap.get("year");
                    rollNoStr = bodyMap.get("rollNo");
                }

                if (branch == null || yearStr == null || rollNoStr == null) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"branch, year, and rollNo parameters required!\"}");
                    return;
                }

                try {
                    int year = Integer.parseInt(yearStr);
                    int rollNo = Integer.parseInt(rollNoStr);
                    StringBuilder error = new StringBuilder();
                    if (studentService.removeStudent(branch, year, rollNo, error)) {
                        sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Student removed successfully!\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(error.toString()) + "\"}");
                    }
                } catch (NumberFormatException e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid year or roll number!\"}");
                }
                return;
            }

            sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
        }
    }

    // ==========================================================
    // 5. Issue Book API: POST
    // ==========================================================
    static class IssueHandler implements HttpHandler {
        private final BookService bookService;
        private final StudentService studentService;

        public IssueHandler(BookService bookService, StudentService studentService) {
            this.bookService = bookService;
            this.studentService = studentService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);

            try {
                int bookId = Integer.parseInt(data.getOrDefault("bookId", "0"));
                String branch = data.getOrDefault("branch", "");
                int year = Integer.parseInt(data.getOrDefault("year", "0"));
                int rollNo = Integer.parseInt(data.getOrDefault("rollNo", "0"));

                StringBuilder error = new StringBuilder();
                if (bookService.issueBookDirect(bookId, branch, year, rollNo, studentService, error)) {
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Book issued successfully!\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(error.toString()) + "\"}");
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid Book ID, Year, or Roll Number!\"}");
            }
        }
    }

    // ==========================================================
    // 6. Return Book API: POST
    // ==========================================================
    static class ReturnHandler implements HttpHandler {
        private final BookService bookService;

        public ReturnHandler(BookService bookService) {
            this.bookService = bookService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> data = parseJson(body);

            try {
                int bookId = Integer.parseInt(data.getOrDefault("bookId", "0"));
                StringBuilder error = new StringBuilder();
                if (bookService.returnBookDirect(bookId, error)) {
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Book returned successfully!\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(error.toString()) + "\"}");
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid Book ID!\"}");
            }
        }
    }

    // ==========================================================
    // 7. Audit History API: GET
    // ==========================================================
    static class HistoryHandler implements HttpHandler {
        private final BookService bookService;

        public HistoryHandler(BookService bookService) {
            this.bookService = bookService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            ArrayList<IssueRecord> history = bookService.getAllHistory();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < history.size(); i++) {
                IssueRecord r = history.get(i);
                boolean isStillIssued = "Currently Issued".equalsIgnoreCase(r.getReturnDate());
                json.append("{")
                        .append("\"bookId\":").append(r.getBookId()).append(",")
                        .append("\"bookTitle\":\"").append(escapeJson(r.getBookTitle())).append("\",")
                        .append("\"studentName\":\"").append(escapeJson(r.getStudentName())).append("\",")
                        .append("\"branch\":\"").append(escapeJson(r.getBranch())).append("\",")
                        .append("\"year\":").append(r.getYear()).append(",")
                        .append("\"rollNo\":").append(r.getRollNo()).append(",")
                        .append("\"phone\":\"").append(escapeJson(r.getPhoneNumber())).append("\",")
                        .append("\"issueDate\":\"").append(escapeJson(r.getIssueDate())).append("\",")
                        .append("\"returnDate\":\"").append(escapeJson(r.getReturnDate())).append("\",")
                        .append("\"status\":\"").append(isStillIssued ? "Borrowing" : "Returned").append("\"")
                        .append("}");
                if (i < history.size() - 1) json.append(",");
            }
            json.append("]");
            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    // ==========================================================
    // Utility Helpers: CORS, JSON, Query Parsing
    // ==========================================================
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJsonResponse(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1).trim();
        }

        // Match "key"\s*:\s*("value"|true|false|number)
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|[^,\\s}]+)");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String val = matcher.group(2).trim();
            if (val.startsWith("\"") && val.endsWith("\"")) {
                val = val.substring(1, val.length() - 1);
            }
            map.put(key, val);
        }
        return map;
    }

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(key, val);
            }
        }
        return map;
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
