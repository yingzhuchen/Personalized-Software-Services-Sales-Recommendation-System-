package com.example.jobrec.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemJacksonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialize_ignoresUnknownFieldsAndMapsCoreProductFields() throws Exception {
        String json = "{"
                + "\"id\":\"innova-crm\","
                + "\"title\":\"INNOVA CRM Platform\","
                + "\"seller\":\"INNOVA AI\","
                + "\"price\":\"$99/month\","
                + "\"source\":\"INNOVA Catalog\","
                + "\"source_type\":\"innova_catalog\","
                + "\"description\":\"Sales CRM for software teams\","
                + "\"url\":\"https://innova.ai/products/crm\","
                + "\"keywords\":[\"crm\",\"sales\"],"
                + "\"favorite\":false,"
                + "\"unexpected_field\":\"ignored\""
                + "}";

        Item item = mapper.readValue(json, Item.class);

        assertEquals("innova-crm", item.getId());
        assertEquals("INNOVA CRM Platform", item.getTitle());
        assertEquals("INNOVA AI", item.getSeller());
        assertEquals(Item.SOURCE_INNOVA_CATALOG, item.getSourceType());
        assertEquals(new HashSet<>(Arrays.asList("crm", "sales")), item.getKeywords());
        assertFalse(item.getFavorite());
    }

    @Test
    void serialize_omitsNullOptionalFields() throws Exception {
        Item item = new Item(
                "innova-analytics",
                "INNOVA Analytics Suite",
                "INNOVA AI",
                null,
                null,
                Item.SOURCE_INNOVA_CATALOG,
                null,
                null,
                "https://innova.ai/products/analytics",
                new HashSet<>(Arrays.asList("analytics")),
                true);

        String json = mapper.writeValueAsString(item);

        assertTrue(json.contains("\"id\":\"innova-analytics\""));
        assertTrue(json.contains("\"favorite\":true"));
        assertTrue(json.contains("\"source_type\":\"innova_catalog\""));
        assertFalse(json.contains("\"price\""));
        assertFalse(json.contains("\"description\""));
    }
}
