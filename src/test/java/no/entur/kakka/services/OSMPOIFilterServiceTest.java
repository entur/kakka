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

    OSMPOIFilterRepository mockedRepository;
    OSMPOIFilterService service;

    @Before
    public void init() {
        mockedRepository = Mockito.mock(OSMPOIFilterRepository.class);
        service = new OSMPOIFilterServiceImpl(mockedRepository);
    }

    @Test
    public void testGet() {
        Assert.assertEquals(service.getFilters().size(), 0);
        when(mockedRepository.findAll()).thenReturn(getTestFilters(5));
        Assert.assertEquals(service.getFilters().size(), 5);
    }

    @Test
    public void testUpdateDeleteAll() {
        List<OSMPOIFilter> toBeDeleted = getTestFilters(5);
        when(mockedRepository.findAll()).thenReturn(toBeDeleted);
        Assert.assertEquals(service.getFilters().size(), 5);
        service.updateFilters(List.of());
        verify(mockedRepository, times(1)).deleteAll(argThat(new ObjectEqualityArgumentMatcher<>(toBeDeleted)));
    }

    @Test
    public void testUpdate() {
        List<OSMPOIFilter> all = getTestFilters(5);
        List<OSMPOIFilter> toBeDeleted = all.subList(0, 2);
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

        when(mockedRepository.findAll()).thenReturn(all);
        service.updateFilters(toBeUpdated);
        verify(mockedRepository, times(1)).deleteAll(argThat(new ObjectEqualityArgumentMatcher<>(toBeDeleted)));
        verify(mockedRepository, times(1)).saveAll(argThat(new ObjectEqualityArgumentMatcher<>(toBeUpdated)));
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

    private class ObjectEqualityArgumentMatcher<T> implements ArgumentMatcher<T> {
        T thisObject;

        public ObjectEqualityArgumentMatcher(T thisObject) {
            this.thisObject = thisObject;
        }

        @Override
        public boolean matches(Object argument) {
            return thisObject.equals(argument);
        }
    }
}
