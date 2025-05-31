package com.example.client_efood.ServerSide.JSON_Deserializers;

import com.google.gson.*;
import com.example.client_efood.Domain.Product;
import com.example.client_efood.Domain.Shop;

import java.lang.reflect.Type;

public class ShopDeserializer implements JsonDeserializer<Shop> {
    @Override
    public Shop deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        Shop tempShop = new Shop(
                obj.get("StoreName").getAsString(),
                obj.get("Latitude").getAsDouble(),
                obj.get("Longitude").getAsDouble(),
                obj.get("FoodCategory").getAsString(),
                obj.get("Stars").getAsFloat(),
                obj.get("NoOfVotes").getAsInt(),
                obj.get("StoreLogo").getAsString()
        );
        JsonArray productsArray = obj.getAsJsonArray("Products");
        for (JsonElement _product : productsArray) {
            JsonObject productObj = _product.getAsJsonObject();
            Product currProduct = new Product(
                    productObj.get("ProductName").getAsString(),
                    productObj.get("ProductType").getAsString(),
                    productObj.get("Available Amount").getAsInt(),
                    productObj.get("Price").getAsFloat()
            );
            tempShop.addProduct(currProduct);
        }
        return tempShop;
    }
}