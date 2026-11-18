package com.aeroflow.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PnrAndPaymentTest {
    @Test
    void pnrUsesUnambiguousAlphabetAndRetriesOnCollision() {
        PnrGenerator gen = new PnrGenerator();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String pnr = gen.next(seen::contains);
            assertThat(pnr).hasSize(6).matches("[" + PnrGenerator.ALPHABET + "]{6}").doesNotContain("0", "O", "1", "I");
            assertThat(seen.add(pnr)).isTrue();
        }
    }

    @Test
    void pnrGivesUpWhenEverythingCollides() {
        assertThatThrownBy(() -> new PnrGenerator().next(p -> true)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({"4242424242424242,true", "4000000000000002,true", "4242424242424241,false", "12ab,false"})
    void luhn(String number, boolean valid) {
        assertThat(MockPaymentGateway.luhn(number)).isEqualTo(valid);
    }

    @Test
    void approvesTestCardAndNeverExposesFullPan() {
        var r = new MockPaymentGateway().charge(BigDecimal.TEN, "AED",
                new PaymentGateway.CardDetails("4242 4242 4242 4242", "12/39", "123", "S A"), "k");
        assertThat(r.approved()).isTrue();
        assertThat(r.last4()).isEqualTo("4242");
        assertThat(r.brand()).isEqualTo("VISA");
        assertThat(r.reference()).startsWith("MOCK-CH-").doesNotContain("4242424242424242");
    }

    @Test
    void declinesDeclineCardExpiredAndInvalid() {
        MockPaymentGateway psp = new MockPaymentGateway();
        assertThat(psp.charge(BigDecimal.ONE, "AED", new PaymentGateway.CardDetails("4000000000000002", "12/39", "1", "x"), "k")
                .declineReason()).contains("declined");
        assertThat(psp.charge(BigDecimal.ONE, "AED", new PaymentGateway.CardDetails("4242424242424242", "01/20", "1", "x"), "k")
                .declineReason()).isEqualTo("Card expired");
        assertThat(psp.charge(BigDecimal.ONE, "AED", new PaymentGateway.CardDetails("4242424242424241", "12/39", "1", "x"), "k")
                .approved()).isFalse();
        assertThat(MockPaymentGateway.brand("5500000000000004")).isEqualTo("MASTERCARD");
        assertThat(MockPaymentGateway.brand("378282246310005")).isEqualTo("AMEX");
    }
}
