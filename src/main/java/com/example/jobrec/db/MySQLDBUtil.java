package com.example.jobrec.db;

/**
 * JDBC connection settings. Override for tests / local runs with:
 * {@code -Dapp.mysql.url=jdbc:mysql://127.0.0.1:3306/jobrec_it?user=jobrec&password=jobrec&...}
 * or env {@code APP_MYSQL_URL}.
 */
public class MySQLDBUtil {
    private static final String INSTANCE = "YOUR_AMAZON_DB_INSTANCE";
    private static final String PORT_NUM = "3306";
    public static final String DB_NAME = "YOUR_DB_NAME";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "YOUR_PASSWORD";

    private static final String DEFAULT_URL = "jdbc:mysql://"
            + INSTANCE + ":" + PORT_NUM + "/" + DB_NAME
            + "?user=" + USERNAME + "&password=" + PASSWORD
            + "&autoReconnect=true&serverTimezone=UTC";

    /** Prefer {@link #getUrl()} so tests can override the connection string. */
    public static final String URL = DEFAULT_URL;

    public static String getUrl() {
        String fromProperty = System.getProperty("app.mysql.url");
        if (fromProperty != null && !fromProperty.trim().isEmpty()) {
            return fromProperty.trim();
        }
        String fromEnv = System.getenv("APP_MYSQL_URL");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        return DEFAULT_URL;
    }
}
