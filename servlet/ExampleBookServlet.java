//This servlet is not a part of the final project, it's only for test and demo.

package com.example.jobrec.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.jobrec.entity.ExampleBook;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.CharBuffer;

@WebServlet(name = "ExampleBookServlet", urlPatterns = {"/example_book"})
public class ExampleBookServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //we do this when we need to add a new book to the server
        JSONObject jsonRequest = new JSONObject(IOUtils.toString(request.getReader()));
        //we are manual parsing JSON
        String title = jsonRequest.getString("title");
        String author = jsonRequest.getString("author");
        String date = jsonRequest.getString("date");
        float price = jsonRequest.getFloat("price");
        String currency = jsonRequest.getString("currency");
        int pages = jsonRequest.getInt("pages");
        String series = jsonRequest.getString("series");
        String language = jsonRequest.getString("language");
        String isbn = jsonRequest.getString("isbn");
        //take the order
        System.out.println("Title is: " + title);
        System.out.println("Author is: " + author);
        System.out.println("Date is: " + date);
        System.out.println("Price is: " + price);
        System.out.println("Currency is: " + currency);
        System.out.println("Pages is: " + pages);
        System.out.println("Series is: " + series);
        System.out.println("Language is: " + language);
        System.out.println("ISBN is: " + isbn);



        //response to client that the food is being prepared
        response.setContentType("application/json");
        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("status", "ok");
        response.getWriter().print(jsonResponse);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String keyword = request.getParameter("keyword");
        String category = request.getParameter("category");

        System.out.println("Keyword is : " + keyword);
        System.out.println("Category is : " + category);

        response.setContentType("application/json");
        JSONObject json = new JSONObject();
        json.put("title", "Harry Potter and the Sorcerer's Stone");
        json.put("author", "JK Rowling");
        json.put("date", "October 1, 1998");
        json.put("price", 11.99);
        json.put("currency", "USD");
        json.put("pages", 309);
        json.put("series", "Harry Potter");
        json.put("language", "en_US");
        json.put("isbn", "0590353403");
        response.getWriter().print(json);

        //no need to log in data manually, instead we could use Jackson to map in all the info
        response.setContentType("application/json");
        ObjectMapper mapper = new ObjectMapper();
        ExampleBook parameters = new ExampleBook("Harry Potter and the Sorcerer's Stone", "JK Rowling",
                "October 1, 1998", 11.99, "USD", 309, "Harry Potter", "en_US", "0590353403");
        mapper.writeValue(response.getWriter(), parameters);

    }
}

