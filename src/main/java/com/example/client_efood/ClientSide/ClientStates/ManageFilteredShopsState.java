package com.example.client_efood.ClientSide.ClientStates;

import com.example.client_efood.ClientSide.ClientHandler;
import com.example.client_efood.ClientSide.ClientStates.ClientStateArgs.ChoseShopArgs;
import com.example.client_efood.Domain.Shop;
import com.example.client_efood.Domain.Utils;
import com.example.client_efood.StatePattern.HandlerInfo;
import com.example.client_efood.StatePattern.LockStatus;
import com.example.client_efood.StatePattern.StateArguments;
import com.example.client_efood.ClientSide.ClientStates.ClientStateArgs.ManageFilteredShopsArgs;
import com.example.client_efood.StatePattern.StateTransition;

import java.io.IOException;
import java.util.ArrayList;

public class ManageFilteredShopsState extends ClientStates {

    private boolean inShopList(ArrayList<Shop> shops, int shop_id){
        for(Shop shop: shops){
            if(shop.getId() == shop_id)
                return true;
        }
        return false;
    }

    @Override
    public StateTransition handleState(HandlerInfo handler_info, StateArguments arguments) throws IOException {
        System.out.println("ManageFilteredShopsState.handleState");
        ManageFilteredShopsArgs shop_args = (ManageFilteredShopsArgs) arguments;
//        shop_args.filtered_shops.forEach(System.out::println);
        LockStatus lock = new LockStatus();

        synchronized (handler_info.output_queue) {
            Runnable task = () -> {
                System.out.println("0. Go Back.");
                System.out.println("1. Select shop.");
                System.out.print("Enter choice: ");
            };

            Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                    task,
                    new Utils.Pair<>(true, lock)
            );
            handler_info.output_queue.add(output_entry);
            handler_info.output_queue.notify();
        }
        try {
             while(lock.input_status[0] != 1){
                 synchronized (lock.input_lock) {
                     lock.input_lock.wait();
                 }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int choice = ClientHandler.sc_input.nextInt();

        if(choice == 0){

            synchronized (handler_info.transition_queue){
                handler_info.transition_queue.add(new StateTransition(State.INITIAL.getCorresponding_state(), null));
                handler_info.transition_queue.notify();
            }
        } else if (choice == 1) {

            LockStatus _lock = new LockStatus();

            synchronized (handler_info.output_queue) {
                Runnable task = () -> {
                    System.out.print("Enter shop ID: ");
                };

                Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                        task,
                        new Utils.Pair<>(true, _lock)
                );
                handler_info.output_queue.add(output_entry);
                handler_info.output_queue.notify();
            }
            try {
                while(_lock.input_status[0] != 1){
                    synchronized (_lock.input_lock) {
                        _lock.input_lock.wait();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            int shop_id = ClientHandler.sc_input.nextInt();
            while(!inShopList(shop_args.filtered_shops, shop_id)){
                System.out.print("The ID you entered isn't in the filtered shop list. Please enter it again: ");
                shop_id = ClientHandler.sc_input.nextInt();
            }

            ChoseShopArgs choseShopArgs = new ChoseShopArgs();
            choseShopArgs.shop_id = shop_id;

            synchronized (handler_info.transition_queue){
                handler_info.transition_queue.add(new StateTransition(State.CHOSE_SHOP.getCorresponding_state(), choseShopArgs));
                handler_info.transition_queue.notify();
            }

            return null;
        }

        return null;
    }

}
