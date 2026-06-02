package org.example.integrationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.ShoppingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ShoppingListControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andDo(result -> {
                    String content = result.getResponse().getContentAsString();
                });
    }

    //тест на добавление и получение товара
    @Test
    void testAddAndGetItem() throws Exception {
        ShoppingItem newItem = new ShoppingItem(null, "Хлеб", false);

        String response = mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newItem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Хлеб"))
                .andExpect(jsonPath("$.purchased").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        ShoppingItem createdItem = objectMapper.readValue(response, ShoppingItem.class);
        Long id = createdItem.getId();

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].name").value("Хлеб"))
                .andExpect(jsonPath("$[?(@.id==" + id + ")].purchased").value(false));
    }

    //тест на отметку товара купленным
    @Test
    void testMarkPurchased() throws Exception {
        ShoppingItem newItem = new ShoppingItem(null, "Молоко", false);
        String response = mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newItem)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ShoppingItem createdItem = objectMapper.readValue(response, ShoppingItem.class);
        Long id = createdItem.getId();

        mockMvc.perform(put("/api/items/{id}/purchased", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased").value(true));

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].purchased").value(true));
    }
}