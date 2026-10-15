package com.sltours.privatetrips.repository;
import com.sltours.privatetrips.model.PrivateTripDetails;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PrivateTripDetailsRepository extends JpaRepository<PrivateTripDetails, UUID> {}
