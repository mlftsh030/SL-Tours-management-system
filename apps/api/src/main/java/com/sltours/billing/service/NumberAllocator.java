package com.sltours.billing.service;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out official invoice, quotation and receipt numbers inside the issuing transaction. The sequence row is
 * locked, so concurrent requests queue and each gets a different number; a number is consumed only if the
 * transaction commits, and the allocator never goes below the highest number already stored. Unique indexes are the
 * final guard.
 */
@Component
public class NumberAllocator {
    public enum Series { INVOICE, QUOTATION, RECEIPT }
    private final JdbcTemplate jdbc;
    public NumberAllocator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public long next(Series series) {
        var row = jdbc.query("select next_number, confirmed from document_sequences where kind = ? for update",
            (rs, i) -> new Object[]{rs.getLong(1), rs.getBoolean(2)}, series.name()).stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing number sequence " + series));
        if (!(boolean) row[1]) throw new IllegalArgumentException("Confirm the next " + series.name().toLowerCase(Locale.ROOT)
            + " number in Billing settings before issuing. Check the last number already sent outside this system first.");
        long next = Math.max((long) row[0], highestUsed(series) + 1);
        jdbc.update("update document_sequences set next_number = ?, updated_at = ?, updated_by = ? where kind = ?",
            next + 1, OffsetDateTime.now(), BillingSupport.actor(), series.name());
        return next;
    }

    public long highestUsed(Series series) {
        Long max = series == Series.RECEIPT
            ? jdbc.queryForObject("select max(receipt_number) from billing_payments", Long.class)
            : jdbc.queryForObject("select max(number) from billing_documents where kind = ?", Long.class, series.name());
        return max == null ? 0 : max;
    }
}
