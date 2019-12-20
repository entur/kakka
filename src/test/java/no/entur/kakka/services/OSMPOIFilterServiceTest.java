package no.entur.kakka.services;

import no.entur.kakka.domain.OSMPOIFilter;
import no.entur.kakka.repository.OSMPOIFilterRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

public class OSMPOIFilterServiceTest {

    OSMPOIFilterRepositoryStub repository;
    OSMPOIFilterService service;

    @Before
    public void init() {
        repository = new OSMPOIFilterRepositoryStub();
        service = new OSMPOIFilterServiceImpl(repository, 1);
    }

    @Test
    public void testGet() {
        Assert.assertEquals(0, service.getFilters().size());
        repository.setFilters(getTestFilters(5));
        Assert.assertEquals(5, service.getFilters().size());
    }

    @Test
    public void testUpdateDeleteAll() {
        List<OSMPOIFilter> toBeDeleted = getTestFilters(5);
        repository.setFilters(toBeDeleted);
        Assert.assertEquals(5, service.getFilters().size());
        service.updateFilters(List.of());
        Assert.assertEquals( 0, service.getFilters().size());
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
        Assert.assertEquals(toBeUpdated, service.getFilters());
    }

    @Test
    public void testDefaultPriority() {
        OSMPOIFilter testFilter = new OSMPOIFilter();
        service.updateFilters(List.of(testFilter));
        Assert.assertEquals(Integer.valueOf(1), repository.findAll().get(0).getPriority());
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
