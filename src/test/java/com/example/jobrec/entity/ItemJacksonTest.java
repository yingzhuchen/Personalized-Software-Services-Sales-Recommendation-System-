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
                + "\"location\":\"$99/month\","
                + "\"via\":\"INNOVA Catalog\","
                + "\"description\":\"Sales CRM for software teams\","
                + "\"url\":\"https://innova.ai/products/crm\","
                + "\"keywords\":[\"crm\",\"sales\"],"
                + "\"favorite\":false,"
                + "\"unexpected_field\":\"ignored\""
                + "}";

        Item item = mapper.readValue(json, Item.class);

        assertEquals("innova-crm", item.getId());
        assertEquals("INNOVA CRM Platform", item.getTitle());
        assertEquals("$99/month", item.getLocation());
        assertEquals(new HashSet<>(Arrays.asList("crm", "sales")), item.getKeywords());
        assertFalse(item.getFavorite());
    }

    @Test
    void serialize_omitsNullOptionalFieldsAndUsesJacksonAnnotations() throws Exception {
        Item item = new Item(
                "innova-analytics",
                "INNOVA Analytics Suite",
                "INNOVA AI",
                null,
                null,
                null,
                null,
                "https://innova.ai/products/analytics",
                new HashSet<>(Arrays.asList("analytics")),
                true);

        String json = mapper.writeValueAsString(item);

        assertTrue(json.contains("\"id\":\"innova-analytics\""));
        assertTrue(json.contains("\"favorite\":true"));
        assertTrue(json.contains("\"companey_name\":\"INNOVA AI\""));
        assertFalse(json.contains("\"location\""));
        assertFalse(json.contains("\"description\""));
    }
}
