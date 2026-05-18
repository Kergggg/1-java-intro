package org.example.controller;

import org.example.model.ShoppingItem;
import org.example.repository.ShoppingListRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items")
public class ShoppingListController {

    private final ShoppingListRepository repository;

    public ShoppingListController(ShoppingListRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ShoppingItem> getAllItems() {
        return repository.findAll();
    }

    @PostMapping
    public ShoppingItem addItem(@RequestBody ShoppingItem item) {
        if (item.getName() == null || item.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Название не может быть пустым");
        }
        item.setPurchased(false);
        return repository.save(item);
    }

    @DeleteMapping("/{id}")
    public void deleteItem(@PathVariable Long id) {
        repository.deleteById(id);
    }

    @PutMapping("/{id}/purchased")
    public ShoppingItem markPurchased(@PathVariable Long id) {
        ShoppingItem item = repository.findById(id);
        if (item == null) {
            throw new RuntimeException("Товар не найден");
        }
        item.setPurchased(true);
        return repository.save(item);
    }
}