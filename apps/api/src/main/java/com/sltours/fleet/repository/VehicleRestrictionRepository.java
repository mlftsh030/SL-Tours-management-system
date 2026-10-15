package com.sltours.fleet.repository;
import com.sltours.fleet.model.VehicleRestriction;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface VehicleRestrictionRepository extends JpaRepository<VehicleRestriction, UUID> {
    List<VehicleRestriction> findByVehicleIdOrderByOpenedAtDesc(UUID vehicleId);
    List<VehicleRestriction> findByVehicleIdAndClearedAtIsNull(UUID vehicleId);
    boolean existsByVehicleIdAndClearedAtIsNull(UUID vehicleId);
    @Query("select r.vehicleId from VehicleRestriction r where r.clearedAt is null")
    Set<UUID> findRestrictedVehicleIds();
    long countByClearedAtIsNull();
}
