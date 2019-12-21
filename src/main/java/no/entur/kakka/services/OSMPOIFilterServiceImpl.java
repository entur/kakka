package no.entur.kakka.services;

import no.entur.kakka.domain.OSMPOIFilter;
import no.entur.kakka.repository.OSMPOIFilterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service("osmpoifilterService")
@Transactional(transactionManager = "jpaTransactionManager")
public class OSMPOIFilterServiceImpl implements OSMPOIFilterService {
    private OSMPOIFilterRepository repository;
    private Integer defaultPriority;

    @Autowired
    public OSMPOIFilterServiceImpl(OSMPOIFilterRepository repository, @Value("${osmpoifilter.priority.default:1}") Integer defaultPriority) {
        this.repository = repository;
        this.defaultPriority = defaultPriority;
    }

    @Override
    public List<OSMPOIFilter> getFilters() {
        return repository.findAll();
    }

    @Override
    public void updateFilters(List<OSMPOIFilter> filters) {
        List<OSMPOIFilter> currentFilters = repository.findAll();
        List<OSMPOIFilter> filtersToDelete = findFiltersToDelete(currentFilters, filters);
        List<OSMPOIFilter> filtersToUpdate = findFiltersToUpdate(currentFilters, filters);
        List<OSMPOIFilter> filtersToAdd = findFiltersToAdd(currentFilters, filters);
        repository.deleteAll(filtersToDelete);
        repository.saveAll(preprocess(filtersToUpdate));
        repository.saveAll(preprocess(filtersToAdd));
    }

    private List<OSMPOIFilter> findFiltersToDelete(List<OSMPOIFilter> currentFilters, List<OSMPOIFilter> filters) {
        return currentFilters
                .stream()
                .filter(f -> !containsFilter(f, filters))
                .collect(Collectors.toList());
    }

    private List<OSMPOIFilter> findFiltersToUpdate(List<OSMPOIFilter> currentFilters, List<OSMPOIFilter> filters) {
        return filters
                .stream()
                .filter(f -> containsFilter(f, currentFilters))
                .collect(Collectors.toList());
    }

    private List<OSMPOIFilter> findFiltersToAdd(List<OSMPOIFilter> currentFilters, List<OSMPOIFilter> filters) {
        return filters
                .stream()
                .filter(f -> !containsFilter(f, currentFilters))
                .collect(Collectors.toList());
    }

    private boolean containsFilter(OSMPOIFilter filter, List<OSMPOIFilter> filters) {
        return filters
                .stream()
                .anyMatch(f -> f.getId().equals(filter.getId()));
    }

    private List<OSMPOIFilter> preprocess(List<OSMPOIFilter> filters) {
        return filters.stream().peek(this::addDefaultPriority).collect(Collectors.toList());
    }

    private void addDefaultPriority(OSMPOIFilter filter) {
        if (filter.getPriority() == null) {
            filter.setPriority(defaultPriority);
        }
    }

}
