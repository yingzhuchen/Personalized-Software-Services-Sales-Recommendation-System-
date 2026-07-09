package com.example.jobrec.controller;

import com.example.jobrec.entity.HistoryRequestBody;
import com.example.jobrec.entity.Item;
import com.example.jobrec.entity.ResultResponse;
import com.example.jobrec.service.HistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistoryController.class)
class HistoryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HistoryService historyService;

    @Test
    void getFavorites_returns403WhenSessionMissing() throws Exception {
        mockMvc.perform(get("/history").param("user_id", "user-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getFavorites_returnsCatalogItemsForAuthenticatedUser() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user_id", "user-1");
        Item item = new Item(
                "innova-crm",
                "INNOVA CRM Platform",
                "INNOVA AI",
                "$99/month",
                "INNOVA AI",
                Item.SOURCE_INNOVA_CATALOG,
                "CRM",
                null,
                "https://innova.ai/products/crm",
                new HashSet<>(Collections.singleton("crm")),
                true);

        when(historyService.getFavorites("user-1")).thenReturn(new HashSet<>(Collections.singleton(item)));

        mockMvc.perform(get("/history").param("user_id", "user-1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("innova-crm"))
                .andExpect(jsonPath("$[0].source_type").value("innova_catalog"));
    }

    @Test
    void addFavorite_returnsSuccessForAuthenticatedUser() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user_id", "user-1");
        HistoryRequestBody body = new HistoryRequestBody();
        body.userId = "user-1";
        body.favorite = new Item(
                "innova-crm",
                "INNOVA CRM Platform",
                "INNOVA AI",
                "$99/month",
                "INNOVA AI",
                Item.SOURCE_INNOVA_CATALOG,
                "CRM",
                null,
                "https://innova.ai/products/crm",
                new HashSet<>(Collections.singleton("crm")),
                false);

        mockMvc.perform(post("/history")
                        .session(session)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(historyService).addFavorite(eq("user-1"), eq(body.favorite));
    }
}
