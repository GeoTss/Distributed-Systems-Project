package org.ManagerSide.ManagerStates;

import org.Domain.Shop;
import org.ManagerSide.ManagerHandler;
import org.ManagerSide.ManagerStates.ManagerStateArgs.ChoseShopArgs;
import org.ManagerSide.ManagerStates.ManagerStateArgs.ManagerStateArgument;
import org.ServerSide.Command;
import org.StatePattern.HandlerInfo;
import org.StatePattern.StateArguments;
import org.StatePattern.StateTransition;

import java.io.IOException;
import java.util.*;

public class InitialState extends ManagerState {

    @Override
    public StateTransition handleState(HandlerInfo handler_info, StateArguments arguments) throws IOException, ClassNotFoundException {
        System.out.println("InitialState.handleState");

        int command;
        do {
            System.out.println("0. Quit.");
            System.out.println("1. Add Shop.");
            System.out.println("2. Chose Shop.");
            System.out.println("3. Display Sales.");
            System.out.print("Enter command: ");
            command = ManagerHandler.sc_input.nextInt();

            switch (command) {
                case 1 -> handleAddShop(handler_info);
                case 2 -> {
                    // Make this program go to ChoseShopState by giving the shop id.
                    ChoseShopArgs shop_args = new ChoseShopArgs();

                    handler_info.outputStream.writeInt(Command.CommandTypeManager.GET_SHOPS.ordinal());
                    handler_info.outputStream.flush();

                    ArrayList<Shop> shops = (ArrayList<Shop>) handler_info.inputStream.readObject();
                    shops.forEach(System.out::println);

                    System.out.println("Enter the id of the shop you would like to manage: ");
                    shop_args.shop_id = ManagerHandler.sc_input.nextInt();

                    return new StateTransition(State.CHOSE_SHOP.getCorresponding_state(), shop_args);
                }
                case 3 -> {
                    // Make this program show total sales per product for every shop.
                    return new StateTransition(State.DISPLAY_TOTAL_SALES.getCorresponding_state(), null);
                }
            }

        }while(command != 0);

        return null;
    }

    public void handleAddShop(HandlerInfo handler_info) throws IOException, ClassNotFoundException {
        handler_info.outputStream.writeInt(Command.CommandTypeManager.ADD_SHOP.ordinal());

        System.out.println("Enter shop name: ");
        String name = ManagerHandler.sc_input.next();

        System.out.println("Enter latitude: ");
        double latitude = ManagerHandler.sc_input.nextDouble();

        System.out.println("Enter longitude: ");
        double longitude = ManagerHandler.sc_input.nextDouble();

        System.out.println("Enter food category: ");
        String food_category = ManagerHandler.sc_input.next();

        System.out.println("Enter initial stars: ");
        float initial_stars = ManagerHandler.sc_input.nextFloat();

        System.out.println("Enter initial votes: ");
        int initial_votes = ManagerHandler.sc_input.nextInt();

        System.out.println("Enter logo path: ");
        String logo_path = ManagerHandler.sc_input.next();

        handler_info.outputStream.writeUTF(name);
        handler_info.outputStream.writeDouble(latitude);
        handler_info.outputStream.writeDouble(longitude);
        handler_info.outputStream.writeUTF(food_category);
        handler_info.outputStream.writeFloat(initial_stars);
        handler_info.outputStream.writeInt(initial_votes);
        handler_info.outputStream.writeUTF(logo_path);
        handler_info.outputStream.flush();

        boolean shop_added_successfully = handler_info.inputStream.readBoolean();
        if (shop_added_successfully) {
            System.out.println("Shop added successfully.");
        } else {
            System.out.println("Failure in adding shop, aborting.");
        }

    }
}
