package com.sltours.billing.model;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPaymentRepository extends JpaRepository<BillingPayment, UUID> {
    List<BillingPayment> findByDocumentIdOrderByRecordedAtAsc(UUID documentId);
    Optional<BillingPayment> findByIdempotencyKey(UUID key);
}
