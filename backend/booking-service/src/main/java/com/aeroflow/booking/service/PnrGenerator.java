package com.aeroflow.booking.service;

import java.security.SecureRandom;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/** 6-character record locator without ambiguous characters (0/O, 1/I). */
@Component
public class PnrGenerator {
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    public String next(Predicate<String> exists) {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            String pnr = sb.toString();
            if (!exists.test(pnr)) return pnr;
        }
        throw new IllegalStateException("Could not allocate a unique PNR");
    }
}
