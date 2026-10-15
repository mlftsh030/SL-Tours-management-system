package com.sltours.privatetrips.repository;
import com.sltours.privatetrips.model.PrivateTripFee;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PrivateTripFeeRepository extends JpaRepository<PrivateTripFee, UUID> {
    List<PrivateTripFee> findByActiveTrueOrderBySortOrderAscNameAsc();
    List<PrivateTripFee> findAllByOrderBySortOrderAscNameAsc();
}
