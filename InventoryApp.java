import java.sql.*;
import java.util.Scanner;

public class InventoryApp {

    // ---- change these to match your MySQL setup ----
    private static final String URL      = "jdbc:mysql://localhost:3306/retail_db";
    private static final String USER     = "root";
    private static final String PASSWORD = "your_password";

    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD)) {
            System.out.println("Connected to retail_db successfully.");
            int choice;
            do {
                System.out.println("\n===== INVENTORY MANAGEMENT SYSTEM =====");
                System.out.println("1. Insert product records");
                System.out.println("2. Show low-stock products by category (qty < 20, price DESC)");
                System.out.println("3. Update product stock");
                System.out.println("4. Search and modify product price");
                System.out.println("5. Total inventory value & conditional delete");
                System.out.println("6. Exit");
                System.out.print("Enter choice: ");
                choice = readInt();
                switch (choice) {
                    case 1: insertProducts(con);   break;
                    case 2: lowStockByCategory(con); break;
                    case 3: updateStock(con);      break;
                    case 4: modifyPrice(con);      break;
                    case 5: valueAndDelete(con);   break;
                    case 6: System.out.println("Goodbye!"); break;
                    default: System.out.println("Invalid choice.");
                }
            } while (choice != 6);
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
    }

    // (a) Insert records using PreparedStatement, handle duplicate IDs
    static void insertProducts(Connection con) {
        System.out.print("How many products to insert (at least 3)? ");
        int n = readInt();
        String sql = "INSERT INTO Product (product_id, product_name, category, quantity, price) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 1; i <= n; i++) {
                System.out.println("--- Product " + i + " ---");
                System.out.print("ID: ");        int id = readInt();
                System.out.print("Name: ");      String name = sc.nextLine().trim();
                System.out.print("Category: ");  String cat = sc.nextLine().trim();
                System.out.print("Quantity: ");  int qty = readInt();
                System.out.print("Price: ");     double price = readDouble();

                ps.setInt(1, id);
                ps.setString(2, name);
                ps.setString(3, cat);
                ps.setInt(4, qty);
                ps.setDouble(5, price);
                try {
                    ps.executeUpdate();
                    System.out.println("Product inserted.");
                } catch (SQLIntegrityConstraintViolationException dup) {
                    System.out.println("Error: Product ID " + id + " already exists. Skipped.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Insert failed: " + e.getMessage());
        }
    }

    // (b) Conditional retrieval + sorting
    static void lowStockByCategory(Connection con) {
        System.out.print("Enter category: ");
        String cat = sc.nextLine().trim();
        String sql = "SELECT * FROM Product WHERE category = ? AND quantity < 20 ORDER BY price DESC";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, cat);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                System.out.printf("%-6s %-25s %-15s %-8s %-10s%n", "ID", "Name", "Category", "Qty", "Price");
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-6d %-25s %-15s %-8d %-10.2f%n",
                            rs.getInt("product_id"), rs.getString("product_name"),
                            rs.getString("category"), rs.getInt("quantity"), rs.getDouble("price"));
                }
                if (!found) System.out.println("No products found with quantity < 20 in this category.");
            }
        } catch (SQLException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
    }

    // (c) Add to existing stock
    static void updateStock(Connection con) {
        System.out.print("Enter product ID: ");
        int id = readInt();
        System.out.print("Enter quantity to add: ");
        int add = readInt();
        String sql = "UPDATE Product SET quantity = quantity + ? WHERE product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, add);
            ps.setInt(2, id);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0 ? "Stock updated successfully."
                                        : "Product with ID " + id + " does not exist.");
        } catch (SQLException e) {
            System.out.println("Update failed: " + e.getMessage());
        }
    }

    // (d) Search, validate, update price, show updated details
    static void modifyPrice(Connection con) {
        System.out.print("Enter product ID: ");
        int id = readInt();
        System.out.print("Enter new price: ");
        double newPrice = readDouble();

        String select = "SELECT * FROM Product WHERE product_id = ?";
        String update = "UPDATE Product SET price = ? WHERE product_id = ?";
        try (PreparedStatement sel = con.prepareStatement(select)) {
            sel.setInt(1, id);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Product with ID " + id + " does not exist.");
                    return;
                }
            }
            if (newPrice <= 0) {
                System.out.println("New price must be greater than zero. No update done.");
                return;
            }
            try (PreparedStatement upd = con.prepareStatement(update)) {
                upd.setDouble(1, newPrice);
                upd.setInt(2, id);
                upd.executeUpdate();
            }
            // display updated details
            sel.setInt(1, id);
            try (ResultSet rs = sel.executeQuery()) {
                if (rs.next()) {
                    System.out.println("Updated product details:");
                    System.out.println("ID       : " + rs.getInt("product_id"));
                    System.out.println("Name     : " + rs.getString("product_name"));
                    System.out.println("Category : " + rs.getString("category"));
                    System.out.println("Quantity : " + rs.getInt("quantity"));
                    System.out.println("Price    : " + rs.getDouble("price"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Operation failed: " + e.getMessage());
        }
    }

    // (e) Aggregate query + conditional deletion
    static void valueAndDelete(Connection con) {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT SUM(quantity * price) AS total FROM Product")) {
            if (rs.next()) System.out.printf("Total inventory value: %.2f%n", rs.getDouble("total"));
        } catch (SQLException e) {
            System.out.println("Aggregate query failed: " + e.getMessage());
            return;
        }

        System.out.print("Enter product ID to delete: ");
        int id = readInt();
        try (PreparedStatement chk = con.prepareStatement("SELECT quantity FROM Product WHERE product_id = ?")) {
            chk.setInt(1, id);
            try (ResultSet rs = chk.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Product with ID " + id + " does not exist.");
                    return;
                }
                if (rs.getInt("quantity") != 0) {
                    System.out.println("Cannot delete: product still has stock (quantity = "
                            + rs.getInt("quantity") + ").");
                    return;
                }
            }
            try (PreparedStatement del = con.prepareStatement("DELETE FROM Product WHERE product_id = ?")) {
                del.setInt(1, id);
                del.executeUpdate();
                System.out.println("Product deleted successfully.");
            }
        } catch (SQLException e) {
            System.out.println("Delete failed: " + e.getMessage());
        }
    }

    // ---- input helpers (avoid crashes on bad input) ----
    static int readInt() {
        while (true) {
            try { return Integer.parseInt(sc.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.print("Enter a valid integer: "); }
        }
    }
    static double readDouble() {
        while (true) {
            try { return Double.parseDouble(sc.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.print("Enter a valid number: "); }
        }
    }
}
