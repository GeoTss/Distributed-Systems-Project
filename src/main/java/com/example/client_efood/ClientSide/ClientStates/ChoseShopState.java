package com.example.client_efood.ClientSide.ClientStates;

import com.example.client_efood.ClientSide.ClientHandler;
import com.example.client_efood.ClientSide.ClientStates.ClientStateArgs.ChoseShopArgs;
import com.example.client_efood.Domain.Utils;
import com.example.client_efood.MessagePKG.MessageType;
import com.example.client_efood.StatePattern.HandlerInfo;
import com.example.client_efood.StatePattern.LockStatus;
import com.example.client_efood.StatePattern.StateArguments;
import com.example.client_efood.Domain.Cart.CartStatus;
import com.example.client_efood.Domain.Cart.ReadableCart;
import com.example.client_efood.Domain.CheckoutResultWrapper;
import com.example.client_efood.Domain.Shop;
import com.example.client_efood.StatePattern.StateTransition;

import java.io.IOException;
import java.io.InterruptedIOException;

public class ChoseShopState extends ClientStates {

    private static void printChoices() {
        System.out.println("0. Go back to home screen.");
        System.out.println("1. Go back to previously viewed shops.");
        System.out.println("2. Checkout.");
        System.out.println("3. Add to cart.");
        System.out.println("4. Remove from cart.");
        System.out.println("5. Show cart.");
        System.out.println("6. Refresh shop");
    }

    @Override
    public StateTransition handleState(HandlerInfo handler_info, StateArguments arguments) throws IOException, ClassNotFoundException {
        System.out.println("ChoseShopState.handleState");
        ChoseShopArgs args = (ChoseShopArgs) arguments;

        Shop resulting_shop;

        try {
            synchronized (handler_info.outputStream) {
                handler_info.outputStream.writeInt(MessageType.CHOSE_SHOP.ordinal());
                handler_info.outputStream.writeInt(args.shop_id);
                handler_info.outputStream.flush();

                synchronized (handler_info.inputStream) {
                    resulting_shop = (Shop) handler_info.inputStream.readObject();
                }
            }

        } catch (ClassNotFoundException | InterruptedIOException e) {
            throw new RuntimeException(e);
        }

        int choice = 0;
        do {

            LockStatus lock = new LockStatus();

            Shop finalResulting_shop = resulting_shop;

            synchronized (handler_info.output_queue){
                Runnable task = () -> {
                    finalResulting_shop.showProducts();
                    printChoices();
                    System.out.println("Enter choice: ");
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

            choice = ClientHandler.sc_input.nextInt();
            ClientHandler.sc_input.nextLine();

            switch (choice) {
                case 1 -> {
                    synchronized (handler_info.outputStream) {
                        handler_info.outputStream.writeInt(MessageType.CLEAR_CART.ordinal());
                        handler_info.outputStream.flush();
                    }

                    synchronized (handler_info.transition_queue) {
                        handler_info.transition_queue.add(new StateTransition(State.APPLY_FILTERS.getCorresponding_state(), null));
                        handler_info.transition_queue.notify();
                    }
                    return null;
                }
                case 2 -> handleCheckout(handler_info);
                case 3 -> handleAddToCart(handler_info);
                case 4 -> handleRemoveFromCart(handler_info);
                case 5 -> handleShowCart(handler_info);
                case 6 -> {
                    try {
                        synchronized (handler_info.outputStream) {
                            handler_info.outputStream.writeInt(MessageType.CHOSE_SHOP.ordinal());
                            handler_info.outputStream.writeInt(args.shop_id);
                            handler_info.outputStream.flush();

                            synchronized (handler_info.inputStream) {
                                resulting_shop = (Shop) handler_info.inputStream.readObject();
                            }
                        }

                    } catch (ClassNotFoundException | InterruptedIOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

        }while (choice != 0);

        synchronized (handler_info.outputStream) {
            handler_info.outputStream.writeInt(MessageType.CLEAR_CART.ordinal());
            handler_info.outputStream.flush();
        }
        synchronized (handler_info.transition_queue){
            handler_info.transition_queue.add(new StateTransition(State.INITIAL.getCorresponding_state(), null));
            handler_info.transition_queue.notify();
        }
        return null;
    }

    private void handleCheckout(HandlerInfo handler_info) {
        new Thread(() -> {
            CheckoutResultWrapper checkout_result;
            try {
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeInt(MessageType.CHECKOUT.ordinal());
                    handler_info.outputStream.flush();

                    synchronized (handler_info.inputStream) {
                        checkout_result = (CheckoutResultWrapper) handler_info.inputStream.readObject();
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            final CheckoutResultWrapper checkout_result_f = checkout_result;
            Runnable task = () -> {
                if (checkout_result_f.in_sync_status == CartStatus.OUT_OF_SYNC)
                    System.out.println("Couldn't checkout. Cart out of sync.");
                else if (!checkout_result_f.checked_out)
                    System.out.println("Couldn't checkout. Insufficient funds");
                else
                    System.out.println("Checked out successfully.");
            };

            synchronized (handler_info.output_queue) {
                handler_info.output_queue.add(
                        new Utils.Pair<>(task, new Utils.Pair<>(false, null))
                );
                handler_info.output_queue.notify();
            }

        }).start();
    }

    private void handleAddToCart(HandlerInfo handler_info) throws IOException {
        System.out.print("Enter the product ID of the product you want to add: ");
        int product_id = ClientHandler.sc_input.nextInt();

        System.out.print("Enter how many you want to be added: ");
        int quantity = ClientHandler.sc_input.nextInt();

        new Thread(() -> {
            boolean added_to_cart;

            try {
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeInt(MessageType.ADD_TO_CART.ordinal());
                    handler_info.outputStream.writeInt(product_id);
                    handler_info.outputStream.writeInt(quantity);
                    handler_info.outputStream.flush();

                    synchronized (handler_info.inputStream) {
                        added_to_cart = handler_info.inputStream.readBoolean();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            boolean finalAdded_to_cart = added_to_cart;
            Runnable task = () -> {
                if (finalAdded_to_cart)
                    System.out.println("Added product to cart successfully");
                else
                    System.out.println("Product wasn't added to cart successfully.");
            };

            synchronized (handler_info.output_queue) {
                handler_info.output_queue.add(
                        new Utils.Pair<>(task, new Utils.Pair<>(false, null))
                );
                handler_info.output_queue.notify();
            }

        }).start();
    }

    private void handleRemoveFromCart(HandlerInfo handler_info) throws IOException {
        System.out.print("Enter product ID of the product you want to remove: ");
        int product_id = ClientHandler.sc_input.nextInt();

        System.out.print("Enter how many you want to be removed: ");
        int quantity = ClientHandler.sc_input.nextInt();

        new Thread(() -> {
            boolean removed;
            try {
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeInt(MessageType.REMOVE_FROM_CART.ordinal());
                    handler_info.outputStream.writeInt(product_id);
                    handler_info.outputStream.writeInt(quantity);
                    handler_info.outputStream.flush();

                    synchronized (handler_info.inputStream) {
                        removed = handler_info.inputStream.readBoolean();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            boolean finalRemoved = removed;
            Runnable task = () -> {
                if (finalRemoved)
                    System.out.println("Removal was successful.");
                else
                    System.out.println("Removal failed.");
            };

            synchronized (handler_info.output_queue) {
                handler_info.output_queue.add(
                        new Utils.Pair<>(task, new Utils.Pair<>(false, null))
                );
                handler_info.output_queue.notify();
            }
        }).start();
    }

    private void handleShowCart(HandlerInfo handler_info) {
        new Thread(() -> {
            ReadableCart readableCart;
            try {
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.writeInt(MessageType.GET_CART.ordinal());
                    handler_info.outputStream.flush();

                    synchronized (handler_info) {
                        readableCart = (ReadableCart) handler_info.inputStream.readObject();
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            Runnable task = () -> {
                System.out.println("My Cart:");
                System.out.println(readableCart);
            };

            synchronized (handler_info.output_queue){
                handler_info.output_queue.add(
                        new Utils.Pair<>(task, new Utils.Pair<>(false, null))
                );
                handler_info.output_queue.notify();
            }
        }).start();
    }
}
