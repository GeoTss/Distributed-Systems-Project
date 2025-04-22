package org.ServerSide;

import java.io.Serializable;
import java.util.Arrays;

public class Command {
    public enum CommandTypeClient implements Serializable {
        QUIT,
        DEFAULT,
        END,
        GET_CART,
        ADD_TO_CART, // ADD_TO_CART product_name amount
        REMOVE_FROM_CART, // REMOVE_FROM_CART product_name amount
        FILTER,
        CHOSE_SHOP,
        CHECKOUT
    }

    public enum CommandTypeManager {
        QUIT,
        DEFAULT,
        END,
        ADD_SHOP, // ADD_SHOP shop_name
        ADD_PRODUCT, // ADD_PRODUCT product_name
        ADD_AVAILABLE_PRODUCT, // ADD_AVAILABLE_PRODUCT product_name amount
        REMOVE_AVAILABLE_PRODUCT, // REMOVE_AVAILABLE_PRODUCT product_name amount
    }

    private String command;
    private String[] args;
    private boolean isManager = false;

    public Command(String _line) {
        String[] temp = _line.split(" ");
        this.command = temp[0];
        this.args = Arrays.copyOfRange(temp, 1, temp.length);
    }

    public CommandTypeClient getCommandClientType() {
        return CommandTypeClient.valueOf(this.command.toUpperCase());
    }

    public CommandTypeManager getCommandManagerType() {
        return CommandTypeManager.valueOf(this.command.toUpperCase());
    }

    public void setManager(boolean _isManager) {
        this.isManager = _isManager;
    }

    public String getCommand() {
        return this.command;
    }

    public String[] getArgs() {
        return this.args;
    }

}
