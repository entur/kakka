package no.entur.kakka.repository;

import no.entur.kakka.domain.OSMPOIFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OSMPOIFilterRepository extends JpaRepository<OSMPOIFilter, Long> {

    @Query("select f from OSMPOIFilter f WHERE f.key LIKE :key AND f.value LIKE :value")
    List<OSMPOIFilter> getByKeyAndValue(@Param("key") String key, @Param("value") String value);
}
