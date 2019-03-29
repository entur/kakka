package no.entur.kakka.repository;

import no.entur.kakka.domain.CustomConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomConfigurationRepository extends JpaRepository<CustomConfiguration,Long> {

    @Query("SELECT t FROM CustomConfiguration t where t.config_key = :key")
    Optional<CustomConfiguration> findByKey(@Param("key") String key);
}
