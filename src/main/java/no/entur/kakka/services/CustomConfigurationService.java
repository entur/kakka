package no.entur.kakka.services;

import no.entur.kakka.domain.CustomConfiguration;

public interface CustomConfigurationService {
    CustomConfiguration getCustomConfigurationByKey(String key);

    CustomConfiguration updateCustomConfiguration(String key, String value);

    CustomConfiguration saveCustomConfiguration(CustomConfiguration customConfiguration);

    void deleteCustomConfiguration(CustomConfiguration customConfiguration);
}
