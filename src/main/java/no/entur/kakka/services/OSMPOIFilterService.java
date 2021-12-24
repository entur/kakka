package no.entur.kakka.services;

import no.entur.kakka.domain.OSMPOIFilter;

import java.util.List;

public interface OSMPOIFilterService {
    List<OSMPOIFilter> getFilters();
    void updateFilters(List<OSMPOIFilter> filters);
}
