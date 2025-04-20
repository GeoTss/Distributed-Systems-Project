package org.Domain;

import java.util.ArrayList;

import org.Domain.Utils.Pair;

public class Client {

    private Credentials userCredentials;
    private Location location;
    private float balance;
    private final Cart shoppingCart;
    private final ArrayList<Pair<Product, Integer>> inventory;
    private final int MAX_RANGE = 5;

    public Client(
            // String _username,
            // String _password,
            Location _location) {
        // this.userCredentials = new Credentials(_username, _password);
        this.location = _location;
        this.balance = 0;
        this.shoppingCart = new Cart();
        this.inventory = new ArrayList<>();
    }

    public double getDistance(Location _otherLoc) {
        return this.location.getDistanceTo(_otherLoc);
    }

    public void addToShoppingCart(Product _product, int _amount) {
        try {
            this.shoppingCart.add_product(_product, _amount);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void removeFromShoppingCart(Product _product, int _amount) {
        this.shoppingCart.remove_product(_product, _amount);
    }

    public void checkout() {
        float total = this.shoppingCart.getCartCost();
        if (total > this.balance) {
            System.out.println("Not enough money balance: " + this.balance + " total: " + total);
            return;
        }
        this.balance -= total;
        this.shoppingCart.getProducts().forEach((pair) -> {
            if (pair.first.getAvailableAmount() >= pair.second) {
                pair.first.sellProduct(pair.second);
                addToInventory(pair.first, pair.second);
            } else {
                System.out.println("Not enough product in stock: " + pair.first.getName() + " "
                        + pair.first.getAvailableAmount() + " available, " + pair.second + " requested.");
            }
        });
        this.shoppingCart.clear_cart();
    }

    public void addToInventory(Product _product, int _amount) {
        Pair<Product, Integer> tempPair = new Pair<>(_product, _amount);
        for (int i = 0; i < this.inventory.size(); i++) {
            Pair<Product, Integer> currPair = this.inventory.get(i);
            if (currPair.first.equals(_product)) {
                inventory.set(i, new Pair<>(currPair.first, currPair.second + _amount));
                return;
            }
        }
        this.inventory.add(tempPair);
    }

    public void setBalance(float _amount) {
        if (_amount < 0)
            return;
        this.balance += _amount;
    }

    public void rateShop(int _rating, Shop _shop) {
        _shop.updateRating(_rating);
    }

    public String getUsername() {
        return this.userCredentials.getUsername();
    }

    public String getPassword() {
        return this.userCredentials.getPassword();
    }

    public float getBalance() {
        return this.balance;
    }

    public Location getLocation() {
        return location;
    }
}