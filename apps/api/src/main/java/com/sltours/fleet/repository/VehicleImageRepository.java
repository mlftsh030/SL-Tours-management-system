package com.sltours.fleet.repository;
import com.sltours.fleet.model.VehicleImage;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VehicleImageRepository extends JpaRepository<VehicleImage, UUID> {
    List<VehicleImage> findByVehicleIdOrderBySortOrderAscCreatedAtAsc(UUID vehicleId);
    List<VehicleImage> findByVehicleIdInOrderBySortOrderAscCreatedAtAsc(Collection<UUID> vehicleIds);
}
