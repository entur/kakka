package no.entur.kakka.services;

import no.entur.kakka.domain.OSMPOIFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OSMPOIFilterServiceTest {

    OSMPOIFilterRepositoryStub repository;
    OSMPOIFilterService service;

    @BeforeEach
    public void init() {
        repository = new OSMPOIFilterRepositoryStub();
        service = new OSMPOIFilterServiceImpl(repository, 1);
    }

    @Test
    public void testGet() {
        Assertions.assertEquals(0, service.getFilters().size());
        repository.setFilters(getTestFilters(5));
        Assertions.assertEquals(5, service.getFilters().size());
    }

    @Test
    public void testUpdateDeleteAll() {
        List<OSMPOIFilter> toBeDeleted = getTestFilters(5);
        repository.setFilters(toBeDeleted);
        Assertions.assertEquals(5, service.getFilters().size());
        service.updateFilters(List.of());
        Assertions.assertEquals( 0, service.getFilters().size());
    }

    @Test
    public void testUpdate() {
        List<OSMPOIFilter> all = getTestFilters(5);
        List<OSMPOIFilter> toBeUpdated = all.subList(2, 5)
                .stream()
                .map((old) -> {
                    OSMPOIFilter updated = new OSMPOIFilter();
                    updated.setId(old.getId());

                    if (old.getId() == 1) {
                        updated.setKey("foo");
                        updated.setValue("bar");
                        updated.setPriority(1);
                    } else {
                        updated.setKey(old.getKey());
                        updated.setValue(old.getValue());
                        updated.setPriority(old.getPriority());
                    }

                    return updated;
                })
                .collect(Collectors.toList());

        repository.setFilters(all);
        service.updateFilters(toBeUpdated);

        List<OSMPOIFilter> updatedFilters = service.getFilters();

        for (int i = 0; i < updatedFilters.size(); i++) {
            Assertions.assertEquals(toBeUpdated.get(i).getId(), updatedFilters.get(i).getId());
        }
    }

    @Test
    public void testDefaultPriority() {
        OSMPOIFilter testFilter = new OSMPOIFilter();
        service.updateFilters(List.of(testFilter));
        Assertions.assertEquals(Integer.valueOf(1), repository.findAll().get(0).getPriority());
    }

    private List<OSMPOIFilter> getTestFilters(int count) {
        List<OSMPOIFilter> filters = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            filters.add(getTestFilter(i));
        }
        return filters;
    }

    private OSMPOIFilter getTestFilter(int index) {
        OSMPOIFilter testFilter = new OSMPOIFilter();
        testFilter.setId((long) index);
        return testFilter;
    }
}
