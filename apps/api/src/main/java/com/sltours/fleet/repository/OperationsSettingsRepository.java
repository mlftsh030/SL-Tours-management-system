package com.sltours.fleet.repository;
import com.sltours.fleet.model.OperationsSettings;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OperationsSettingsRepository extends JpaRepository<OperationsSettings, Short> {
    default OperationsSettings current() { return findById(OperationsSettings.ID).orElseThrow(); }
}
