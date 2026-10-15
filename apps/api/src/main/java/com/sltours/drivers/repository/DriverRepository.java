package com.sltours.drivers.repository;
import com.sltours.drivers.model.Driver;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByAccountId(UUID accountId);
    List<Driver> findAllByOrderByNameAsc();
}
