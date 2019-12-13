package no.entur.kakka.services;

import no.entur.kakka.domain.OSMPOIFilter;

import java.util.List;
import java.util.Optional;

public interface OSMPOIFilterService {
    public Optional<OSMPOIFilter> getByKeyAndValue(String key, String value);
    public List<OSMPOIFilter> getFilters();
    public void updateFilters(List<OSMPOIFilter> filters);
}
