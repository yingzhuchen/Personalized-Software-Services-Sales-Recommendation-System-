package com.example.jobrec.controller;

import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.LoginRequestBody;
import com.example.jobrec.entity.RegisterRequestBody;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void register_returnsOkForNewUser() throws Exception {
        try (MockedConstruction<MySQLConnection> mysql = mockConstruction(MySQLConnection.class,
                (mock, context) -> when(mock.addUser("user-1", "secret", "Jane", "Doe")).thenReturn(true))) {

            RegisterRequestBody body = new RegisterRequestBody();
            body.userId = "user-1";
            body.password = "secret";
            body.firstName = "Jane";
            body.lastName = "Doe";

            mockMvc.perform(post("/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"user_id\":\"user-1\",\"password\":\"secret\",\"first_name\":\"Jane\",\"last_name\":\"Doe\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"));
        }
    }

    @Test
    void login_returnsUnauthorizedForInvalidCredentials() throws Exception {
        try (MockedConstruction<MySQLConnection> mysql = mockConstruction(MySQLConnection.class,
                (mock, context) -> when(mock.verifyLogin("user-1", "bad-password")).thenReturn(false))) {

            mockMvc.perform(post("/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"user_id\":\"user-1\",\"password\":\"bad-password\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("Login failed, user id and passcode do not exist."));
        }
    }

    @Test
    void login_returnsOkForValidCredentials() throws Exception {
        try (MockedConstruction<MySQLConnection> mysql = mockConstruction(MySQLConnection.class,
                (mock, context) -> {
                    when(mock.verifyLogin("user-1", "secret")).thenReturn(true);
                    when(mock.getFullname("user-1")).thenReturn("Jane Doe");
                })) {

            mockMvc.perform(post("/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"user_id\":\"user-1\",\"password\":\"secret\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.user_id").value("user-1"))
                    .andExpect(jsonPath("$.name").value("Jane Doe"));
        }
    }
}
