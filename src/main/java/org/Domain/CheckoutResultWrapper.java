package org.Domain;

import org.Domain.Cart.CartStatus;

import java.io.Serializable;

public class CheckoutResultWrapper implements Serializable {
    public CartStatus in_sync_status;
    public boolean checked_out;
}
