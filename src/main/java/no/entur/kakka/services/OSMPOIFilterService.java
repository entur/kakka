package no.entur.kakka.services;

import no.entur.kakka.domain.OSMPOIFilter;

import java.util.List;

public interface OSMPOIFilterService {
    public List<OSMPOIFilter> getFilters();
    public void updateFilters(List<OSMPOIFilter> filters);
}
