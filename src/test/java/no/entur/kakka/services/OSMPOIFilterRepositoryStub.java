package no.entur.kakka.services;

import no.entur.kakka.domain.OSMPOIFilter;
import no.entur.kakka.repository.OSMPOIFilterRepository;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNullApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class OSMPOIFilterRepositoryStub implements OSMPOIFilterRepository {

    private List<OSMPOIFilter> filters = new ArrayList<>();

    public void setFilters(List<OSMPOIFilter> filters) {
        this.filters = filters;
    }

    @Override
    public List<OSMPOIFilter> findAll() {
        return filters;
    }

    @Override
    public List<OSMPOIFilter> findAll(Sort sort) {
        return null;
    }

    @Override
    public Page<OSMPOIFilter> findAll(Pageable pageable) {
        return null;
    }

    @Override
    public List<OSMPOIFilter> findAllById(Iterable<Long> longs) {
        return null;
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public void deleteById(Long aLong) {

    }

    @Override
    public void delete(OSMPOIFilter osmpoiFilter) {

    }

    @Override
    public void deleteAll(Iterable<? extends OSMPOIFilter> iterable) {
        List<OSMPOIFilter> list = StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
        filters = filters
                .stream()
                .filter(f -> !containsFilter(f, list))
                .collect(Collectors.toList());
    }

    private boolean containsFilter(OSMPOIFilter filter, List<OSMPOIFilter> filters) {
        return filters
                .stream()
                .anyMatch(f -> f.getId().equals(filter.getId()));
    }

    @Override
    public void deleteAll() {

    }

    @Override
    public <S extends OSMPOIFilter> S save(S s) {
        return null;
    }

    @Override
    public <S extends OSMPOIFilter> List<S> saveAll(Iterable<S> entities) {

        StreamSupport.stream(entities.spliterator(), false)
                .forEach(f -> {
                    if (containsFilter(f, filters)) {
                        OSMPOIFilter filter = filters.stream().filter(f2 -> f.getId().equals(f2.getId())).findFirst().get();
                        filter.setKey(f.getKey());
                        filter.setValue(f.getValue());
                        filter.setPriority(f.getPriority());
                    } else {
                        filters.add(f);
                    }
                });
        return null;
    }

    @Override
    public Optional<OSMPOIFilter> findById(Long aLong) {
        return Optional.empty();
    }

    @Override
    public boolean existsById(Long aLong) {
        return false;
    }

    @Override
    public void flush() {

    }

    @Override
    public <S extends OSMPOIFilter> S saveAndFlush(S entity) {
        return null;
    }

    @Override
    public void deleteInBatch(Iterable<OSMPOIFilter> entities) {

    }

    @Override
    public void deleteAllInBatch() {

    }

    @Override
    public OSMPOIFilter getOne(Long aLong) {
        return null;
    }

    @Override
    public <S extends OSMPOIFilter> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends OSMPOIFilter> List<S> findAll(Example<S> example) {
        return null;
    }

    @Override
    public <S extends OSMPOIFilter> List<S> findAll(Example<S> example, Sort sort) {
        return null;
    }

    @Override
    public <S extends OSMPOIFilter> Page<S> findAll(Example<S> example, Pageable pageable) {
        return null;
    }

    @Override
    public <S extends OSMPOIFilter> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends OSMPOIFilter> boolean exists(Example<S> example) {
        return false;
    }
}
