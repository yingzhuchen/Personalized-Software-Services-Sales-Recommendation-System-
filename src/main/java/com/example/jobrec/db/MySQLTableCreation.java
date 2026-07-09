package com.example.jobrec.db;


import java.sql.*;

public class MySQLTableCreation {
    public static void main(String[] args) {
        try {

            // Step 1 Connect to MySQL.
            System.out.println("Connecting to " + MySQLDBUtil.URL);
            Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
            Connection conn = DriverManager.getConnection(MySQLDBUtil.URL);

            if (conn == null) {
                return;
            }

            // Step 2 Drop tables in case they exist.
            Statement statement = conn.createStatement();
            String sql = "DROP TABLE IF EXISTS keyword_stats";
            statement.executeUpdate(sql);

            sql = "DROP TABLE IF EXISTS corpus_stats";
            statement.executeUpdate(sql);

            sql = "DROP TABLE IF EXISTS keywords";
            statement.executeUpdate(sql);

            sql = "DROP TABLE IF EXISTS history";
            statement.executeUpdate(sql);

            sql = "DROP TABLE IF EXISTS items";
            statement.executeUpdate(sql);

            sql = "DROP TABLE IF EXISTS users";
            statement.executeUpdate(sql);


            // Step 3 Create new tables
            sql = "CREATE TABLE items ("
                    + "item_id VARCHAR(255) NOT NULL,"
                    + "name VARCHAR(255),"
                    + "address VARCHAR(255),"
                    + "url VARCHAR(255),"
                    + "seller VARCHAR(255),"
                    + "description TEXT,"
                    + "source_type VARCHAR(32) NOT NULL DEFAULT 'innova_catalog',"
                    + "PRIMARY KEY (item_id)"
                    + ")";
            statement.executeUpdate(sql);

            sql = "CREATE TABLE users ("
                    + "user_id VARCHAR(255) NOT NULL,"
                    + "password VARCHAR(255) NOT NULL,"
                    + "first_name VARCHAR(255),"
                    + "last_name VARCHAR(255),"
                    + "PRIMARY KEY (user_id)"  //primary key for users
                    + ")";
            statement.executeUpdate(sql);

            sql = "CREATE TABLE keywords ("
                    + "item_id VARCHAR(255) NOT NULL,"
                    + "keyword VARCHAR(255) NOT NULL,"
                    + "PRIMARY KEY (item_id, keyword)," //normally we dont use tow columns to set up a table
                    + "FOREIGN KEY (item_id) REFERENCES items(item_id)"
                    + ")";
            statement.executeUpdate(sql);

            sql = "CREATE TABLE history ("
                    + "user_id VARCHAR(255) NOT NULL,"
                    + "item_id VARCHAR(255) NOT NULL,"
                    + "last_favor_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (user_id, item_id),"
                    + "FOREIGN KEY (user_id) REFERENCES users(user_id),"
                    + "FOREIGN KEY (item_id) REFERENCES items(item_id)"
                    + ")";
            statement.executeUpdate(sql);

            sql = "CREATE TABLE keyword_stats ("
                    + "keyword VARCHAR(255) NOT NULL,"
                    + "document_frequency INT NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (keyword)"
                    + ")";
            statement.executeUpdate(sql);

            sql = "CREATE TABLE corpus_stats ("
                    + "stat_key VARCHAR(64) NOT NULL,"
                    + "stat_value BIGINT NOT NULL,"
                    + "PRIMARY KEY (stat_key)"
                    + ")";
            statement.executeUpdate(sql);

            sql = "INSERT INTO corpus_stats (stat_key, stat_value) VALUES ('total_items', 5)";
            statement.executeUpdate(sql);

            seedInnovaCatalog(statement);

            // Step 4: insert fake user 1111/3229c1097c00d497a0fd282d586be050
            sql = "INSERT INTO users VALUES('1111', '3229c1097c00d497a0fd282d586be050', 'John', 'Smith')";
            statement.executeUpdate(sql);

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void seedInnovaCatalog(Statement statement) throws SQLException {
        insertCatalogProduct(statement,
                "innova-crm",
                "INNOVA CRM Platform",
                "$99/month",
                "https://innova.ai/products/crm",
                "INNOVA AI",
                "Cloud CRM for B2B software and services sales teams.",
                new String[]{"crm", "sales", "customer", "saas"});
        insertCatalogProduct(statement,
                "innova-analytics",
                "INNOVA Analytics Suite",
                "$199/month",
                "https://innova.ai/products/analytics",
                "INNOVA AI",
                "Business intelligence dashboards and product usage analytics.",
                new String[]{"analytics", "dashboard", "bi", "data"});
        insertCatalogProduct(statement,
                "innova-ai-platform",
                "INNOVA AI Platform",
                "$499/month",
                "https://innova.ai/products/ai-platform",
                "INNOVA AI",
                "Enterprise AI platform with NLP and recommendation services.",
                new String[]{"ai", "nlp", "ml", "recommendation"});
        insertCatalogProduct(statement,
                "innova-cloud-migrate",
                "INNOVA Cloud Migration Service",
                "Custom pricing",
                "https://innova.ai/products/cloud-migration",
                "INNOVA AI",
                "Managed cloud migration for enterprise software workloads.",
                new String[]{"cloud", "migration", "aws", "enterprise"});
        insertCatalogProduct(statement,
                "innova-security",
                "INNOVA Enterprise Security Suite",
                "$299/month",
                "https://innova.ai/products/security",
                "INNOVA AI",
                "Security and compliance tooling for regulated software vendors.",
                new String[]{"security", "compliance", "enterprise", "software"});
    }

    private static void insertCatalogProduct(Statement statement, String id, String name, String price,
                                             String url, String seller, String description,
                                             String[] keywords) throws SQLException {
        String sql = "INSERT INTO items (item_id, name, address, url, seller, description, source_type) "
                + "VALUES ('" + id + "', '" + name + "', '" + price + "', '" + url + "', '" + seller + "', '"
                + description + "', 'innova_catalog')";
        statement.executeUpdate(sql);

        for (String keyword : keywords) {
            statement.executeUpdate("INSERT INTO keywords VALUES ('" + id + "', '" + keyword + "')");
            statement.executeUpdate("INSERT INTO keyword_stats (keyword, document_frequency) VALUES ('"
                    + keyword + "', 1) ON DUPLICATE KEY UPDATE document_frequency = document_frequency + 1");
        }
    }

}
