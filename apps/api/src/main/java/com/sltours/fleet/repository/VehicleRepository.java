package com.sltours.fleet.repository;
import com.sltours.fleet.model.Vehicle;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    List<Vehicle> findAllByOrderByDisplayNameAsc();
    boolean existsByRegistrationIgnoreCase(String registration);
    boolean existsByRegistrationIgnoreCaseAndIdNot(String registration, UUID id);
}
