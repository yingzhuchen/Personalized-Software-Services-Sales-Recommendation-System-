package com.example.jobrec.service;

import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;
import com.example.jobrec.external.SerpAPIClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hybrid product search: INNOVA catalog (MySQL master data) first,
 * SerpAPI market listings as supplemental context.
 */
@Service
public class ProductSearchService {
    private static final int MARKET_SUPPLEMENT_LIMIT = 5;

    private final SerpAPIClient serpAPIClient = new SerpAPIClient();

    public List<Item> search(double lat, double lon, String keyword) {
        List<Item> results = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        MySQLConnection connection = new MySQLConnection();
        List<Item> catalogItems = connection.searchCatalogProducts(keyword);
        connection.close();

        appendUnique(results, seenIds, catalogItems);

        List<Item> marketItems = serpAPIClient.search(lat, lon, keyword);
        int marketAdded = 0;
        for (Item item : marketItems) {
            if (marketAdded >= MARKET_SUPPLEMENT_LIMIT) {
                break;
            }
            if (seenIds.add(item.getId())) {
                item.setSourceType(Item.SOURCE_MARKET);
                results.add(item);
                marketAdded++;
            }
        }
        return results;
    }

    public List<Item> searchCatalogByKeywords(List<String> keywords) {
        List<Item> results = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        MySQLConnection connection = new MySQLConnection();
        for (String keyword : keywords) {
            appendUnique(results, seenIds, connection.searchCatalogProducts(keyword));
        }
        connection.close();
        return results;
    }

    private void appendUnique(List<Item> results, Set<String> seenIds, List<Item> items) {
        for (Item item : items) {
            if (seenIds.add(item.getId())) {
                results.add(item);
            }
        }
    }
}
