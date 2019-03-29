package no.entur.kakka.services;

import no.entur.kakka.domain.CustomConfiguration;
import no.entur.kakka.repository.CustomConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.NoSuchElementException;

@Service
@Transactional
public class CustomConfigurationServiceImpl implements CustomConfigurationService {

    @Autowired
    private CustomConfigurationRepository customConfigurationRepository;


    @Override
    public CustomConfiguration getCustomConfigurationByKey(String key) {
        return customConfigurationRepository.findByKey(key).orElse(null);
    }

    @Override
    public CustomConfiguration updateCustomConfiguration(String key, String value) {
        final CustomConfiguration customConfiguration = customConfigurationRepository.findByKey(key).
                orElseThrow(() -> new NoSuchElementException("No configuration found for : " + key));

        customConfiguration.setConfig_value(value);

        return customConfigurationRepository.save(customConfiguration);
    }

    @Override
    public CustomConfiguration saveCustomConfiguration(CustomConfiguration customConfiguration) {
        return customConfigurationRepository.saveAndFlush(customConfiguration);
    }

    @Override
    public void deleteCustomConfiguration(CustomConfiguration customConfiguration) {

        customConfigurationRepository.delete(customConfiguration);
    }

}
