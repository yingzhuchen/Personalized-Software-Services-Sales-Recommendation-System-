package com.example.jobrec.servlet;

import com.example.jobrec.db.RedisConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.HistoryRequestBody;
import com.example.jobrec.entity.Item;
import com.example.jobrec.entity.ResultResponse;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@WebServlet(name = "HistoryServlet", urlPatterns = {"/history"})
public class HistoryServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        ObjectMapper mapper = new ObjectMapper();
        //protect servlet
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.setStatus(403);
            mapper.writeValue(response.getWriter(), new ResultResponse("Session Invalid"));
            return;
        }
        //transfer the request to class
        HistoryRequestBody body = mapper.readValue(request.getReader(), HistoryRequestBody.class);

        MySQLConnection connection = new MySQLConnection();
        //use the setFavoriteItem() that just implemented in MySQLConnection
        connection.setFavoriteItems(body.userId, body.favorite);
        connection.close();
        //check Redis
        RedisConnection redis = new RedisConnection();
        redis.deleteFavoriteResult(body.userId);
        redis.close();

        ResultResponse resultResponse = new ResultResponse("SUCCESS");
        mapper.writeValue(response.getWriter(), resultResponse);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        ObjectMapper mapper = new ObjectMapper();
        //protect servlets
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.setStatus(403);
            mapper.writeValue(response.getWriter(), new ResultResponse("Session Invalid"));
            return;
        }

        //get userID
        String userId = request.getParameter("user_id");
        //check Redis
        RedisConnection redis = new RedisConnection();
        String cachedResult = redis.getFavoriteResult(userId);
        Set<Item> items = null;
        if (cachedResult != null) {
            items = new HashSet<>(Arrays.asList(mapper.readValue(cachedResult, Item[].class)));
        } else {
            MySQLConnection connection = new MySQLConnection();
            items = connection.getFavoriteItems(userId);
            connection.close();
            redis.setFavoriteResult(userId, mapper.writeValueAsString(items));
        }
        redis.close();
        //get response
        mapper.writeValue(response.getWriter(), items);

    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        ObjectMapper mapper = new ObjectMapper();
        //protect servlets
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.setStatus(403);
            mapper.writeValue(response.getWriter(), new ResultResponse("Session Invalid"));
            return;
        }

        HistoryRequestBody body = mapper.readValue(request.getReader(), HistoryRequestBody.class);
        response.setContentType("application/json");
        MySQLConnection connection = new MySQLConnection();
        connection.unsetFavoriteItems(body.userId, body.favorite.getId());
        connection.close();

        //check Redis
        RedisConnection redis = new RedisConnection();
        redis.deleteFavoriteResult(body.userId);
        redis.close();

        ResultResponse resultResponse = new ResultResponse("SUCCESS");
        mapper.writeValue(response.getWriter(), resultResponse);
    }

}

