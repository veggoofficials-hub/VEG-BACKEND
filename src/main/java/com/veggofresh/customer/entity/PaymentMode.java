package com.veggofresh.customer.entity;

import com.veggofresh.platform.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * How an order is paid for. This, not the free-text label the app sends, is what the code
 * branches on.
 *
 * <ul>
 *   <li>{@link #COD}: the customer pays the delivery partner at the door. Checkout creates the
 *       orders straight away and Razorpay is never involved.</li>
 *   <li>{@link #ONLINE}: payment first. Checkout creates a Razorpay payment hold and the orders
 *       only exist once the payment is authorized.</li>
 * </ul>
 */
public enum PaymentMode {
    COD,
    ONLINE;

    /**
     * Reads the {@code paymentMethodId} label the app sends at checkout.
     *
     * <ul>
     *   <li>{@code COD} (or {@code CASH_ON_DELIVERY}) is {@link #COD}.</li>
     *   <li>{@code UPI}, {@code CARD} and {@code ONLINE} are {@link #ONLINE}.</li>
     *   <li>Missing or blank is {@link #ONLINE}, which keeps older clients that send nothing on the
     *       payment-first flow they already know.</li>
     *   <li>Anything else (for example {@code WALLET}, which checkout does not support) is
     *       rejected, rather than quietly sending the customer to Razorpay.</li>
     * </ul>
     */
    public static PaymentMode fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return ONLINE;
        }
        switch (label.trim().toUpperCase(Locale.ROOT)) {
            case "COD":
            case "CASH_ON_DELIVERY":
                return COD;
            case "ONLINE":
            case "UPI":
            case "CARD":
                return ONLINE;
            default:
                throw new BusinessException("PAYMENT_METHOD_NOT_SUPPORTED",
                        "Unsupported payment method: " + label.trim(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * The label to store on the order for display: what the app sent, upper-cased, or the mode's own
     * name when it sent nothing. {@code CASH_ON_DELIVERY} is shown as {@code COD}.
     */
    public static String displayLabel(String label) {
        PaymentMode mode = fromLabel(label);
        if (label == null || label.isBlank() || mode == COD) {
            return mode.name();
        }
        return label.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * The label to keep when a payment-first checkout turns into orders. An online payment can only
     * ever produce an online order, so anything that is not a known online label (for example a
     * stale {@code COD} left in a checkout snapshot) becomes {@code ONLINE}.
     */
    public static String onlineLabel(String label) {
        if (label == null || label.isBlank()) {
            return ONLINE.name();
        }
        String upper = label.trim().toUpperCase(Locale.ROOT);
        return "UPI".equals(upper) || "CARD".equals(upper) ? upper : ONLINE.name();
    }
}
