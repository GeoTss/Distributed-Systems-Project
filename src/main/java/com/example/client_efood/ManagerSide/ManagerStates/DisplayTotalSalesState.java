package com.example.client_efood.ManagerSide.ManagerStates;

import com.example.client_efood.Domain.Utils;
import com.example.client_efood.ManagerSide.ManagerHandler;
import com.example.client_efood.MessagePKG.MessageType;
import com.example.client_efood.StatePattern.HandlerInfo;
import com.example.client_efood.StatePattern.LockStatus;
import com.example.client_efood.StatePattern.StateArguments;
import com.example.client_efood.StatePattern.StateTransition;

import java.io.IOException;
import java.util.ArrayList;

public class DisplayTotalSalesState extends ManagerState {
    @Override
    public StateTransition handleState(HandlerInfo handler_info, StateArguments arguments) throws IOException, ClassNotFoundException {
        System.out.println("DisplayTotalSalesState.handleState");

        int command;
        LockStatus lock;
        do {
            lock = new LockStatus();
            synchronized (handler_info.output_queue) {
                Runnable task = () -> {
                    System.out.println("0. Go Back.");
                    System.out.println("1. Display Shop Category Sales.");
                    System.out.println("2. Display Product Category Sales.");
                    System.out.print("Enter command: ");
                };
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

            command = ManagerHandler.sc_input.nextInt();

            if(command != 0) {
                synchronized (handler_info.output_queue) {
                    Runnable task = () -> System.out.print("Give category: ");
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
                ManagerHandler.sc_input.nextLine();
                String category = ManagerHandler.sc_input.nextLine();

                switch (command) {
                    case 1 -> handleShopCategory(handler_info, category);
                    case 2 -> handleProductCategory(handler_info, category);
                }
            }
        }while (command != 0);


        synchronized (handler_info.transition_queue) {
            handler_info.transition_queue.add(new StateTransition(State.INITIAL.getCorresponding_state(), null));
            handler_info.transition_queue.notify();
        }

        return null;
    }

    public void handleShopCategory(HandlerInfo handler_info, String category) {

        new Thread(() -> {
            Utils.Pair<ArrayList<Utils.Pair<String, Integer>>, Integer> resulting_shops_stats;
            try {
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeInt(MessageType.GET_SHOP_CATEGORY_SALES.ordinal());
                    handler_info.outputStream.writeUTF(category);
                    handler_info.outputStream.flush();
                }

                resulting_shops_stats = (Utils.Pair<ArrayList<Utils.Pair<String, Integer>>, Integer>) handler_info.inputStream.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            Utils.Pair<ArrayList<Utils.Pair<String, Integer>>, Integer> finalResult = resulting_shops_stats;
            Runnable output_task = () -> {
                System.out.println("Shop category sales query results:");
                finalResult.first.forEach(System.out::println);
                System.out.println("Total: " + finalResult.second);
            };

            synchronized (handler_info.output_queue) {
                Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                        output_task,
                        new Utils.Pair<>(false, null)
                );
                handler_info.output_queue.add(output_entry);
                handler_info.output_queue.notify();
            }

        }).start();
    }

    public void handleProductCategory(HandlerInfo handler_info, String category) {

        new Thread(() -> {
            Utils.Pair<ArrayList<Utils.Pair<String, Integer>>, Integer> resulting_stats;
            try {
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeInt(MessageType.GET_PRODUCT_CATEGORY_SALES.ordinal());
                    handler_info.outputStream.writeUTF(category);
                    handler_info.outputStream.flush();
                }

                resulting_stats = (Utils.Pair<ArrayList<Utils.Pair<String, Integer>>, Integer>) handler_info.inputStream.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            Utils.Pair<ArrayList<Utils.Pair<String, Integer>>, Integer> finalResult = resulting_stats;
            Runnable output_task = () -> {
                System.out.println("Product category sales query results:");
                finalResult.first.forEach(System.out::println);
                System.out.println("Total: " + finalResult.second);
            };

            synchronized (handler_info.output_queue) {
                Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                        output_task,
                        new Utils.Pair<>(false, null)
                );
                handler_info.output_queue.add(output_entry);
                handler_info.output_queue.notify();
            }

        }).start();
    }
}
