package no.entur.kakka.config;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.List;

public class ExportParams implements Serializable {

    private String name;
    private ExportMode tariffZoneExportMode = ExportMode.RELEVANT;
    private ExportMode fareZoneExportMode = ExportMode.RELEVANT;
    private ExportMode groupOfStopPlacesExportMode = ExportMode.RELEVANT;
    private ExportMode groupOfTariffZonesExportMode = ExportMode.RELEVANT;
    private ExportMode serviceFrameExportMode = ExportMode.NONE;
    private ExportMode topographicPlaceExportMode = ExportMode.RELEVANT;
    private List<String> municipalityReferences;
    private List<String> countyReferences;
    private List<String> countryReferences;
    private StopPlaceSearch stopPlaceSearch;


    public ExportParams() {
    }

    public ExportParams(String name,
                        ExportMode tariffZoneExportMode,
                        ExportMode fareZoneExportMode,
                        ExportMode groupOfStopPlacesExportMode,
                        ExportMode groupOfTariffZonesExportMode,
                        ExportMode serviceFrameExportMode,
                        ExportMode topographicPlaceExportMode,
                        List<String> municipalityReferences,
                        List<String> countyReferences,
                        List<String> countryReferences,
                        StopPlaceSearch stopPlaceSearch) {
        this.name = name;
        this.tariffZoneExportMode = tariffZoneExportMode;
        this.fareZoneExportMode = fareZoneExportMode;
        this.groupOfStopPlacesExportMode = groupOfStopPlacesExportMode;
        this.groupOfTariffZonesExportMode = groupOfTariffZonesExportMode;
        this.serviceFrameExportMode = serviceFrameExportMode;
        this.topographicPlaceExportMode = topographicPlaceExportMode;
        this.municipalityReferences = municipalityReferences;
        this.countyReferences = countyReferences;
        this.countryReferences = countryReferences;
        this.stopPlaceSearch = stopPlaceSearch;
    }

    public static ExportParams fromString(String exportJob) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(exportJob, ExportParams.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ExportMode getTariffZoneExportMode() {
        return tariffZoneExportMode;
    }

    public void setTariffZoneExportMode(ExportMode tariffZoneExportMode) {
        this.tariffZoneExportMode = tariffZoneExportMode;
    }

    public ExportMode getFareZoneExportMode() {
        return fareZoneExportMode;
    }

    public void setFareZoneExportMode(ExportMode fareZoneExportMode) {
        this.fareZoneExportMode = fareZoneExportMode;
    }

    public ExportMode getGroupOfStopPlacesExportMode() {
        return groupOfStopPlacesExportMode;
    }

    public void setGroupOfStopPlacesExportMode(ExportMode groupOfStopPlacesExportMode) {
        this.groupOfStopPlacesExportMode = groupOfStopPlacesExportMode;
    }

    public ExportMode getGroupOfTariffZonesExportMode() {
        return groupOfTariffZonesExportMode;
    }

    public void setGroupOfTariffZonesExportMode(ExportMode groupOfTariffZonesExportMode) {
        this.groupOfTariffZonesExportMode = groupOfTariffZonesExportMode;
    }

    public ExportMode getServiceFrameExportMode() {
        return serviceFrameExportMode;
    }

    public void setServiceFrameExportMode(ExportMode serviceFrameExportMode) {
        this.serviceFrameExportMode = serviceFrameExportMode;
    }

    public ExportMode getTopographicPlaceExportMode() {
        return topographicPlaceExportMode;
    }

    public void setTopographicPlaceExportMode(ExportMode topographicPlaceExportMode) {
        this.topographicPlaceExportMode = topographicPlaceExportMode;
    }

    public List<String> getMunicipalityReferences() {
        return municipalityReferences;
    }

    public void setMunicipalityReferences(List<String> municipalityReferences) {
        this.municipalityReferences = municipalityReferences;
    }

    public List<String> getCountyReferences() {
        return countyReferences;
    }

    public void setCountyReferences(List<String> countyReferences) {
        this.countyReferences = countyReferences;
    }

    public List<String> getCountryReferences() {
        return countryReferences;
    }

    public void setCountryReferences(List<String> countryReferences) {
        this.countryReferences = countryReferences;
    }

    public StopPlaceSearch getStopPlaceSearch() {
        return stopPlaceSearch;
    }

    public void setStopPlaceSearch(StopPlaceSearch stopPlaceSearch) {
        this.stopPlaceSearch = stopPlaceSearch;
    }

    public String toString() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, this);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
