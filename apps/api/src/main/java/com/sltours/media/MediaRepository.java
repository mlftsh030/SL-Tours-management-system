package com.sltours.media;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MediaRepository extends JpaRepository<MediaFile, UUID> {}
