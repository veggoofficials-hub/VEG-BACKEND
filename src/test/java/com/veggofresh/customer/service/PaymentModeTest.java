package com.veggofresh.customer.service;

import com.veggofresh.customer.entity.PaymentMode;
import com.veggofresh.platform.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What the app sends in {@code paymentMethodId} decides whether checkout is cash on delivery or
 * payment-first. Getting this wrong either charges a COD customer online, or creates unpaid orders for a
 * customer who meant to pay online, so the mapping is pinned down here.
 */
class PaymentModeTest {

    @Test
    @DisplayName("COD label means cash on delivery, whatever the case or spacing")
    void codLabel() {
        assertEquals(PaymentMode.COD, PaymentMode.fromLabel("COD"));
        assertEquals(PaymentMode.COD, PaymentMode.fromLabel("cod"));
        assertEquals(PaymentMode.COD, PaymentMode.fromLabel("  Cod "));
        assertEquals(PaymentMode.COD, PaymentMode.fromLabel("CASH_ON_DELIVERY"));
    }

    @Test
    @DisplayName("UPI, CARD and ONLINE are all the online, payment-first flow")
    void onlineLabels() {
        assertEquals(PaymentMode.ONLINE, PaymentMode.fromLabel("UPI"));
        assertEquals(PaymentMode.ONLINE, PaymentMode.fromLabel("card"));
        assertEquals(PaymentMode.ONLINE, PaymentMode.fromLabel("ONLINE"));
    }

    @Test
    @DisplayName("a missing label stays on the online flow, so older clients keep working")
    void missingLabelIsOnline() {
        assertEquals(PaymentMode.ONLINE, PaymentMode.fromLabel(null));
        assertEquals(PaymentMode.ONLINE, PaymentMode.fromLabel(""));
        assertEquals(PaymentMode.ONLINE, PaymentMode.fromLabel("   "));
    }

    @Test
    @DisplayName("an unsupported label is rejected instead of silently going online")
    void unsupportedLabelIsRejected() {
        assertThrows(BusinessException.class, () -> PaymentMode.fromLabel("WALLET"));
        assertThrows(BusinessException.class, () -> PaymentMode.fromLabel("BITCOIN"));
    }

    @Test
    @DisplayName("the stored display label is the upper-cased label, or the mode name when none was sent")
    void displayLabel() {
        assertEquals("COD", PaymentMode.displayLabel("cod"));
        assertEquals("COD", PaymentMode.displayLabel("CASH_ON_DELIVERY"));
        assertEquals("UPI", PaymentMode.displayLabel("upi"));
        assertEquals("CARD", PaymentMode.displayLabel("Card"));
        assertEquals("ONLINE", PaymentMode.displayLabel(null));
    }

    @Test
    @DisplayName("an online payment can never produce a COD-labelled order")
    void onlineLabelNeverCod() {
        assertEquals("UPI", PaymentMode.onlineLabel("UPI"));
        assertEquals("CARD", PaymentMode.onlineLabel("card"));
        assertEquals("ONLINE", PaymentMode.onlineLabel("COD"));
        assertEquals("ONLINE", PaymentMode.onlineLabel("WALLET"));
        assertEquals("ONLINE", PaymentMode.onlineLabel(null));
    }
}
