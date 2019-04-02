package no.entur.kakka.services;

import no.entur.kakka.domain.CustomConfiguration;
import no.entur.kakka.repository.CustomConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service("customConfigurationService")
@Transactional
public class CustomConfigurationServiceImpl implements CustomConfigurationService {

    @Autowired
    private CustomConfigurationRepository customConfigurationRepository;


    @Override
    public List<CustomConfiguration> findAllCustomConfigurations() {
        return customConfigurationRepository.findAll();
    }

    @Override
    public CustomConfiguration getCustomConfigurationByKey(String key) {
        return customConfigurationRepository.findByKey(key).orElse(null);
    }

    @Override
    public CustomConfiguration updateCustomConfiguration(CustomConfiguration updatedConfiguration) {
        final CustomConfiguration customConfiguration = customConfigurationRepository.findByKey(updatedConfiguration.getKey()).
                orElseThrow(() -> new NoSuchElementException("No configuration found for : " + updatedConfiguration.getKey()));

        customConfiguration.setValue(customConfiguration.getValue());

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
