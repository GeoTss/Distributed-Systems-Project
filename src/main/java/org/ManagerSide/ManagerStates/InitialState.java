package org.ManagerSide.ManagerStates;

import org.Domain.Shop;
import org.ManagerSide.ManagerHandler;
import org.ManagerSide.ManagerStates.ManagerStateArgs.ChoseShopArgs;
import org.MessagePKG.MessageType;
import org.StatePattern.HandlerInfo;
import org.StatePattern.LockStatus;
import org.StatePattern.StateArguments;
import org.StatePattern.StateTransition;
import org.Domain.Utils;

import java.io.IOException;
import java.util.ArrayList;

public class InitialState extends ManagerState {

    @Override
    public StateTransition handleState(HandlerInfo handler_info, StateArguments arguments) throws IOException, ClassNotFoundException {
        System.out.println("InitialState.handleState");

        int command;
        do {
            LockStatus lock = new LockStatus();

            Runnable task = () -> {
                System.out.println("0. Quit.");
                System.out.println("1. Add Shop.");
                System.out.println("2. Chose Shop.");
                System.out.println("3. Display Sales.");
                System.out.print("Enter command: ");
            };

            Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                    task,
                    new Utils.Pair<>(true, lock)
            );

            synchronized (handler_info.output_queue) {
                handler_info.output_queue.add(output_entry);
                handler_info.output_queue.notify();
            }

            try {
                synchronized (lock.input_lock) {
                    while (lock.input_status[0] != 1) {
                        lock.input_lock.wait();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            command = ManagerHandler.sc_input.nextInt();
            ManagerHandler.sc_input.nextLine();

            if (command < 0 || command > 3) {
                System.out.println("Invalid input, try again.");
            }

        } while (command < 0 || command > 3);


        switch (command) {
            case 0 -> {
                synchronized (handler_info.transition_queue){
                    handler_info.transition_queue.add(null);
                    handler_info.transition_queue.notify();
                }
                synchronized (handler_info.output_queue){
                    handler_info.output_queue.add(null);
                    handler_info.output_queue.notify();
                }
                return null;
            }
            case 1 -> {
                handleAddShop(handler_info);
                break;
            }
            case 2 -> {
                ChoseShopArgs shop_args = new ChoseShopArgs();

                new Thread(() -> {
                    ArrayList<Shop> shops;
                    try {
                        synchronized (handler_info.outputStream) {
                            handler_info.outputStream.writeInt(MessageType.GET_SHOPS.ordinal());
                            handler_info.outputStream.flush();
                        }

                        shops = (ArrayList<Shop>) handler_info.inputStream.readObject();
                    } catch (IOException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }

                    ArrayList<Shop> finalShops = shops;
                    Runnable task = () -> finalShops.forEach(System.out::println);

                    synchronized (handler_info.output_queue) {
                        Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                                task,
                                new Utils.Pair<>(false, null)
                        );
                        handler_info.output_queue.add(output_entry);
                        handler_info.output_queue.notify();
                    }

                    synchronized (handler_info.transition_queue) {
                        handler_info.transition_queue.add(new StateTransition(State.CHOSE_SHOP.getCorresponding_state(), shop_args));
                        handler_info.transition_queue.notify();
                    }

                }).start();

                return null;
            }
            case 3 -> {
                synchronized (handler_info.transition_queue) {
                    handler_info.transition_queue.add(new StateTransition(State.DISPLAY_TOTAL_SALES.getCorresponding_state(), null));
                    handler_info.transition_queue.notify();
                }
                return null;
            }
        }

        synchronized (handler_info.transition_queue){
            handler_info.transition_queue.add(new StateTransition(State.INITIAL.getCorresponding_state(), null));
            handler_info.transition_queue.notify();
        }
        return null;
    }

    public void handleAddShop(HandlerInfo handler_info) throws IOException {

        String name = promptInput("Enter shop name: ", handler_info);
        double latitude = Double.parseDouble(promptInput("Enter latitude: ", handler_info));
        double longitude = Double.parseDouble(promptInput("Enter longitude: ", handler_info));
        String food_category = promptInput("Enter food category: ", handler_info);
        float initial_stars = Float.parseFloat(promptInput("Enter initial stars: ", handler_info));
        int initial_votes = Integer.parseInt(promptInput("Enter initial votes: ", handler_info));
        String logo_path = promptInput("Enter logo path: ", handler_info);

        new Thread(() -> {
            try {
                boolean shop_added_successfully = false;
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeInt(MessageType.ADD_SHOP.ordinal());
                    handler_info.outputStream.writeUTF(name);
                    handler_info.outputStream.writeDouble(latitude);
                    handler_info.outputStream.writeDouble(longitude);
                    handler_info.outputStream.writeUTF(food_category);
                    handler_info.outputStream.writeFloat(initial_stars);
                    handler_info.outputStream.writeInt(initial_votes);
                    handler_info.outputStream.writeUTF(logo_path);
                    handler_info.outputStream.flush();
                    shop_added_successfully = handler_info.inputStream.readBoolean();
                }

                String message = shop_added_successfully
                        ? "Shop added successfully."
                        : "Failure in adding shop, aborting.";

                Runnable task = () -> System.out.println(message);
                synchronized (handler_info.output_queue) {
                    Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                            task,
                            new Utils.Pair<>(false, null)
                    );
                    handler_info.output_queue.add(output_entry);
                    handler_info.output_queue.notify();
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    private String promptInput(String prompt, HandlerInfo handler_info) {
        LockStatus lock = new LockStatus();
        synchronized (handler_info.output_queue) {
            Runnable task = () -> System.out.print(prompt);
            Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                    task,
                    new Utils.Pair<>(true, lock)
            );
            handler_info.output_queue.add(output_entry);
            handler_info.output_queue.notify();
        }

        try {
            while (lock.input_status[0] != 1) {
                synchronized (lock.input_lock) {
                    lock.input_lock.wait();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return ManagerHandler.sc_input.nextLine();
    }
}
