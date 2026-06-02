package org.example.model;

public class ShoppingItem {
    private Long id;
    private String name;
    private boolean purchased;

    public ShoppingItem() {}

    public ShoppingItem(Long id, String name, boolean purchased) {
        this.id = id;
        this.name = name;
        this.purchased = purchased;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isPurchased() { return purchased; }
    public void setPurchased(boolean purchased) { this.purchased = purchased; }
}