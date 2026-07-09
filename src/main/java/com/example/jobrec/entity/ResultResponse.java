package com.example.jobrec.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResultResponse {
    @JsonProperty("status")
    public String result;

    public ResultResponse() {
    }

    public ResultResponse(String result) {
        this.result = result;
    }
}
