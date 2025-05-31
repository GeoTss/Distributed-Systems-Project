package com.example.client_efood.Domain;

import com.example.client_efood.Domain.Cart.CartStatus;

import java.io.Serializable;

public class CheckoutResultWrapper implements Serializable {
    private static final long serialVersionUID = 4L;

    public CartStatus in_sync_status;
    public boolean checked_out;
}
