package no.entur.kakka.geocoder.routes.pelias.mapper.gtfs;

import com.conveyal.gtfs.model.Stop;
import no.entur.kakka.geocoder.routes.pelias.json.GeoPoint;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GtfsStopPlaceToPeliasMapper {

    // Using substitute layer for stops to avoid having to fork pelias (custom layers not configurable).
    public static final String STOP_PLACE_LAYER = "venue";

    public final long popularity;

    public GtfsStopPlaceToPeliasMapper(@Value("${pelias.gtfs.stopplace.boost:4}") long popularity) {
        this.popularity = popularity;
    }

    public PeliasDocument toPeliasDocument(Stop stop) {
        if (stop.parent_station != null) {
            return null; // discard quay when parent stop exists
        }
        PeliasDocument document = new PeliasDocument(STOP_PLACE_LAYER, stop.stop_id);
        document.setDefaultNameAndPhrase(stop.stop_name);
        document.setCenterPoint(toCenterPoint(stop));
        document.setPopularity(popularity);
        return document;
    }

    private GeoPoint toCenterPoint(Stop stop) {
        if (stop.stop_lat <= 0 || stop.stop_lon <= 0) {
            return null;
        }

        return new GeoPoint(stop.stop_lat, stop.stop_lon);
    }
}
