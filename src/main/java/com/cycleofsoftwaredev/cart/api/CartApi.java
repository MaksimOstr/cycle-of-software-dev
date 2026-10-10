package com.cycleofsoftwaredev.cart.api;

import java.util.UUID;

/** Public interface of the Cart module: used by Ordering during checkout. */
public interface CartApi {

    /** Current content of the cart with up-to-date prices and the applied promo code discount. */
    CartSnapshot getCheckoutCart(UUID cartId);

    /** The order was created from the cart: the promo code is redeemed and the cart is emptied. */
    void completeCheckout(UUID cartId, UUID orderId);
}
