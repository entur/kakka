package no.entur.kakka.services;

import no.entur.kakka.domain.OSMPOIFilter;
import no.entur.kakka.repository.OSMPOIFilterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service("osmpoifilterService")
@Transactional(transactionManager = "jpaTransactionManager")
public class OSMPOIFilterServiceImpl implements OSMPOIFilterService {

    OSMPOIFilterRepository repository;

    public OSMPOIFilterServiceImpl(@Autowired OSMPOIFilterRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<OSMPOIFilter> getFilters() {
        return repository.findAll();
    }

    @Override
    public void updateFilters(List<OSMPOIFilter> filtersToUpdate) {
        List<OSMPOIFilter> currentFilters = repository.findAll();
        List<OSMPOIFilter> filtersToDelete = currentFilters
                .stream()
                .filter(f1 -> filtersToUpdate.stream().noneMatch(f2 -> f1.getId().equals(f2.getId())))
                .collect(Collectors.toList());
        repository.deleteAll(filtersToDelete);
        repository.saveAll(filtersToUpdate);
    }

}
