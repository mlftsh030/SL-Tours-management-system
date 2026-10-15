package com.sltours.billing.model;
import com.sltours.billing.model.BillingTypes.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BillingDocumentRepository extends JpaRepository<BillingDocument, UUID>, JpaSpecificationExecutor<BillingDocument> {
    Optional<BillingDocument> findByIdempotencyKey(UUID key);
    Optional<BillingDocument> findFirstBySourceQuotationIdAndStatusNot(UUID quotationId, Status excluded);
    Optional<BillingDocument> findFirstByRevisionOfIdAndStatusNot(UUID originalId, Status excluded);
    List<BillingDocument> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);
    List<BillingDocument> findByAccountIdAndStatusInOrderByIssueDateDesc(UUID accountId, Collection<Status> statuses);
    /** Serialises issue, payment and void operations on one document. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from BillingDocument d where d.id = :id")
    Optional<BillingDocument> lockById(@Param("id") UUID id);
}
