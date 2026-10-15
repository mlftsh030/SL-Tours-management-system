package com.sltours.enquiries.service;
import com.sltours.enquiries.model.EnquiryStatus;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sltours.enquiries.model.EnquiryStatus.*;

/** Handling transitions. CONVERTED is reached only through conversion and is final. */
@Component
public class EnquiryStatusPolicy {
    private static final Map<EnquiryStatus, List<EnquiryStatus>> ALLOWED = Map.of(
        NEW, List.of(IN_PROGRESS, AWAITING_CUSTOMER, CLOSED),
        IN_PROGRESS, List.of(AWAITING_CUSTOMER, CLOSED),
        AWAITING_CUSTOMER, List.of(IN_PROGRESS, CLOSED),
        CLOSED, List.of(IN_PROGRESS),
        CONVERTED, List.of());
    public List<EnquiryStatus> next(EnquiryStatus from) { return ALLOWED.get(from); }
    public void validate(EnquiryStatus from, EnquiryStatus to) {
        if (from == to) return;
        if (to == CONVERTED) throw new IllegalArgumentException("Use “Convert to booking” to convert an enquiry");
        if (!ALLOWED.get(from).contains(to))
            throw new IllegalArgumentException("An enquiry cannot move from " + from + " to " + to);
    }
    public boolean canConvert(EnquiryStatus status) { return status != CONVERTED && status != CLOSED; }
}
