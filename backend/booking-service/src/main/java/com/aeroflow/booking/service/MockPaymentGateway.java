package com.aeroflow.booking.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deterministic mock PSP. 4242 4242 4242 4242 approves; any card ending in 0002 is declined;
 * card numbers must pass the Luhn check and not be expired. Full PAN/CVV are never stored or logged.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public ChargeResult charge(BigDecimal amount, String currency, CardDetails card, String idempotencyKey) {
        String digits = card.number().replaceAll("\\s|-", "");
        String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        String brand = brand(digits);
        if (!luhn(digits)) return new ChargeResult(false, null, last4, brand, "Invalid card number");
        if (expired(card.expiry())) return new ChargeResult(false, null, last4, brand, "Card expired");
        if (digits.endsWith("0002")) return new ChargeResult(false, null, last4, brand, "Card declined by issuer");
        String ref = "MOCK-CH-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase();
        log.info("[mock-psp] charged {} {} to {} ****{} ref={}", amount, currency, brand, last4, ref);
        return new ChargeResult(true, ref, last4, brand, null);
    }

    @Override
    public String refund(String chargeReference, BigDecimal amount, String currency) {
        String ref = "MOCK-RF-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase();
        log.info("[mock-psp] refunded {} {} for {} ref={}", amount, currency, chargeReference, ref);
        return ref;
    }

    static boolean luhn(String digits) {
        if (!digits.matches("\\d{12,19}")) return false;
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    static boolean expired(String mmYY) {
        try {
            YearMonth ym = YearMonth.parse(mmYY.trim(), DateTimeFormatter.ofPattern("MM/yy"));
            return ym.isBefore(YearMonth.now());
        } catch (Exception e) {
            return true;
        }
    }

    static String brand(String digits) {
        if (digits.startsWith("4")) return "VISA";
        if (digits.matches("^5[1-5].*") || digits.matches("^2[2-7].*")) return "MASTERCARD";
        if (digits.matches("^3[47].*")) return "AMEX";
        return "CARD";
    }
}
