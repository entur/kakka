package no.entur.kakka.repository;

import no.entur.kakka.domain.OSMPOIFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OSMPOIFilterRepository extends JpaRepository<OSMPOIFilter, Long> {}
