package com.sltours.shared.util;
import java.security.SecureRandom;
import java.time.Year;
import java.util.function.Predicate;

/** Human-friendly references such as SLE-2026-7KQ2M, without ambiguous characters. */
public final class ReferenceCodes {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private ReferenceCodes() {}
    public static String next(String prefix, int length, Predicate<String> exists) {
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder suffix = new StringBuilder();
            for (int i = 0; i < length; i++) suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            String reference = prefix + "-" + Year.now() + "-" + suffix;
            if (!exists.test(reference)) return reference;
        }
        throw new IllegalStateException("Could not allocate a " + prefix + " reference");
    }
}
