package com.example.jobrec.service;

import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;
import com.example.jobrec.external.SerpAPIClient;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class ProductSearchServiceTest {

    @Test
    void search_returnsCatalogProductsBeforeMarketSupplements() {
        Item catalogItem = new Item(
                "innova-crm",
                "INNOVA CRM Platform",
                "INNOVA AI",
                "$99/month",
                "INNOVA AI",
                Item.SOURCE_INNOVA_CATALOG,
                "CRM for sales teams",
                null,
                "https://innova.ai/products/crm",
                new HashSet<>(Arrays.asList("crm", "sales")),
                false);
        Item marketItem = new Item(
                "market-1",
                "External CRM Tool",
                "Amazon",
                "$49",
                "Amazon",
                Item.SOURCE_MARKET,
                "Third-party CRM",
                null,
                "https://market.example/crm",
                new HashSet<>(Collections.singletonList("crm")),
                false);

        try (MockedConstruction<MySQLConnection> mysql = mockConstruction(MySQLConnection.class,
                (mock, context) -> when(mock.searchCatalogProducts("crm")).thenReturn(Collections.singletonList(catalogItem)));
             MockedConstruction<SerpAPIClient> serp = mockConstruction(SerpAPIClient.class,
                     (mock, context) -> when(mock.search(any(), any(), anyString()))
                             .thenReturn(Collections.singletonList(marketItem)))) {

            ProductSearchService service = new ProductSearchService();
            List<Item> results = service.search(37.4, -122.1, "crm");

            assertEquals(2, results.size());
            assertEquals(Item.SOURCE_INNOVA_CATALOG, results.get(0).getSourceType());
            assertEquals(Item.SOURCE_MARKET, results.get(1).getSourceType());
        }
    }

    @Test
    void searchCatalogByKeywords_deduplicatesCatalogMatches() {
        Item crmItem = new Item(
                "innova-crm",
                "INNOVA CRM Platform",
                "INNOVA AI",
                "$99/month",
                "INNOVA AI",
                Item.SOURCE_INNOVA_CATALOG,
                "CRM",
                null,
                "https://innova.ai/products/crm",
                new HashSet<>(Collections.singletonList("crm")),
                false);

        try (MockedConstruction<MySQLConnection> mysql = mockConstruction(MySQLConnection.class,
                (mock, context) -> {
                    when(mock.searchCatalogProducts("crm")).thenReturn(Collections.singletonList(crmItem));
                    when(mock.searchCatalogProducts("sales")).thenReturn(Collections.singletonList(crmItem));
                })) {

            ProductSearchService service = new ProductSearchService();
            List<Item> results = service.searchCatalogByKeywords(Arrays.asList("crm", "sales"));

            assertEquals(1, results.size());
            assertEquals("innova-crm", results.get(0).getId());
            assertTrue(results.get(0).getSourceType().equals(Item.SOURCE_INNOVA_CATALOG));
        }
    }
}
