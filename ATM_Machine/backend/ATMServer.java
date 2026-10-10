import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REST API for the ATM. Uses the same OOP classes as the console version
 * (Account, Bank, Transaction, custom exceptions from ATM.java), so ALL the
 * banking logic stays in Java. The React app only shows the UI.
 *
 * No external libraries needed: uses the JDK's built-in HttpServer.
 *
 * Compile: javac ATM.java ATMServer.java
 * Run:     java ATMServer      (starts on http://localhost:5000)
 *
 * Demo accounts: 1001 / PIN 1234   and   1002 / PIN 4321
 */
public class ATMServer {

    private static final Bank bank = new Bank();
    private static final Map<String, Account> sessions = new ConcurrentHashMap<>();

    /** Request handler that returns a JSON string. */
    interface Handler {
        String handle(HttpExchange ex, String body) throws Exception;
    }

    /** Error with an HTTP status code. */
    static class ApiException extends Exception {
        final int status;
        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    public static void main(String[] args) throws IOException {
        bank.addAccount(new Account("1001", "Taa", 1234, 15000));
        bank.addAccount(new Account("1002", "Demo User", 4321, 5000));

        HttpServer server = HttpServer.create(new InetSocketAddress(5000), 0);
        server.createContext("/api/login", ex -> handle(ex, "POST", ATMServer::login));
        server.createContext("/api/balance", ex -> handle(ex, "GET", ATMServer::balance));
        server.createContext("/api/deposit", ex -> handle(ex, "POST", ATMServer::deposit));
        server.createContext("/api/withdraw", ex -> handle(ex, "POST", ATMServer::withdraw));
        server.createContext("/api/statement", ex -> handle(ex, "GET", ATMServer::statement));
        server.createContext("/api/change-pin", ex -> handle(ex, "POST", ATMServer::changePin));
        server.start();

        System.out.println("ATM API running on http://localhost:5000");
    }

    // ---------------- Endpoints ----------------

    private static String login(HttpExchange ex, String body) throws Exception {
        String accNo = field(body, "accountNumber").trim();
        String pinText = field(body, "pin").trim();

        Account acc = bank.findAccount(accNo);
        if (acc == null) {
            throw new ApiException(404, "Account not found");
        }

        int pin;
        try {
            pin = Integer.parseInt(pinText);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "PIN must be numeric.");
        }

        // authenticate() throws AccountLockedException after too many wrong attempts
        if (!acc.authenticate(pin)) {
            throw new ApiException(401, "Incorrect PIN. Attempts left: " + acc.attemptsLeft());
        }

        String token = UUID.randomUUID().toString();
        sessions.put(token, acc);
        return "{\"token\":\"" + token + "\",\"name\":\"" + esc(acc.getHolderName())
                + "\",\"balance\":" + acc.getBalance() + "}";
    }

    private static String balance(HttpExchange ex, String body) throws Exception {
        Account acc = authenticated(ex);
        return "{\"balance\":" + acc.getBalance() + "}";
    }

    private static String deposit(HttpExchange ex, String body) throws Exception {
        Account acc = authenticated(ex);
        double amount = parseAmount(body);
        acc.deposit(amount); // may throw InvalidAmountException
        return "{\"balance\":" + acc.getBalance() + ",\"message\":\""
                + String.format("Deposited Rs. %.2f successfully.", amount) + "\"}";
    }

    private static String withdraw(HttpExchange ex, String body) throws Exception {
        Account acc = authenticated(ex);
        double amount = parseAmount(body);
        acc.withdraw(amount); // may throw InvalidAmountException / InsufficientFundsException
        return "{\"balance\":" + acc.getBalance() + ",\"message\":\""
                + String.format("Withdrew Rs. %.2f. Please collect your cash.", amount) + "\"}";
    }

    private static String statement(HttpExchange ex, String body) throws Exception {
        Account acc = authenticated(ex);
        List<Transaction> list = acc.getMiniStatement();

        StringBuilder sb = new StringBuilder("{\"transactions\":[");
        for (int i = list.size() - 1; i >= 0; i--) { // latest first
            Transaction t = list.get(i);
            sb.append("{\"type\":\"").append(t.getType()).append("\"")
              .append(",\"amount\":").append(t.getAmount())
              .append(",\"balanceAfter\":").append(t.getBalanceAfter())
              .append(",\"date\":\"").append(Instant.ofEpochMilli(t.getTime())).append("\"}");
            if (i > 0) sb.append(",");
        }
        return sb.append("]}").toString();
    }

    private static String changePin(HttpExchange ex, String body) throws Exception {
        Account acc = authenticated(ex);
        int oldPin;
        int newPin;
        try {
            oldPin = Integer.parseInt(field(body, "oldPin").trim());
            newPin = Integer.parseInt(field(body, "newPin").trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "PIN must be numeric.");
        }
        if (!acc.changePin(oldPin, newPin)) {
            throw new ApiException(400, "PIN change failed. Check your current PIN and use exactly 4 digits.");
        }
        return "{\"message\":\"PIN changed successfully.\"}";
    }

    // ---------------- Helpers ----------------

    /** Common wrapper: CORS, method check, body reading, and exception -> HTTP status mapping. */
    private static void handle(HttpExchange ex, String method, Handler handler) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        int status = 200;
        String json;
        try {
            if (!ex.getRequestMethod().equalsIgnoreCase(method)) {
                throw new ApiException(405, "Method not allowed");
            }
            json = handler.handle(ex, readBody(ex));
        } catch (ApiException e) {
            status = e.status;
            json = errorJson(e.getMessage());
        } catch (InvalidAmountException | InsufficientFundsException e) {
            status = 400;
            json = errorJson(e.getMessage());
        } catch (AccountLockedException e) {
            status = 423;
            json = errorJson(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            status = 500;
            json = errorJson("Server error");
        }

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Account authenticated(HttpExchange ex) throws ApiException {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(401, "Please login first");
        }
        Account acc = sessions.get(header.substring(7));
        if (acc == null) {
            throw new ApiException(401, "Session expired. Please login again.");
        }
        return acc;
    }

    private static double parseAmount(String body) throws ApiException {
        try {
            return Double.parseDouble(field(body, "amount").trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Please enter a valid amount.");
        }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Tiny JSON reader for flat objects like {"amount":"500"} or {"amount":500}. Returns "" if missing. */
    private static String field(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(?:\"([^\"]*)\"|(-?[0-9.]+))").matcher(body);
        if (!m.find()) return "";
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    private static String errorJson(String message) {
        return "{\"error\":\"" + esc(message) + "\"}";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
