package org.Domain;

import org.Filters.*;

import java.io.Serializable;
import java.util.HashMap;

public class Shop implements Rateable, Categorisable, PriceCategory, Locatable, Serializable {
    private String name;
    private Location location;
    private String foodCategory;
    private float stars;
    private int noOfVotes;
    private String logoPath;
    private HashMap<String, Product> products;

    public Shop(
        String _name,
        double _latitude,
        double _longitude,
        String _foodCategory,
        float _stars,
        int _noOfVotes,
        String _logoPath
    ) {
        this.name = _name;
        this.location = new Location(_latitude, _longitude);
        this.foodCategory = _foodCategory;
        this.stars = _stars;
        this.noOfVotes = _noOfVotes;
        this.logoPath = _logoPath;
        this.products = new HashMap<>();
    }

    public void addProduct(Product _product) {
        if (this.products.containsKey(_product.getName())) return;
        this.products.put(_product.getName(), _product);
    }

    public void removeProduct(Product _product) {
        if (!this.products.containsKey(_product.getName())) return;
        this.products.remove(_product.getName());
    }

    public void updateRating(float _rating) {
        if ((_rating >= 0) && (_rating <= 5)) {
            this.stars += _rating;
            this.noOfVotes += 1;
        }
    }

    public float getAverageOverallCost() {
        return (float) this.products.values().stream()
                .mapToDouble(Product::getPrice)
                .average()
                .orElse(0.0);
    }

    @Override
    public PriceCategoryEnum getPriceCategory() {
        float prices = getAverageOverallCost();
        if (prices <= 5) {
            return PriceCategoryEnum.LOW;
        } else if (prices <= 15) {
            return PriceCategoryEnum.MEDIUM;
        } else {
            return PriceCategoryEnum.HIGH;
        }
    }

    public float getRating() {
        return this.stars;
    }

    public String getName() {
        return this.name;
    }
    public Location getLocation() {
        return this.location;
    }
    public String getCategory() {
        return this.foodCategory;
    }
    public float getStars() {
        return this.stars;
    }
    public int getNoOfVotes() {
        return this.noOfVotes;
    }
    public String getLogoPath() {
        return this.logoPath;
    }
    public Product getProductByName(String _name) {
        return this.products.get(_name);
    }
    public HashMap<String, Product> getProducts() {
        return this.products;
    }

//    public String toString() {
//        StringBuilder builder = new StringBuilder();
//        builder.append("Shop: ").append(name).append("\n");
//        builder.append("  Location: ").append(location).append("\n");
//        builder.append("  Category: ").append(foodCategory).append("\n");
//        builder.append("  Rating: ").append(String.format("%.2f", getRating()))
//                .append(" (").append(noOfVotes).append(" reviews)\n");
//        builder.append("  Price Category: ").append(getPriceCategory()).append("\n");
//        builder.append("  Logo Path: ").append(logoPath).append("\n");
//        builder.append("  Products:\n");
//
//        for (Product product : products.values()) {
//            builder.append("    - ").append(product.getName())
//                    .append(" (").append(product.getType()).append("): ")
//                    .append(product.getAvailableAmount()).append(" available @ ")
//                    .append(product.getPrice()).append("€\n");
//        }
//
//        return builder.toString();
//    }
}