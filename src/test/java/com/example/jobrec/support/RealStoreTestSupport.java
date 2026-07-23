package com.example.jobrec.support;

import com.example.jobrec.db.MySQLDBUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * Boots a real MySQL schema + seeded catalog for integration tests.
 * Uses TCP {@code 127.0.0.1} so the test user can connect without the
 * unix-socket permission issues common on locked-down hosts.
 */
public final class RealStoreTestSupport {
    public static final String JDBC_URL =
            "jdbc:mysql://127.0.0.1:3306/jobrec_it?user=jobrec&password=jobrec"
                    + "&autoReconnect=true&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";

    public static final String SEARCH_KEYWORD = "crm";
    public static final double LAT = 37.77;
    public static final double LON = -122.42;

    private RealStoreTestSupport() {
    }

    public static void configureJdbcUrl() {
        System.setProperty("app.mysql.url", JDBC_URL);
    }

    public static boolean canConnect() {
        try (Connection ignored = DriverManager.getConnection(JDBC_URL)) {
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static void resetSchemaAndSeedCatalog(int extraProducts) throws Exception {
        configureJdbcUrl();
        try (Connection conn = DriverManager.getConnection(JDBC_URL);
             Statement statement = conn.createStatement()) {
            statement.executeUpdate("SET FOREIGN_KEY_CHECKS=0");
            statement.executeUpdate("DROP TABLE IF EXISTS history");
            statement.executeUpdate("DROP TABLE IF EXISTS keywords");
            statement.executeUpdate("DROP TABLE IF EXISTS keyword_stats");
            statement.executeUpdate("DROP TABLE IF EXISTS corpus_stats");
            statement.executeUpdate("DROP TABLE IF EXISTS items");
            statement.executeUpdate("DROP TABLE IF EXISTS users");
            statement.executeUpdate("SET FOREIGN_KEY_CHECKS=1");

            for (String ddl : loadSchemaStatements()) {
                statement.executeUpdate(ddl);
            }

            insertCatalogProduct(conn,
                    "innova-crm",
                    "INNOVA CRM Platform",
                    "$99/month",
                    "https://innova.ai/products/crm",
                    "INNOVA AI",
                    "Cloud CRM for B2B software and services sales teams.",
                    new String[]{"crm", "sales", "customer", "saas"});
            insertCatalogProduct(conn,
                    "innova-analytics",
                    "INNOVA Analytics Suite",
                    "$199/month",
                    "https://innova.ai/products/analytics",
                    "INNOVA AI",
                    "Business intelligence dashboards and product usage analytics.",
                    new String[]{"analytics", "dashboard", "bi", "data"});

            // Extra rows make LIKE catalog search do real MySQL work on miss.
            for (int i = 0; i < extraProducts; i++) {
                String id = "seed-product-" + i;
                insertCatalogProduct(conn,
                        id,
                        "Seed Product " + i + " Suite",
                        "$" + (10 + i) + "/month",
                        "https://innova.ai/products/seed-" + i,
                        "INNOVA AI",
                        "Seeded catalog product " + i + " for latency integration tests.",
                        new String[]{"seed", "product" + i, (i % 2 == 0 ? "enterprise" : "smb")});
            }

            int total = 2 + extraProducts;
            statement.executeUpdate(
                    "INSERT INTO corpus_stats (stat_key, stat_value) VALUES ('total_items', " + total + ")");
            statement.executeUpdate(
                    "INSERT INTO users VALUES('1111', '3229c1097c00d497a0fd282d586be050', 'John', 'Smith')");
        }
    }

    private static void insertCatalogProduct(Connection conn, String id, String name, String price,
                                             String url, String seller, String description,
                                             String[] keywords) throws Exception {
        try (PreparedStatement item = conn.prepareStatement(
                "INSERT INTO items (item_id, name, address, url, seller, description, source_type) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'innova_catalog')")) {
            item.setString(1, id);
            item.setString(2, name);
            item.setString(3, price);
            item.setString(4, url);
            item.setString(5, seller);
            item.setString(6, description);
            item.executeUpdate();
        }
        try (PreparedStatement keywordStmt = conn.prepareStatement(
                "INSERT INTO keywords (item_id, keyword) VALUES (?, ?)")) {
            for (String keyword : keywords) {
                keywordStmt.setString(1, id);
                keywordStmt.setString(2, keyword);
                keywordStmt.executeUpdate();
            }
        }
    }

    private static String[] loadSchemaStatements() throws Exception {
        InputStream in = RealStoreTestSupport.class.getClassLoader().getResourceAsStream("schema-it.sql");
        if (in == null) {
            throw new IllegalStateException("schema-it.sql not found on classpath");
        }
        StringBuilder cleaned = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                cleaned.append(line).append('\n');
            }
        }
        String[] parts = cleaned.toString().split(";");
        java.util.List<String> statements = new java.util.ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        return statements.toArray(new String[0]);
    }

    /** Sanity: ensure override is visible to production JDBC helper. */
    public static String activeJdbcUrl() {
        return MySQLDBUtil.getUrl();
    }
}
