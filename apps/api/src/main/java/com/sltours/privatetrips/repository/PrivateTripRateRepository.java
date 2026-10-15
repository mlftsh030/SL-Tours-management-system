package com.sltours.privatetrips.repository;
import com.sltours.fleet.model.VehicleCategory;
import com.sltours.privatetrips.model.PrivateTripRate;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PrivateTripRateRepository extends JpaRepository<PrivateTripRate, VehicleCategory> {}
