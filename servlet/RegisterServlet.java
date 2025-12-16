package com.example.jobrec.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.RegisterRequestBody;
import com.example.jobrec.entity.ResultResponse;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;

@WebServlet(name = "RegisterServlet", urlPatterns = {"/register"})
public class RegisterServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //initiate a mapper for mapping values
        ObjectMapper mapper = new ObjectMapper();
        //read request body
        RegisterRequestBody body = mapper.readValue(request.getReader(), RegisterRequestBody.class);
        MySQLConnection connection = new MySQLConnection();
        ResultResponse resultResponse;
        //check if user already registered
        if (connection.addUser(body.userId, body.password, body.firstName, body.lastName)) {
            //if not, then add user and return "OK"
            resultResponse = new ResultResponse("OK");
        } else { //if so, then return user already exists
            resultResponse = new ResultResponse("User Already Exists");
        }
        connection.close();
        response.setContentType("application/json");
        mapper.writeValue(response.getWriter(), resultResponse);
    }

}

