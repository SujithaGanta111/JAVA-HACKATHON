import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/*
 * CampusCart - Student Marketplace
 * Demo-only Java web application using the JDK built-in HTTP server.
 * Data is stored in memory and resets when the application stops.
 */
public class CampusCart {

    private static final int PORT = 8080;
    private static final Map<String, User> users = new ConcurrentHashMap<>();
    private static final Map<String, User> sessions = new ConcurrentHashMap<>();
    private static final List<Item> items = Collections.synchronizedList(new ArrayList<>());
    private static final List<Inquiry> inquiries = Collections.synchronizedList(new ArrayList<>());
    private static int nextItemId = 1;

    static class User {

        String name, email, password, role;

        User(String name, String email, String password, String role) {
            this.name = name;
            this.email = email;
            this.password = password;
            this.role = role;
        }
    }

    static class Item {

        int id;
        String sellerEmail, title, description, category, condition, price;
        boolean sold;

        Item(int id, String sellerEmail, String title, String description,
                String category, String condition, String price) {
            this.id = id;
            this.sellerEmail = sellerEmail;
            this.title = title;
            this.description = description;
            this.category = category;
            this.condition = condition;
            this.price = price;
            this.sold = false;
        }
    }

    static class Inquiry {

        int itemId;
        String buyerEmail, buyerName, question;

        Inquiry(int itemId, String buyerEmail, String buyerName, String question) {
            this.itemId = itemId;
            this.buyerEmail = buyerEmail;
            this.buyerName = buyerName;
            this.question = question;
        }
    }

    public static void main(String[] args) throws Exception {
        users.put("buyer@campus.com",
                new User("Demo Buyer", "buyer@campus.com", "buyer123", "buyer"));
        users.put("seller@campus.com",
                new User("Demo Seller", "seller@campus.com", "seller123", "seller"));

        addSample("seller@campus.com", "Engineering Mathematics Textbook",
                "Useful reference book for engineering mathematics. Kept neatly.", "Books", "Good", "250");
        addSample("seller@campus.com", "Scientific Calculator",
                "Working scientific calculator. All buttons are functional.", "Electronics", "Like New", "450");
        addSample("seller@campus.com", "Study Table",
                "Compact study table suitable for a hostel room.", "Furniture", "Used", "700");
        addSample("seller@campus.com", "College Backpack",
                "Spacious backpack with multiple compartments.", "Accessories", "Good", "350");

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", CampusCart::handle);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        System.out.println("CampusCart is running!");
        System.out.println("Open http://localhost:" + PORT);
        System.out.println("Demo buyer: buyer@campus.com / buyer123");
        System.out.println("Demo seller: seller@campus.com / seller123");
    }

    private static synchronized void addSample(String seller, String title, String description,
            String category, String condition, String price) {
        items.add(new Item(nextItemId++, seller, title, description, category, condition, price));
    }

    private static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        User user = currentUser(ex);

        try {
            if (path.equals("/") || path.equals("/login")) {
                if (user == null) {
                    loginPage(ex, "");
                } else {
                    redirect(ex, user.role.equals("seller") ? "/seller" : "/buyer");
                }
            } else if (path.equals("/login-submit") && method.equals("POST")) {
                login(ex);
            } else if (path.equals("/logout")) {
                logout(ex);
            } else if (path.equals("/buyer")) {
                buyerDashboard(ex, user);
            } else if (path.equals("/seller")) {
                sellerDashboard(ex, user);
            } else if (path.equals("/add-item") && method.equals("POST")) {
                addItem(ex, user);
            } else {
                sendResponse(ex, 404, "404 Not Found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(ex, 500, "500 Internal Server Error");
        }
    }

    private static User currentUser(HttpExchange ex) {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) {
            for (String c : cookie.split(";")) {
                String[] parts = c.trim().split("=");
                if (parts.length == 2 && parts[0].equals("session")) {
                    return sessions.get(parts[1]);
                }
            }
        }
        return null;
    }

    private static void login(HttpExchange ex) throws IOException {
        Map<String, String> body = parseBody(ex);
        String email = body.get("email");
        String password = body.get("password");
        User u = users.get(email);
        if (u != null && u.password.equals(password)) {
            String sessionId = UUID.randomUUID().toString();
            sessions.put(sessionId, u);
            ex.getResponseHeaders().set("Set-Cookie", "session=" + sessionId + "; Path=/");
            redirect(ex, u.role.equals("seller") ? "/seller" : "/buyer");
        } else {
            loginPage(ex, "Invalid email or password");
        }
    }

    private static void logout(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Set-Cookie", "session=; Path=/; Max-Age=0");
        redirect(ex, "/login");
    }

    private static void loginPage(HttpExchange ex, String error) throws IOException {
        String html = "<html><head><title>CampusCart - Login</title>"
                + "<style>body{font-family:Arial;background:#f4f6f9;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;}"
                + ".card{background:white;padding:30px;border-radius:8px;box-shadow:0 4px 12px rgba(0,0,0,0.1);width:320px;}"
                + "h2{margin-top:0;color:#333;text-align:center;}"
                + "input{width:100%;padding:10px;margin:8px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:4px;}"
                + "button{width:100%;background:#007bff;color:white;border:none;padding:10px;border-radius:4px;cursor:pointer;font-weight:bold;}"
                + "button:hover{background:#0056b3;}.err{color:red;font-size:14px;text-align:center;}"
                + ".demo{font-size:12px;color:#666;background:#f8f9fa;padding:8px;border-radius:4px;margin-top:10px;}"
                + "</style></head><body>"
                + "<div class='card'><h2>CampusCart Login</h2>"
                + (error.isEmpty() ? "" : "<div class='err'>" + error + "</div>")
                + "<form method='POST' action='/login-submit'>"
                + "<input type='email' name='email' placeholder='Email' required>"
                + "<input type='password' name='password' placeholder='Password' required>"
                + "<button type='submit'>Login</button></form>"
                + "<div class='demo'><b>Demo Accounts:</b><br>Buyer: buyer@campus.com / buyer123<br>Seller: seller@campus.com / seller123</div>"
                + "</div></body></html>";
        sendResponse(ex, 200, html);
    }

    private static void buyerDashboard(HttpExchange ex, User user) throws IOException {
        if (user == null || !user.role.equals("buyer")) {
            redirect(ex, "/login");
            return;
        }
        StringBuilder sb = new StringBuilder("<html><head><title>Buyer Dashboard - CampusCart</title>"
                + "<style>body{font-family:Arial;background:#f4f6f9;margin:0;padding:20px;}"
                + ".header{display:flex;justify-content:space-between;align-items:center;background:white;padding:15px 25px;border-radius:8px;box-shadow:0 2px 5px rgba(0,0,0,0.05);}"
                + ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:20px;margin-top:20px;}"
                + ".card{background:white;padding:15px;border-radius:8px;box-shadow:0 2px 5px rgba(0,0,0,0.05);}"
                + ".btn{background:#dc3545;color:white;border:none;padding:5px 10px;border-radius:4px;text-decoration:none;font-size:12px;}"
                + "</style></head><body>"
                + "<div class='header'><h2>Welcome, " + user.name + " (Buyer)</h2>"
                + "<a href='/logout' class='btn'>Logout</a></div>"
                + "<h3>Available Marketplace Items</h3><div class='grid'>");

        synchronized (items) {
            for (Item item : items) {
                sb.append("<div class='card'><h4>").append(item.title).append("</h4>")
                        .append("<p><b>Category:</b> ").append(item.category).append("</p>")
                        .append("<p><b>Condition:</b> ").append(item.condition).append("</p>")
                        .append("<p><b>Price:</b> ₹").append(item.price).append("</p>")
                        .append("<p>").append(item.description).append("</p>")
                        .append("<p style='font-size:12px;color:#777;'>Seller: ").append(item.sellerEmail).append("</p>")
                        .append("</div>");
            }
        }
        sb.append("</div></body></html>");
        sendResponse(ex, 200, sb.toString());
    }

    private static void sellerDashboard(HttpExchange ex, User user) throws IOException {
        if (user == null || !user.role.equals("seller")) {
            redirect(ex, "/login");
            return;
        }
        StringBuilder sb = new StringBuilder("<html><head><title>Seller Dashboard - CampusCart</title>"
                + "<style>body{font-family:Arial;background:#f4f6f9;margin:0;padding:20px;}"
                + ".header{display:flex;justify-content:space-between;align-items:center;background:white;padding:15px 25px;border-radius:8px;box-shadow:0 2px 5px rgba(0,0,0,0.05);}"
                + ".form-card, .card{background:white;padding:20px;border-radius:8px;box-shadow:0 2px 5px rgba(0,0,0,0.05);margin-top:20px;}"
                + "input, textarea, select{width:100%;padding:8px;margin:6px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:4px;}"
                + "button{background:#28a745;color:white;border:none;padding:10px 15px;border-radius:4px;cursor:pointer;font-weight:bold;}"
                + ".btn{background:#dc3545;color:white;border:none;padding:5px 10px;border-radius:4px;text-decoration:none;font-size:12px;}"
                + "</style></head><body>"
                + "<div class='header'><h2>Welcome, " + user.name + " (Seller)</h2>"
                + "<a href='/logout' class='btn'>Logout</a></div>"
                + "<div class='form-card'><h3>Add New Item</h3>"
                + "<form method='POST' action='/add-item'>"
                + "<input type='text' name='title' placeholder='Item Title' required>"
                + "<textarea name='description' placeholder='Description' rows='3' required></textarea>"
                + "<select name='category'><option>Books</option><option>Electronics</option><option>Furniture</option><option>Accessories</option><option>Others</option></select>"
                + "<select name='condition'><option>Brand New</option><option>Like New</option><option>Good</option><option>Used</option></select>"
                + "<input type='text' name='price' placeholder='Price in INR' required>"
                + "<button type='submit'>Post Item</button></form></div>"
                + "<h3>Your Listed Items</h3><div style='display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:20px;'>");

        synchronized (items) {
            for (Item item : items) {
                if (item.sellerEmail.equals(user.email)) {
                    sb.append("<div class='card'><h4>").append(item.title).append("</h4>")
                            .append("<p><b>Category:</b> ").append(item.category).append("</p>")
                            .append("<p><b>Price:</b> ₹").append(item.price).append("</p>")
                            .append("<p>").append(item.description).append("</p></div>");
                }
            }
        }
        sb.append("</div></body></html>");
        sendResponse(ex, 200, sb.toString());
    }

    private static void addItem(HttpExchange ex, User user) throws IOException {
        if (user == null || !user.role.equals("seller")) {
            redirect(ex, "/login");
            return;
        }
        Map<String, String> body = parseBody(ex);
        addSample(user.email, body.get("title"), body.get("description"),
                body.get("category"), body.get("condition"), body.get("price"));
        redirect(ex, "/seller");
    }

    private static Map<String, String> parseBody(HttpExchange ex) throws IOException {
        String query = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static void redirect(HttpExchange ex, String url) throws IOException {
        ex.getResponseHeaders().set("Location", url);
        ex.sendResponseHeaders(303, -1);
    }

    private static void sendResponse(HttpExchange ex, int code, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
