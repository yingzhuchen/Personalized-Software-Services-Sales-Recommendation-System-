package com.example.jobrec.db;

import redis.clients.jedis.Jedis;

public class RedisConnection {
    private static final String INSTANCE = "YOUR_REDIS_INSTANCE_IP";
    private static final int PORT = 6379; //redis default port
    private static final String PASSWORD = "YOUR_PASSWORD"; // This is the password
    private static final String SEARCH_KEY_TEMPLATE = "search:lat=%s&lon=%s&keyword=%s";
    private static final String FAVORITE_KEY_TEMPLATE = "history:userId=%s";

    //create and close connecrions to Redis
    private Jedis jedis; //Jedis, a client library in Java for Redis – the popular in-memory data structure store that can persist on disk as well

    public RedisConnection() {
        jedis = new Jedis(INSTANCE, PORT);
        jedis.auth(PASSWORD);
    }

    public void close() {
        jedis.close();
    }
    public String getSearchResult(double lat, double lon, String keyword) {
        if (jedis == null) {
            return null;
        }
        String key = String.format(SEARCH_KEY_TEMPLATE, lat, lon, keyword);
        return jedis.get(key);
    }

    public void setSearchResult(double lat, double lon, String keyword, String value) {
        if (jedis == null) {
            return;
        }
        String key = String.format(SEARCH_KEY_TEMPLATE, lat, lon, keyword);
        jedis.set(key, value);
        jedis.expire(key, 10);  //redis will fetch data from GitHub every 10s
    }

    public String getFavoriteResult(String userId) {
        if (jedis == null) {
            return null;
        }
        String key = String.format(FAVORITE_KEY_TEMPLATE, userId);
        return jedis.get(key);
    }

    public void setFavoriteResult(String userId, String value) {
        if (jedis == null) {
            return;
        }
        String key = String.format(FAVORITE_KEY_TEMPLATE, userId);
        jedis.set(key, value);
        jedis.expire(key, 10);
    }

    public void deleteFavoriteResult(String userId) {
        if (jedis == null) {
            return;
        }
        String key = String.format(FAVORITE_KEY_TEMPLATE, userId);
        jedis.del(key);
    }
    public static void main(String[] args) {
        RedisConnection c = new RedisConnection();
        c.setFavoriteResult("1234", "aaaa");
        System.out.println(c.getFavoriteResult("1234"));
        c.deleteFavoriteResult("1234");
        System.out.println(c.getFavoriteResult("1234"));
        c.setSearchResult(1, 2, "123", "aaa");
        System.out.println(c.getSearchResult(1, 2, "123"));
    }
}

