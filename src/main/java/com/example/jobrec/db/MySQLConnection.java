package com.example.jobrec.db;

import com.example.jobrec.entity.Item;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

//users click at "save" / "unsave" on frontend, and we will need servlets to update db on backend
public class MySQLConnection {
    private Connection conn;

    public MySQLConnection() {
        //try, catch: try{} and if fails then do catch{}
        try {
            Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
            conn = DriverManager.getConnection(MySQLDBUtil.URL);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public boolean saveItem(Item item) {
        if (conn == null) {
            System.err.println("DB connection failed");
            return false;
        }
        boolean corpusUpdated = false;
        String insertItemSql = "INSERT IGNORE INTO items "
                + "(item_id, name, address, url, seller, description, source_type) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement statement = conn.prepareStatement(insertItemSql);
            String sourceType = item.getSourceType() == null ? Item.SOURCE_MARKET : item.getSourceType();
            statement.setString(1, item.getId());
            statement.setString(2, item.getTitle());
            statement.setString(3, item.getPrice());
            statement.setString(4, item.getUrl());
            statement.setString(5, item.getSeller());
            statement.setString(6, item.getDescription());
            statement.setString(7, sourceType);
            if (statement.executeUpdate() == 1 && Item.SOURCE_INNOVA_CATALOG.equals(sourceType)) {
                incrementTotalItems();
                corpusUpdated = true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String insertKeywordSql = "INSERT IGNORE INTO keywords VALUES (?, ?)";
        try {
            String sourceType = item.getSourceType() == null ? Item.SOURCE_MARKET : item.getSourceType();
            for (String keyword : item.getKeywords()) {
                PreparedStatement statement = conn.prepareStatement(insertKeywordSql);
                statement.setString(1, item.getId());
                statement.setString(2, keyword);
                if (statement.executeUpdate() == 1
                        && Item.SOURCE_INNOVA_CATALOG.equals(sourceType)) {
                    incrementKeywordDocumentFrequency(keyword);
                    corpusUpdated = true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return corpusUpdated;
    }
    public void setFavoriteItems(String userId, Item item) {
        if (conn == null) {
            System.err.println("DB connection failed");
            return;
        }
        saveItem(item);
        String sql = "INSERT IGNORE INTO history (user_id, item_id) VALUES (?, ?)"; //time will automatically filled in
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, userId);
            statement.setString(2, item.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void unsetFavoriteItems(String userId, String itemId) {
        if (conn == null) {
            System.err.println("DB connection failed");
            return;
        }
        //no need to delete items from db
        String sql = "DELETE FROM history WHERE user_id = ? AND item_id = ?"; //?: input arguments
        //delete all items which match the user id and item id
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, userId);
            statement.setString(2, itemId);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public Set<String> getFavoriteItemIds(String userId) {
        if (conn == null) {
            System.err.println("DB connection failed");
            return new HashSet<>();
        }

        Set<String> favoriteItems = new HashSet<>();

        try {
            String sql = "SELECT item_id FROM history WHERE user_id = ?";
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, userId);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                String itemId = rs.getString("item_id");
                favoriteItems.add(itemId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return favoriteItems;
    }

    public Set<Item> getFavoriteItems(String userId) {
        if (conn == null) {
            System.err.println("DB connection failed");
            return new HashSet<>();
        }
        Set<Item> favoriteItems = new HashSet<>();
        Set<String> favoriteItemIds = getFavoriteItemIds(userId);

        String sql = "SELECT item_id, name, address, url, seller, description, source_type FROM items WHERE item_id = ?";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            for (String itemId : favoriteItemIds) {
                statement.setString(1, itemId);
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    favoriteItems.add(buildItemFromRow(rs, getKeywords(itemId), true));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return favoriteItems;
    }
    public int getTotalItemCount() {
        return getCatalogItemCount();
    }

    public int getCatalogItemCount() {
        if (conn == null) {
            System.err.println("DB connection failed");
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM items WHERE source_type = ?";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, Item.SOURCE_INNOVA_CATALOG);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public List<Item> searchCatalogProducts(String keyword) {
        if (conn == null) {
            System.err.println("DB connection failed");
            return Collections.emptyList();
        }

        List<Item> products = new ArrayList<>();
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        String sql;
        if (hasKeyword) {
            sql = "SELECT DISTINCT i.item_id, i.name, i.address, i.url, i.seller, i.description, i.source_type "
                    + "FROM items i "
                    + "LEFT JOIN keywords k ON i.item_id = k.item_id "
                    + "WHERE i.source_type = ? "
                    + "AND (k.keyword LIKE ? OR i.name LIKE ? OR i.description LIKE ?)";
        } else {
            sql = "SELECT i.item_id, i.name, i.address, i.url, i.seller, i.description, i.source_type "
                    + "FROM items i WHERE i.source_type = ?";
        }

        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, Item.SOURCE_INNOVA_CATALOG);
            if (hasKeyword) {
                String pattern = "%" + keyword.trim() + "%";
                statement.setString(2, pattern);
                statement.setString(3, pattern);
                statement.setString(4, pattern);
            }

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                String itemId = rs.getString("item_id");
                products.add(buildItemFromRow(rs, getKeywords(itemId), false));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return products;
    }

    public List<Item> getPopularCatalogItems(int limit) {
        if (conn == null || limit <= 0) {
            return Collections.emptyList();
        }

        List<Item> products = new ArrayList<>();
        String sql = "SELECT i.item_id, i.name, i.address, i.url, i.seller, i.description, i.source_type, "
                + "COUNT(h.item_id) AS favorite_count "
                + "FROM items i "
                + "LEFT JOIN history h ON i.item_id = h.item_id "
                + "WHERE i.source_type = ? "
                + "GROUP BY i.item_id, i.name, i.address, i.url, i.seller, i.description, i.source_type "
                + "ORDER BY favorite_count DESC, i.name ASC "
                + "LIMIT ?";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, Item.SOURCE_INNOVA_CATALOG);
            statement.setInt(2, limit);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                String itemId = rs.getString("item_id");
                products.add(buildItemFromRow(rs, getKeywords(itemId), false));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (products.isEmpty()) {
            List<Item> catalogItems = searchCatalogProducts(null);
            return catalogItems.subList(0, Math.min(limit, catalogItems.size()));
        }
        return products;
    }

    public List<String> getUsersWithMinimumFavorites(int minimumFavorites, int limit) {
        if (conn == null || minimumFavorites <= 0 || limit <= 0) {
            return Collections.emptyList();
        }

        List<String> userIds = new ArrayList<>();
        String sql = "SELECT user_id, COUNT(*) AS favorite_count "
                + "FROM history GROUP BY user_id HAVING favorite_count >= ? "
                + "ORDER BY favorite_count DESC LIMIT ?";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setInt(1, minimumFavorites);
            statement.setInt(2, limit);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                userIds.add(rs.getString("user_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return userIds;
    }

    public Map<String, Integer> getDocumentFrequenciesForKeywords(List<String> keywords) {
        if (conn == null || keywords.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> documentFrequencies = new HashMap<>();
        String sql = buildInClauseQuery(
                "SELECT k.keyword, COUNT(DISTINCT k.item_id) AS df "
                        + "FROM keywords k JOIN items i ON k.item_id = i.item_id "
                        + "WHERE i.source_type = 'innova_catalog' AND k.keyword IN ",
                keywords.size())
                + " GROUP BY k.keyword";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            bindStringParameters(statement, keywords);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                documentFrequencies.put(rs.getString("keyword"), rs.getInt("df"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return documentFrequencies;
    }

    public Map<String, Integer> getTermFrequenciesForItems(Set<String> itemIds) {
        if (conn == null || itemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> itemIdList = new ArrayList<>(itemIds);
        Map<String, Integer> termFrequencies = new HashMap<>();
        String sql = buildInClauseQuery(
                "SELECT keyword, COUNT(*) AS freq FROM keywords WHERE item_id IN ", itemIdList.size())
                + " GROUP BY keyword";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            bindStringParameters(statement, itemIdList);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                termFrequencies.put(rs.getString("keyword"), rs.getInt("freq"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return termFrequencies;
    }

    private void incrementTotalItems() {
        String sql = "INSERT INTO corpus_stats (stat_key, stat_value) VALUES ('total_items', 1) "
                + "ON DUPLICATE KEY UPDATE stat_value = stat_value + 1";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void incrementKeywordDocumentFrequency(String keyword) {
        String sql = "INSERT INTO keyword_stats (keyword, document_frequency) VALUES (?, 1) "
                + "ON DUPLICATE KEY UPDATE document_frequency = document_frequency + 1";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, keyword);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String buildInClauseQuery(String prefix, int size) {
        StringBuilder sql = new StringBuilder(prefix).append("(");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");
        return sql.toString();
    }

    private void bindStringParameters(PreparedStatement statement, List<String> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            statement.setString(i + 1, values.get(i));
        }
    }

    private Item buildItemFromRow(ResultSet rs, Set<String> keywords, boolean favorite) throws SQLException {
        return new Item(
                rs.getString("item_id"),
                rs.getString("name"),
                rs.getString("seller"),
                rs.getString("address"),
                rs.getString("seller"),
                rs.getString("source_type"),
                rs.getString("description"),
                null,
                rs.getString("url"),
                keywords,
                favorite);
    }

    public Set<String> getKeywords(String itemId) {
        if (conn == null) {
            System.err.println("DB connection failed");
            return Collections.emptySet();
        }
        Set<String> keywords = new HashSet<>();
        String sql = "SELECT keyword from keywords WHERE item_id = ? ";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, itemId);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                String keyword = rs.getString("keyword");
                keywords.add(keyword);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return keywords;
    }
    public String getFullname(String userId) {
        if (conn == null) {
            System.err.println("DB connection failed");
            return "";
        }
        //set up an empty name
        String name = "";
        //get first name and last name, and combine them together
        String sql = "SELECT first_name, last_name FROM users WHERE user_id = ?";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, userId);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                name = rs.getString("first_name") + " " + rs.getString("last_name");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return name;
    }
    //check database to verify log in
    public boolean verifyLogin(String userId, String password) {
        if (conn == null) {
            System.err.println("DB connection failed");
            return false;
        }
        //need to verify both ID and password
        String sql = "SELECT user_id FROM users WHERE user_id = ? AND password = ?";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, userId);
            statement.setString(2, password);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return true;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return false;
    }
    //insert user
    public boolean addUser(String userId, String password, String firstname, String lastname) {
        if (conn == null) {
            System.err.println("DB connection failed");
            return false;
        }

        String sql = "INSERT IGNORE INTO users VALUES (?, ?, ?, ?)";
        try {
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setString(1, userId);
            statement.setString(2, password);
            statement.setString(3, firstname);
            statement.setString(4, lastname);

            return statement.executeUpdate() == 1; //1 --> true  0 --> false
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}

