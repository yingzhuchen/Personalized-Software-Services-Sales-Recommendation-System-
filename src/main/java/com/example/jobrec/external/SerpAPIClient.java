package com.example.jobrec.external;

import com.example.jobrec.entity.Item;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Component
public class SerpAPIClient {
    private static final String URL_TEMPLATE =
            "https://serpapi.com/search?engine=google_shopping&q=%s&location=%s&api_key=%s";

    private static final String API_KEY = "YOUR_API_KEY";
    private static final String DEFAULT_KEYWORD = "software";

    public List<Item> search(Double lat, Double lon, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            keyword = DEFAULT_KEYWORD;
        }

        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        GeoConverterClient converterClient = new GeoConverterClient();
        String location = converterClient.getLocationName(lat, lon);
        if (location.isEmpty()) {
            location = "United States";
        }
        String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8);
        String url = String.format(URL_TEMPLATE, encodedKeyword, encodedLocation, API_KEY);

        CloseableHttpClient httpClient = HttpClients.createDefault();
        ResponseHandler<List<Item>> responseHandler = response -> {
            if (response.getStatusLine().getStatusCode() != 200) {
                return Collections.emptyList();
            }

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return Collections.emptyList();
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(entity.getContent());
            JsonNode results = root.get("shopping_results");
            if (results == null || !results.isArray()) {
                return Collections.emptyList();
            }

            List<Item> items = new ArrayList<>();
            Iterator<JsonNode> result = results.elements();
            while (result.hasNext()) {
                items.add(extract(result.next()));
            }

            extractKeywords(items);
            return items;
        };

        try {
            return httpClient.execute(new HttpGet(url), responseHandler);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    private Item extract(JsonNode itemNode) {
        String productId = itemNode.has("product_id")
                ? itemNode.get("product_id").asText()
                : String.valueOf(itemNode.path("position").asInt());
        String title = itemNode.path("title").asText("");
        String seller = itemNode.path("source").asText("");
        String price = itemNode.has("price")
                ? itemNode.get("price").asText()
                : itemNode.path("extracted_price").asText("");
        String source = seller;
        String url = itemNode.path("link").asText("");
        String description = buildDescription(title, price, seller, itemNode);

        List<String> features = new ArrayList<>();
        Set<String> keywords = new HashSet<>();
        if (itemNode.has("extensions") && itemNode.get("extensions").isArray()) {
            Iterator<JsonNode> extensions = itemNode.get("extensions").elements();
            while (extensions.hasNext()) {
                String extension = extensions.next().asText();
                features.add(extension);
                keywords.add(extension);
            }
        }
        if (itemNode.has("delivery")) {
            features.add(itemNode.get("delivery").asText());
        }
        if (itemNode.has("rating")) {
            features.add("Rating: " + itemNode.get("rating").asText());
        }

        return new Item(productId, title, seller, price, source, Item.SOURCE_MARKET,
                description, features, url, keywords, false);
    }

    private String buildDescription(String title, String price, String seller, JsonNode itemNode) {
        StringBuilder description = new StringBuilder(title);
        if (!price.isEmpty()) {
            description.append(". Price: ").append(price);
        }
        if (!seller.isEmpty()) {
            description.append(". Sold by ").append(seller);
        }
        if (itemNode.has("reviews")) {
            description.append(". Reviews: ").append(itemNode.get("reviews").asText());
        }
        return description.toString();
    }

    private void extractKeywords(List<Item> items) {
        EdenAI client = new EdenAI();
        for (Item item : items) {
            String article = item.getDescription();
            if (item.getFeatures() != null && !item.getFeatures().isEmpty()) {
                article = article + ". " + String.join(". ", item.getFeatures());
            }
            Set<String> keywords = new HashSet<>(item.getKeywords());
            keywords.addAll(client.extract(article, 3));
            item.setKeywords(keywords);
        }
    }
}
