package org.ManagerSide.ManagerStates;

import org.Domain.Shop;
import org.ManagerSide.ManagerHandler;
import org.ManagerSide.ManagerStates.ManagerStateArgs.ManagerStateArgument;
import org.ServerSide.Command;
import org.StatePattern.HandlerInfo;
import org.StatePattern.StateArguments;
import org.StatePattern.StateTransition;

import java.io.IOException;
import java.util.ArrayList;

public class DisplayTotalSalesState extends ManagerState {
    @Override
    public StateTransition handleState(HandlerInfo handler_info, StateArguments arguments) throws IOException, ClassNotFoundException {
        System.out.println("DisplayTotalSalesState.handleState");

        int command;
        do {
            System.out.println("0. Go Back.");
            System.out.println("1. Display Shop Category Sales.");
            System.out.println("2. Display Product Category Sales.");
            System.out.print("Enter command: ");
            command = ManagerHandler.sc_input.nextInt();


            switch (command) {
                case 1 -> handleShopCategory(handler_info);
                case 2 -> handleProductCategory(handler_info);
            }

        }while(command != 0);

        return new StateTransition(State.INITIAL.getCorresponding_state(), null);
    }

    public void handleShopCategory(HandlerInfo handler_info) throws IOException, ClassNotFoundException {
        handler_info.outputStream.writeInt(Command.CommandTypeManager.GET_SHOP_CATEGORY_SALES.ordinal());

        System.out.println("Give shop category: ");
        String category =  ManagerHandler.sc_input.next();

        handler_info.outputStream.writeUTF(category);
        handler_info.outputStream.flush();

        ArrayList<Shop> shops = (ArrayList<Shop>) handler_info.inputStream.readObject();
        shops.forEach(System.out::println);
    }

    public void handleProductCategory(HandlerInfo handler_info) throws IOException, ClassNotFoundException {
        handler_info.outputStream.writeInt(Command.CommandTypeManager.GET_PRODUCT_CATEGORY_SALES.ordinal());

        System.out.println("Give product category: ");
        String category =  ManagerHandler.sc_input.next();

        handler_info.outputStream.writeUTF(category);
        handler_info.outputStream.flush();

        ArrayList<Shop> shops = (ArrayList<Shop>) handler_info.inputStream.readObject();
        shops.forEach(System.out::println);
    }

}
