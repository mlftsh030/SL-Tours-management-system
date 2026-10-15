package com.sltours.bookings.service;

import com.sltours.bookings.repository.BookingRepository;
import java.security.SecureRandom;
import java.time.Year;
import org.springframework.stereotype.Component;

@Component
public class BookingReferenceGenerator {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom secureRandom = new SecureRandom();

    public String nextReference(BookingRepository bookingRepository) {
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder suffix = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                suffix.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
            }
            String reference = "SLT-" + Year.now() + "-" + suffix;
            if (!bookingRepository.existsByReference(reference)) {
                return reference;
            }
        }
        throw new IllegalStateException("Could not allocate a booking reference");
    }
}
