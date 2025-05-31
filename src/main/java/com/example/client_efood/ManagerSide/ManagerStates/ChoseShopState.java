package com.example.client_efood.ManagerSide.ManagerStates;

import com.example.client_efood.Domain.Utils;
import com.example.client_efood.ManagerSide.ManagerHandler;
import com.example.client_efood.Domain.Shop;
import com.example.client_efood.MessagePKG.MessageType;
import com.example.client_efood.StatePattern.HandlerInfo;
import com.example.client_efood.StatePattern.LockStatus;
import com.example.client_efood.StatePattern.StateArguments;
import com.example.client_efood.StatePattern.StateTransition;

import java.io.IOException;

public class ChoseShopState extends ManagerState {

    private static void printChoices(){
        System.out.println("0. Go Back.");
        System.out.println("1. Add Product.");
        System.out.println("2. Add old product.");
        System.out.println("3. Remove Product.");
        System.out.println("4. Add Available Product.");
        System.out.println("5. Remove Available Product.");
        System.out.println("6. Refresh shop");
    }

    @Override
    public StateTransition handleState(HandlerInfo handler_info, StateArguments arguments) throws IOException, ClassNotFoundException {
        System.out.println("ChoseShopState.handleState");

        LockStatus lock = new LockStatus();

        synchronized (handler_info.output_queue) {

            Runnable task = () -> {
                System.out.println("Enter the id of the shop you would like to manage: ");
            };

            Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                    task,
                    new Utils.Pair<>(true, lock)
            );
            handler_info.output_queue.add(output_entry);
            handler_info.output_queue.notify();
        }

        try {
            while(lock.input_status[0] != 1) {
                synchronized (lock.input_lock) {
                    lock.input_lock.wait();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }


        int shop_id = ManagerHandler.sc_input.nextInt();
        ManagerHandler.sc_input.nextLine();


        int choice = 0;



        Shop resulting_shop;
        synchronized (handler_info.outputStream) {
            handler_info.outputStream.writeInt(MessageType.CHOSE_SHOP.ordinal());
            handler_info.outputStream.writeInt(shop_id);
            handler_info.outputStream.flush();

        }
        synchronized (handler_info.inputStream) {
            resulting_shop = (Shop) handler_info.inputStream.readObject();
        }

        do{

            LockStatus input_lock_in = new LockStatus();
            synchronized (handler_info.output_queue) {

                Shop finalResulting_shop = resulting_shop;
                Runnable task = () -> {
                    finalResulting_shop.showManagerProducts();
                    printChoices();
                    System.out.println("Enter choice:");
                };

                Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                        task,
                        new Utils.Pair<>(true, input_lock_in)
                );
                handler_info.output_queue.add(output_entry);
                handler_info.output_queue.notify();
            }

            try {
                while(input_lock_in.input_status[0] != 1) {
                    synchronized (input_lock_in.input_lock) {
                        input_lock_in.input_lock.wait();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            choice = ManagerHandler.sc_input.nextInt();

            switch (choice){
                case 1 -> handleAddProduct(handler_info);
                case 2 -> handleAddOldProduct(handler_info);
                case 3 -> handleRemoveProduct(handler_info);
                case 4 -> handleAddAvailableProduct(handler_info);
                case 5 -> handleRemoveAvailableProduct(handler_info);
                case 6 -> {
                    synchronized (handler_info.outputStream) {
                        handler_info.outputStream.writeInt(MessageType.CHOSE_SHOP.ordinal());
                        handler_info.outputStream.writeInt(shop_id);
                        handler_info.outputStream.flush();

                        synchronized (handler_info.inputStream) {
                            resulting_shop = (Shop) handler_info.inputStream.readObject();
                        }
                    }
                }
            }

        }while (choice != 0);

        synchronized (handler_info.transition_queue){
            handler_info.transition_queue.add(new StateTransition(State.INITIAL.getCorresponding_state(), null));
            handler_info.transition_queue.notify();
        }
        return null;
    }

    private void handleAddOldProduct(HandlerInfo handler_info) {
        System.out.print("Enter the product ID of the product you want to add back: ");
        int product_id = ManagerHandler.sc_input.nextInt();

        new Thread(() -> {
            boolean successfully_removed = false;
            try {
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeInt(MessageType.ADD_OLD_PRODUCT_TO_SHOP.ordinal());
                    handler_info.outputStream.writeInt(product_id);
                    handler_info.outputStream.flush();
                }
                synchronized (handler_info.inputStream) {
                    successfully_removed = handler_info.inputStream.readBoolean();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            synchronized (handler_info.output_queue){
                boolean finalSuccessfully_removed = successfully_removed;

                Runnable task = () -> {
                    if (finalSuccessfully_removed)
                        System.out.println("Product was successfully addded back.");
                    else
                        System.out.println("Failure on adding back the product.");
                };

                Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                        task,
                        new Utils.Pair<>(false, null)
                );
                handler_info.output_queue.add(output_entry);
                handler_info.output_queue.notify();

            }
        }).start();
    }

    private void handleAddProduct(HandlerInfo handler_info) throws IOException, ClassNotFoundException {
        handler_info.outputStream.writeInt(MessageType.ADD_PRODUCT_TO_SHOP.ordinal());
        handler_info.outputStream.flush();

        System.out.println("Give product name: ");
        ManagerHandler.sc_input.nextLine();
        String name = ManagerHandler.sc_input.nextLine();

        System.out.println("Give product type: ");
        String type = ManagerHandler.sc_input.next();

        System.out.println("Give product available amount: ");
        int available_amount = ManagerHandler.sc_input.nextInt();

        System.out.println("Give product price: ");
        float price = ManagerHandler.sc_input.nextFloat();

        new Thread(() -> {
            boolean successfully_added = false;
            try {
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeUTF(name);
                    handler_info.outputStream.writeUTF(type);
                    handler_info.outputStream.writeInt(available_amount);
                    handler_info.outputStream.writeFloat(price);
                    handler_info.outputStream.flush();

                }
                synchronized (handler_info.inputStream) {
                    successfully_added = handler_info.inputStream.readBoolean();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            synchronized (handler_info.output_queue){
                boolean finalSuccessfully_added = successfully_added;

                Runnable task = () -> {
                    if (finalSuccessfully_added)
                        System.out.println("Product was successfully added.");
                    else
                        System.out.println("Failure on adding the product.");
                };

                Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                        task,
                        new Utils.Pair<>(false, null)
                );
                handler_info.output_queue.add(output_entry);
                handler_info.output_queue.notify();
            }
        }).start();

    }

    private void handleRemoveProduct(HandlerInfo handler_info) throws IOException, ClassNotFoundException {
        System.out.print("Enter the product ID of the product you want to remove: ");
        int product_id = ManagerHandler.sc_input.nextInt();

        new Thread(() -> {
            boolean successfully_removed = false;
            try {
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeInt(MessageType.REMOVE_PRODUCT_FROM_SHOP.ordinal());
                    handler_info.outputStream.writeInt(product_id);
                    handler_info.outputStream.flush();
                }
                synchronized (handler_info.inputStream) {
                    successfully_removed = handler_info.inputStream.readBoolean();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            synchronized (handler_info.output_queue){
                boolean finalSuccessfully_removed = successfully_removed;

                Runnable task = () -> {
                    if (finalSuccessfully_removed)
                        System.out.println("Product was successfully removed.");
                    else
                        System.out.println("Failure on removing the product.");
                };

                Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                        task,
                        new Utils.Pair<>(false, null)
                );
                handler_info.output_queue.add(output_entry);
                handler_info.output_queue.notify();

            }
        }).start();
    }

    private void handleAddAvailableProduct(HandlerInfo handler_info) throws IOException {
        System.out.print("Enter the product ID of the product you want to add: ");
        int product_id = ManagerHandler.sc_input.nextInt();

        System.out.print("Enter the quantity to be added. ");
        int quantity = ManagerHandler.sc_input.nextInt();

        new Thread(() -> {
            boolean successfully_added = false;
            try {
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeInt(MessageType.ADD_PRODUCT_STOCK.ordinal());
                    handler_info.outputStream.writeInt(product_id);
                    handler_info.outputStream.writeInt(quantity);
                    handler_info.outputStream.flush();
                }

                successfully_added = handler_info.inputStream.readBoolean();

            }catch (IOException e){
                throw new RuntimeException(e);
            }

            synchronized (handler_info.output_queue){
                boolean finalSuccessfully_added = successfully_added;

                Runnable task = () -> {
                    if (finalSuccessfully_added)
                        System.out.println("Product was successfully added.");
                    else
                        System.out.println("Failure on adding the product.");
                };

                Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                        task,
                        new Utils.Pair<>(false, null)
                );
                handler_info.output_queue.add(output_entry);
                handler_info.output_queue.notify();
            }
        }).start();
    }

    private void handleRemoveAvailableProduct(HandlerInfo handler_info) throws IOException, ClassNotFoundException {

        System.out.println("Enter the product ID of the product you want to remove: ");
        int product_id = ManagerHandler.sc_input.nextInt();

        System.out.println("Enter the quantity to be removed.");
        int quantity = ManagerHandler.sc_input.nextInt();

        new Thread(() -> {
            boolean successfully_removed = false;
            try {
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeInt(MessageType.REMOVE_PRODUCT_STOCK.ordinal());
                    handler_info.outputStream.writeInt(product_id);
                    handler_info.outputStream.writeInt(quantity);
                    handler_info.outputStream.flush();

                }
                synchronized (handler_info.inputStream) {
                    successfully_removed = handler_info.inputStream.readBoolean();
                }
            }catch (IOException e){
                throw new RuntimeException(e);
            }

            synchronized (handler_info.output_queue){
                boolean finalSuccessfully_removed = successfully_removed;
                Runnable task = () -> {
                    if (finalSuccessfully_removed)
                        System.out.println("Product stock was successfully removed.");
                    else
                        System.out.println("Failure on removing product stock.");
                };

                Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                        task,
                        new Utils.Pair<>(false, null)
                );
                handler_info.output_queue.add(output_entry);
                handler_info.output_queue.notify();

            }

        }).start();
    }
}
