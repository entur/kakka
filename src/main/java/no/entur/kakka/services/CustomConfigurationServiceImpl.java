package no.entur.kakka.services;

import no.entur.kakka.domain.CustomConfiguration;
import no.entur.kakka.repository.CustomConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service("customConfigurationService")
public class CustomConfigurationServiceImpl implements CustomConfigurationService {

    @Autowired
    private CustomConfigurationRepository customConfigurationRepository;


    @Override
    public List<CustomConfiguration> findAllCustomConfigurations() {

        final Iterable<CustomConfiguration> all = customConfigurationRepository.findAll();
        List<CustomConfiguration> customConfigurations= new ArrayList<>();
        for (CustomConfiguration customConfiguration : all) {

            customConfigurations.add(customConfiguration);
        }

        return customConfigurations;
    }

    @Override
    public CustomConfiguration getCustomConfigurationByKey(String key) {
        return customConfigurationRepository.findByKey(key).orElse(null);
    }

    @Override
    public CustomConfiguration updateCustomConfiguration(CustomConfiguration updatedConfiguration) {
        final CustomConfiguration existingConfiguration = customConfigurationRepository.findByKey(updatedConfiguration.getKey()).
                orElseThrow(() -> new NoSuchElementException("No configuration found for : " + updatedConfiguration.getKey()));
        updatedConfiguration.setId(existingConfiguration.getId());
        customConfigurationRepository.save(updatedConfiguration);
        return existingConfiguration;
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
