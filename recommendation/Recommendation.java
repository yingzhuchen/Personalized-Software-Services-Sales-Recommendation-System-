package com.example.jobrec.recommendation;

import com.example.jobrec.external.SerpAPIClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;
import com.example.jobrec.external.GitHubClient;
import org.checkerframework.checker.units.qual.A;

import java.util.*;

public class Recommendation {
    public List<Item> recommendItems(String userId, double lat, double lon) {
        List<Item> recommendedItems = new ArrayList<>();

//         Step 1, get all favorited itemids
        MySQLConnection connection = new MySQLConnection();
        Set<String> favoritedItemIds = connection.getFavoriteItemIds(userId);

        // Step 2, get all keywords, sort by count
        // {"software engineer": 6, "backend": 4, "san francisco": 3, "remote": 1}
        Map<String, Integer> allKeywords = new HashMap<>();
        for (String itemId : favoritedItemIds) {
            Set<String> keywords = connection.getKeywords(itemId);
            //use for loop to get all keywords
            for (String keyword : keywords) {
                allKeywords.put(keyword, allKeywords.getOrDefault(keyword, 0) + 1);
            }
        }
        connection.close();

        List<Map.Entry<String, Integer>> keywordList = new ArrayList<>(allKeywords.entrySet());
        //we got the keywords list in descending order
        keywordList.sort((Map.Entry<String, Integer> e1, Map.Entry<String, Integer> e2) ->
                Integer.compare(e2.getValue(), e1.getValue()));

        // Cut down search list only top 3
        if (keywordList.size() > 3) {
            keywordList = keywordList.subList(0, 3);
        }

        // Step 3, search based on keywords, filter out favorite items
        Set<String> visitedItemIds = new HashSet<>();
        SerpAPIClient client = new SerpAPIClient();
        ObjectMapper mapper = new ObjectMapper();
        //RedisConnection redis = new RedisConnection();
        //check Redis is missed or not
        for (Map.Entry<String, Integer> keyword : keywordList) {
            //String cachedResult = redis.getSearchResult(lat, lon, keyword.getKey());
            List<Item> items = null;
            //  try {
            // if (cachedResult != null) {
            //      items = Arrays.asList(mapper.readValue(cachedResult, Item[].class));
            //  } else {
            items = client.search(lat, lon, keyword.getKey());
            // redis.setSearchResult(lat, lon, keyword.getKey(), mapper.writeValueAsString(items));
            //   }
            //  } catch (JsonProcessingException e) {
            //     e.printStackTrace();
            // }

            for (Item item : items) {
                if (!favoritedItemIds.contains(item.getId()) && !visitedItemIds.contains(item.getId())) {
                    recommendedItems.add(item);
                    visitedItemIds.add(item.getId());
                }
            }
        }
        // redis.close();
        return recommendedItems;
    }
}




