import java.sql.*;
import java.util.Scanner;

/**
 * Inventory Management System - JDBC + MySQL
 * Database: retail_db, Table: Product
 */
public class InventoryApp {

    private static final String URL = "jdbc:mysql://localhost:3306/retail_db?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = "root";

    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD)) {
            createTableIfMissing(con);
            int choice;
            do {
                System.out.println("\n===== INVENTORY MENU =====");
                System.out.println("1. Insert sample products");
                System.out.println("2. Show low-stock products by category (qty < 20, price DESC)");
                System.out.println("3. Add stock to a product");
                System.out.println("4. Search product and update price");
                System.out.println("5. Total inventory value / Delete product with zero stock");
                System.out.println("6. Exit");
                System.out.print("Enter choice: ");
                choice = readInt();
                switch (choice) {
                    case 1: insertProducts(con); break;
                    case 2: lowStockByCategory(con); break;
                    case 3: addStock(con); break;
                    case 4: updatePrice(con); break;
                    case 5: totalValueAndDelete(con); break;
                    case 6: System.out.println("Goodbye!"); break;
                    default: System.out.println("Invalid choice.");
                }
            } while (choice != 6);
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    private static void createTableIfMissing(Connection con) throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS Product ("
                + "product_id INT PRIMARY KEY, "
                + "product_name VARCHAR(100), "
                + "category VARCHAR(50), "
                + "quantity INT, "
                + "price DOUBLE)";
        try (Statement st = con.createStatement()) {
            st.execute(ddl);
        }
    }

    // (a) Insert at least three records using PreparedStatement; handle duplicate IDs
    private static void insertProducts(Connection con) {
        Object[][] data = {
                {101, "Laptop", "Electronics", 10, 55000.0},
                {102, "Mouse", "Electronics", 50, 599.0},
                {103, "Notebook", "Stationery", 15, 80.0},
                {104, "Pen Pack", "Stationery", 200, 120.0}
        };
        String sql = "INSERT INTO Product (product_id, product_name, category, quantity, price) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (Object[] row : data) {
                try {
                    ps.setInt(1, (Integer) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setInt(4, (Integer) row[3]);
                    ps.setDouble(5, (Double) row[4]);
                    ps.executeUpdate();
                    System.out.println("Inserted product " + row[0]);
                } catch (SQLIntegrityConstraintViolationException dup) {
                    System.out.println("Duplicate product ID " + row[0] + " - skipped.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Insert failed: " + e.getMessage());
        }
    }

    // (b) Products of a category with quantity < 20, sorted by price DESC
    private static void lowStockByCategory(Connection con) {
        System.out.print("Enter category: ");
        String category = sc.nextLine().trim();
        String sql = "SELECT * FROM Product WHERE category = ? AND quantity < 20 ORDER BY price DESC";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                System.out.printf("%-6s %-20s %-15s %-8s %-10s%n", "ID", "Name", "Category", "Qty", "Price");
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-6d %-20s %-15s %-8d %-10.2f%n",
                            rs.getInt("product_id"), rs.getString("product_name"),
                            rs.getString("category"), rs.getInt("quantity"), rs.getDouble("price"));
                }
                if (!found) System.out.println("No products found with quantity below 20 in this category.");
            }
        } catch (SQLException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
    }

    // (c) Add quantity to existing stock
    private static void addStock(Connection con) {
        System.out.print("Enter product ID: ");
        int id = readInt();
        System.out.print("Enter quantity to add: ");
        int qty = readInt();
        String sql = "UPDATE Product SET quantity = quantity + ? WHERE product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setInt(2, id);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0 ? "Stock updated successfully." : "Product with ID " + id + " does not exist.");
        } catch (SQLException e) {
            System.out.println("Update failed: " + e.getMessage());
        }
    }

    // (d) Retrieve product and update price only if product exists and new price > 0
    private static void updatePrice(Connection con) {
        System.out.print("Enter product ID: ");
        int id = readInt();
        System.out.print("Enter new price: ");
        double newPrice = readDouble();
        try {
            if (!exists(con, id)) {
                System.out.println("Product with ID " + id + " not found.");
                return;
            }
            if (newPrice <= 0) {
                System.out.println("Price must be greater than zero. Update rejected.");
                return;
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE Product SET price = ? WHERE product_id = ?")) {
                ps.setDouble(1, newPrice);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM Product WHERE product_id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Updated details -> ID: " + rs.getInt("product_id")
                                + ", Name: " + rs.getString("product_name")
                                + ", Category: " + rs.getString("category")
                                + ", Qty: " + rs.getInt("quantity")
                                + ", Price: " + rs.getDouble("price"));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Operation failed: " + e.getMessage());
        }
    }

    // (e) SUM(quantity * price), then delete product only if quantity is zero
    private static void totalValueAndDelete(Connection con) {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT SUM(quantity * price) AS total FROM Product")) {
            if (rs.next()) System.out.printf("Total inventory value: %.2f%n", rs.getDouble("total"));
        } catch (SQLException e) {
            System.out.println("Aggregate query failed: " + e.getMessage());
            return;
        }

        System.out.print("Enter product ID to delete: ");
        int id = readInt();
        try (PreparedStatement ps = con.prepareStatement("SELECT quantity FROM Product WHERE product_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Product with ID " + id + " not found. Cannot delete.");
                    return;
                }
                if (rs.getInt("quantity") != 0) {
                    System.out.println("Product cannot be deleted: quantity is not zero.");
                    return;
                }
            }
            try (PreparedStatement del = con.prepareStatement("DELETE FROM Product WHERE product_id = ? AND quantity = 0")) {
                del.setInt(1, id);
                del.executeUpdate();
                System.out.println("Product " + id + " deleted successfully.");
            }
        } catch (SQLException e) {
            System.out.println("Delete failed: " + e.getMessage());
        }
    }

    private static boolean exists(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM Product WHERE product_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int readInt() {
        while (true) {
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.print("Enter a valid integer: ");
            }
        }
    }

    private static double readDouble() {
        while (true) {
            try {
                return Double.parseDouble(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.print("Enter a valid number: ");
            }
        }
    }
}
