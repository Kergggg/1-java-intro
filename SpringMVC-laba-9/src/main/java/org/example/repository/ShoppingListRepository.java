package org.example.repository;

import org.example.model.ShoppingItem;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class ShoppingListRepository {
    private final Map<Long, ShoppingItem> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public List<ShoppingItem> findAll() {
        return new ArrayList<>(storage.values());
    }

    public ShoppingItem save(ShoppingItem item) {
        if (item.getId() == null) {
            item.setId(idGenerator.getAndIncrement());
        }
        storage.put(item.getId(), item);
        return item;
    }

    public ShoppingItem findById(Long id) {
        return storage.get(id);
    }

    public void deleteById(Long id) {
        storage.remove(id);
    }
}