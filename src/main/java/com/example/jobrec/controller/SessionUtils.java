package com.example.jobrec.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpSession;

final class SessionUtils {
    private SessionUtils() {
    }

    static void requireSession(HttpSession session) {
        if (session == null || session.getAttribute("user_id") == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session Invalid");
        }
    }
}
