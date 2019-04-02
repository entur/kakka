package no.entur.kakka.services;

import no.entur.kakka.domain.CustomConfiguration;

import java.util.List;

public interface CustomConfigurationService {
    List<CustomConfiguration> findAllCustomConfigurations();
    CustomConfiguration getCustomConfigurationByKey(String key);

    CustomConfiguration updateCustomConfiguration(CustomConfiguration updatedConfiguration);

    CustomConfiguration saveCustomConfiguration(CustomConfiguration customConfiguration);

    void deleteCustomConfiguration(CustomConfiguration customConfiguration);
}
