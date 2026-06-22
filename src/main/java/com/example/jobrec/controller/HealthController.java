package com.example.jobrec.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/test")
    public String healthCheck() {
        return "Hello World";
    }
}
