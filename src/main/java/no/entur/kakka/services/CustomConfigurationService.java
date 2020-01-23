package no.entur.kakka.services;

import no.entur.kakka.domain.CustomConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Transactional
public interface CustomConfigurationService {
    List<CustomConfiguration> findAllCustomConfigurations();
    CustomConfiguration getCustomConfigurationByKey(String key);

    CustomConfiguration updateCustomConfiguration(CustomConfiguration updatedConfiguration);

    CustomConfiguration saveCustomConfiguration(CustomConfiguration customConfiguration);

    void deleteCustomConfiguration(String key);
}
