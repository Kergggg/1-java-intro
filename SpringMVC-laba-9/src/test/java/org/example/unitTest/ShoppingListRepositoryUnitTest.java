package org.example.unitTest;

import org.example.model.ShoppingItem;
import org.example.repository.ShoppingListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ShoppingListRepositoryUnitTest {

    @InjectMocks
    private ShoppingListRepository repository;

    @BeforeEach
    void setUp() {
        }

    //тест на сохранение и поиск товара по id
    @Test
    void testSaveAndFindById() {
        ShoppingItem item = new ShoppingItem(null, "Яблоки", false);

        ShoppingItem savedItem = repository.save(item);
        Long id = savedItem.getId();
        ShoppingItem foundItem = repository.findById(id);

        assertNotNull(savedItem.getId());
        assertEquals("Яблоки", savedItem.getName());
        assertFalse(savedItem.isPurchased());
        assertNotNull(foundItem);
        assertEquals("Яблоки", foundItem.getName());
    }

    //тест на удаление товара
    @Test
    void testDeleteById() {
        ShoppingItem item = new ShoppingItem(null, "Масло", false);
        ShoppingItem savedItem = repository.save(item);
        Long id = savedItem.getId();

        repository.deleteById(id);
        ShoppingItem deletedItem = repository.findById(id);

        assertNull(deletedItem);
    }
}