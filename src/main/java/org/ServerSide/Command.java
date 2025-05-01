package org.ServerSide;

import java.io.Serializable;
import java.util.Arrays;

public class Command {
    public enum CommandTypeClient implements Serializable {
        QUIT,
        DEFAULT,
        END,
        GET_CART,
        ADD_TO_CART,
        REMOVE_FROM_CART,
        CLEAR_CART,
        FILTER,
        CHOSE_SHOP,
        CHECKOUT
    }

    public enum CommandTypeManager {
        QUIT,
        DEFAULT,
        END,
        CHOSE_SHOP,
        GET_SHOPS,
        ADD_SHOP,
        ADD_PRODUCT,
        REMOVE_PRODUCT,
        ADD_AVAILABLE_PRODUCT,
        REMOVE_AVAILABLE_PRODUCT,
        GET_PRODUCT_CATEGORY_SALES,
        GET_SHOP_CATEGORY_SALES
    }

    private String command;
    private String[] args;

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

    public String getCommand() {
        return this.command;
    }

    public String[] getArgs() {
        return this.args;
    }

}
