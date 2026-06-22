package com.example.jobrec.controller;

import com.example.jobrec.entity.LoginRequestBody;
import com.example.jobrec.entity.LoginResponseBody;
import com.example.jobrec.entity.RegisterRequestBody;
import com.example.jobrec.entity.ResultResponse;
import com.example.jobrec.db.MySQLConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

@RestController
public class AuthController {

    @PostMapping("/register")
    public ResultResponse register(@RequestBody RegisterRequestBody body) {
        MySQLConnection connection = new MySQLConnection();
        ResultResponse resultResponse;
        if (connection.addUser(body.userId, body.password, body.firstName, body.lastName)) {
            resultResponse = new ResultResponse("OK");
        } else {
            resultResponse = new ResultResponse("User Already Exists");
        }
        connection.close();
        return resultResponse;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseBody> login(@RequestBody LoginRequestBody body, HttpSession session) {
        MySQLConnection connection = new MySQLConnection();
        if (connection.verifyLogin(body.userId, body.password)) {
            session.setAttribute("user_id", body.userId);
            LoginResponseBody response = new LoginResponseBody(
                    "OK", body.userId, connection.getFullname(body.userId));
            connection.close();
            return ResponseEntity.ok(response);
        }
        connection.close();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new LoginResponseBody("Login failed, user id and passcode do not exist.", null, null));
    }

    @GetMapping("/logout")
    public ResultResponse logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        return new ResultResponse("OK");
    }
}
