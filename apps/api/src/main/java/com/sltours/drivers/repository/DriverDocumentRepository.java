package com.sltours.drivers.repository;
import com.sltours.drivers.model.DriverDocument;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {
    List<DriverDocument> findByDriverIdOrderByCreatedAtDesc(UUID driverId);
    long countByDriverIdAndStatus(UUID driverId, DriverDocument.Status status);
}
