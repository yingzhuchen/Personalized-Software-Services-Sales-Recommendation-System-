package com.example.jobrec.controller;

import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;
import com.example.jobrec.recommendation.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Set;

@RestController
public class SearchController {
    private final RecommendationService recommendationService;

    public SearchController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/search")
    public List<Item> search(@RequestParam("user_id") String userId,
                             @RequestParam("lat") double lat,
                             @RequestParam("lon") double lon,
                             @RequestParam(value = "keyword", required = false) String keyword,
                             HttpSession session) {
        SessionUtils.requireSession(session);

        MySQLConnection connection = new MySQLConnection();
        Set<String> favoritedItemIds = connection.getFavoriteItemIds(userId);
        connection.close();

        List<Item> items = recommendationService.searchProducts(lat, lon, keyword);
        for (Item item : items) {
            item.setFavorite(favoritedItemIds.contains(item.getId()));
        }
        return items;
    }
}
