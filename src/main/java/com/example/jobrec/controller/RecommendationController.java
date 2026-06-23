package com.example.jobrec.controller;

import com.example.jobrec.entity.Item;
import com.example.jobrec.recommendation.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
public class RecommendationController {
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/recommendation")
    public List<Item> recommend(@RequestParam("user_id") String userId,
                                @RequestParam("lat") double lat,
                                @RequestParam("lon") double lon,
                                HttpSession session) {
        SessionUtils.requireSession(session);
        return recommendationService.recommendItems(userId, lat, lon);
    }
}
