package no.entur.kakka.geocoder.routes.pelias.mapper.netex;

import no.entur.kakka.geocoder.routes.pelias.json.AddressParts;
import no.entur.kakka.geocoder.routes.pelias.json.GeoPoint;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.netex.model.GroupOfStopPlaces;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Map NeTEx GroupOfStopPlaces objects to Pelias documents.
 */
public class GroupOfStopPlacesToPeliasMapper {
    // Using substitute layer for GoS to avoid having to fork pelias (custom layers not configurable).
    public static final String ADDRESS_LAYER = "address";

    private static final String DEFAULT_LANGUAGE = "nor";

    /**
     * Map single GroupOfStopPlaces to (potentially) multiple pelias documents, one per alias/alternative name.
     * <p>
     * Pelias does not yet support queries in multiple languages / for aliases. When support for this is ready this mapping should be
     * refactored to produce a single document per GoS.
     */
    public List<PeliasDocument> toPeliasDocuments(GroupOfStopPlaces groupOfStopPlaces, long popularity) {

        if (!NetexPeliasMapperUtil.isValid(groupOfStopPlaces)) {
            return new ArrayList<>();
        }
        AtomicInteger cnt = new AtomicInteger();

        return getNames(groupOfStopPlaces).stream().map(name -> toPeliasDocument(groupOfStopPlaces, name, popularity, cnt.getAndAdd(1))).collect(Collectors.toList());
    }

    private PeliasDocument toPeliasDocument(GroupOfStopPlaces groupOfStopPlaces, MultilingualString name, long popularity, int idx) {
        String idSuffix = idx > 0 ? "-" + idx : "";

        PeliasDocument document = new PeliasDocument(ADDRESS_LAYER, groupOfStopPlaces.getId() + idSuffix);
        if (name != null) {
            document.setDefaultNameAndPhrase(name.getValue());
        }

        // Add official name as display name. Not a part of standard pelias model, will be copied to name.default before deduping and labelling in Entur-pelias API.
        MultilingualString displayName = groupOfStopPlaces.getName();
        if (displayName != null) {
            document.getNameMap().put("display", displayName.getValue());
            if (displayName.getLang() != null) {
                document.addName(displayName.getLang(), displayName.getValue());
            }
        }

        if (groupOfStopPlaces.getCentroid() != null) {
            LocationStructure loc = groupOfStopPlaces.getCentroid().getLocation();
            document.setCenterPoint(new GeoPoint(loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue()));
        }

        if (groupOfStopPlaces.getPolygon() != null) {
            // TODO issues with shape validation in elasticsearch. duplicate coords + intersections cause document to be discarded. is shape even used by pelias?
            document.setShape(NetexPeliasMapperUtil.toPolygon(groupOfStopPlaces.getPolygon().getExterior().getAbstractRing().getValue()));
        }

        addIdToStreetNameToAvoidFalseDuplicates(groupOfStopPlaces, document);

        if (groupOfStopPlaces.getDescription() != null && !StringUtils.isEmpty(groupOfStopPlaces.getDescription().getValue())) {
            String lang = groupOfStopPlaces.getDescription().getLang();
            if (lang == null) {
                lang = DEFAULT_LANGUAGE;
            }
            document.addDescription(lang, groupOfStopPlaces.getDescription().getValue());
        }

        document.setPopularity(popularity);
        document.setCategory(List.of(GroupOfStopPlaces.class.getSimpleName()));

        return document;
    }


    /**
     * The Pelias APIs deduper will throw away results with identical name, layer, parent and address. Setting unique ID in street part of address to avoid unique
     * topographic places with identical names being deduped.
     */
    private void addIdToStreetNameToAvoidFalseDuplicates(GroupOfStopPlaces groupOfStopPlaces, PeliasDocument document) {
        if (document.getAddressParts() == null) {
            document.setAddressParts(new AddressParts());
        }
        document.getAddressParts().setStreet("NOT_AN_ADDRESS-" + groupOfStopPlaces.getId());
    }

    private List<MultilingualString> getNames(GroupOfStopPlaces groupOfStopPlaces) {
        List<MultilingualString> names = new ArrayList<>();
        if (groupOfStopPlaces.getName() != null) {
            names.add(groupOfStopPlaces.getName());
        }

        if (groupOfStopPlaces.getAlternativeNames() != null && !CollectionUtils.isEmpty(groupOfStopPlaces.getAlternativeNames().getAlternativeName())) {
            groupOfStopPlaces.getAlternativeNames().getAlternativeName().stream().filter(an -> an.getName() != null && an.getName().getLang() != null).forEach(n -> names.add(n.getName()));
        }

        return NetexPeliasMapperUtil.filterUnique(names);
    }
}
