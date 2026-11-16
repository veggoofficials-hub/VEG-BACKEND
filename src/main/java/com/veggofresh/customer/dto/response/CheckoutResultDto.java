package com.veggofresh.customer.dto.response;

import com.veggofresh.payment.dto.PaymentHoldResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PHASE 2 — result of a multi-cart checkout: one payment can fan out into N
 * independent orders (PROJECT_STATE section 2). Any cart whose vendor
 * overlap broke between add-time and checkout-time is reported in
 * {@link #issues} and excluded from {@link #orders} rather than blocking
 * the rest of checkout.
 *
 * PAYMENT FIRST (paymentMode ONLINE): {@link #orders} is ALWAYS EMPTY. Orders are created when the
 * payment is authorized, so their ids come from the verify response, not from checkout. Carts that
 * were left out are still reported in {@link #issues}.
 *
 * <p>CASH ON DELIVERY (paymentMode COD): there is no payment step, so the orders are created by this
 * call itself. {@link #orders} holds one order per cart that was ordered and {@link #paymentHold} is null.
 *
 * PAYMENT INTEGRATION: paymentHold carries the Razorpay order details
 * (razorpayOrderId, keyId, totalAmount) needed to open Checkout.js.
 * It is null only when all orders failed checkout (CHECKOUT_FAILED is thrown).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResultDto {
    private List<OrderResponseDto> orders;
    private List<CheckoutIssueDto> issues;
    /** Razorpay payment hold — use razorpayOrderId + razorpayKeyId to open Checkout.js. Null for COD. */
    private PaymentHoldResponseDto paymentHold;

    /**
     * "COD" or "ONLINE". The client branches on this: ONLINE opens Razorpay using paymentHold, COD goes
     * straight to the order-placed screen using orders.
     */
    private String paymentMode;

    /** Set when a coupon was held/given but did not fit these carts, so no discount was applied. */
    private String promoMessage;
}
