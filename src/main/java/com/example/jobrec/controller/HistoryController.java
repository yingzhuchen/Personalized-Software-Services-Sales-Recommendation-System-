package com.example.jobrec.controller;

import com.example.jobrec.entity.HistoryRequestBody;
import com.example.jobrec.entity.Item;
import com.example.jobrec.entity.ResultResponse;
import com.example.jobrec.service.HistoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.Set;

@RestController
public class HistoryController {
    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @PostMapping("/history")
    public ResultResponse addFavorite(@RequestBody HistoryRequestBody body, HttpSession session) {
        SessionUtils.requireSession(session);
        historyService.addFavorite(body.userId, body.favorite);
        return new ResultResponse("SUCCESS");
    }

    @GetMapping("/history")
    public Set<Item> getFavorites(@RequestParam("user_id") String userId, HttpSession session) {
        SessionUtils.requireSession(session);
        return historyService.getFavorites(userId);
    }

    @DeleteMapping("/history")
    public ResultResponse removeFavorite(@RequestBody HistoryRequestBody body, HttpSession session) {
        SessionUtils.requireSession(session);
        historyService.removeFavorite(body.userId, body.favorite.getId());
        return new ResultResponse("SUCCESS");
    }
}
