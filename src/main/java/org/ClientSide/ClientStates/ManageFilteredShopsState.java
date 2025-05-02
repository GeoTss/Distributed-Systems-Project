package org.ClientSide.ClientStates;

import org.ClientSide.ClientHandler;
import org.ClientSide.ClientStates.ClientStateArgs.ChoseShopArgs;
import org.Domain.Utils;
import org.StatePattern.HandlerInfo;
import org.StatePattern.LockStatus;
import org.StatePattern.StateArguments;
import org.ClientSide.ClientStates.ClientStateArgs.ManageFilteredShopsArgs;
import org.StatePattern.StateTransition;

import java.io.IOException;

public class ManageFilteredShopsState extends ClientStates {

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
            System.out.print("Enter shop id: ");
            int shop_id = ClientHandler.sc_input.nextInt();

            ChoseShopArgs choseShopArgs = new ChoseShopArgs();
            choseShopArgs.shop_id = shop_id;

            synchronized (handler_info.transition_queue){
                handler_info.transition_queue.add(new StateTransition(State.CHOSE_SHOP.getCorresponding_state(), choseShopArgs));
                handler_info.transition_queue.notify();
            }

            return null;
        }

        synchronized (handler_info.transition_queue){
            handler_info.transition_queue.add(new StateTransition(State.INITIAL.getCorresponding_state(), null));
            handler_info.transition_queue.notify();
        }
        return null;
    }

}
