package com.aeroflow.booking.service;

import java.math.BigDecimal;

/** Port for payment providers. The demo uses {@link MockPaymentGateway}. */
public interface PaymentGateway {
    record CardDetails(String number, String expiry, String cvv, String holder) {}
    record ChargeResult(boolean approved, String reference, String last4, String brand, String declineReason) {}

    ChargeResult charge(BigDecimal amount, String currency, CardDetails card, String idempotencyKey);
    String refund(String chargeReference, BigDecimal amount, String currency);
}
