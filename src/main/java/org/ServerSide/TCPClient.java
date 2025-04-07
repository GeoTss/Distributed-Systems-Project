package org.ServerSide;

import java.net.Socket;
import java.io.*;

public class TCPClient {

    public static void connectToMasterServer() {
        try (Socket masterSocket = new Socket(MasterServer.SERVER_LOCAL_HOST, MasterServer.SERVER_CLIENT_PORT);) {
            System.out.println("Connected to master server.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static String addShop(String _shopName) {
        return "ADD_SHOP " + _shopName;
    }
    private static String addAvailableProduct(String _productName) {
        return "ADD_AVAILABLE_PRODUCT " + _productName;
    }
    private static String removeAvailableProduct(String _productName) {
        return "REMOVE_AVAILABLE_PRODUCT " + _productName;
    }
    private static String addProduct(String _productName) {
        return "ADD_PRODUCT " + _productName;
    }
    private static String removeProduct(String _productName) {
        return "REMOVE_PRODUCT " + _productName;
    }
    private static String displayTotalSales() {
        return "DISPLAY_TOTAL_SALES";
    }
    private static String displayShopsNear() {
        return "DISPLAY_SHOPS_NEAR";
    }
    private static String search(String _filter) {
        switch(_filter) {
            case "STARS":
                return "SEARCH_STARS";
            case "CATEGORY":
                return "SEARCH_CATEGORY";
            case "PRICE":
                return "SEARCH_PRICE";
            default:
                return "INVALID";
        }
    }
    private static String buy(String _productName, int _amount) {
        return "BUY " + _productName + " " + _amount;
    }
    private static String rateShop(String _shopName, int _rating) {
        return "RATE_SHOP " + _shopName + " " + _rating;
    }
}