/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.entur.kakka.geocoder.routes.pelias.mapper.netex;


import no.entur.kakka.geocoder.routes.pelias.json.Parent;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.netex.boost.StopPlaceBoostConfiguration;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.rutebanken.netex.model.BusSubmodeEnumeration;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.NameTypeEnumeration;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.StopTypeEnumeration;
import org.rutebanken.netex.model.VehicleModeEnumeration;
import org.rutebanken.netex.model.VersionOfObjectRefStructure;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class StopPlaceToPeliasMapper extends AbstractNetexPlaceToPeliasDocumentMapper<StopPlace> {

    // Using substitute layer for stops to avoid having to fork pelias (custom layers not configurable).
    public static final String STOP_PLACE_LAYER = "venue";
    public static final String SOURCE_PARENT_STOP_PLACE = "openstreetmap";
    public static final String SOURCE_CHILD_STOP_PLACE = "geonames";
    private static final String KEY_IS_PARENT_STOP_PLACE = "IS_PARENT_STOP_PLACE";
    private final StopPlaceBoostConfiguration boostConfiguration;

    public StopPlaceToPeliasMapper(StopPlaceBoostConfiguration boostConfiguration) {
        super();
        this.boostConfiguration = boostConfiguration;
    }

    @Override
    protected boolean isValid(StopPlace place) {
        // Ignore rail replacement bus
        if (VehicleModeEnumeration.BUS.equals(place.getTransportMode()) && BusSubmodeEnumeration.RAIL_REPLACEMENT_BUS.equals(place.getBusSubmode())) {
            return false;
        }

        // Skip stops without quays, unless they are parent stops
        if (isQuayLessNonParentStop(place)) {
            return false;
        }

        return super.isValid(place);
    }

    @Override
    protected List<MultilingualString> getNames(PlaceHierarchy<StopPlace> placeHierarchy) {
        List<MultilingualString> names = new ArrayList<>();

        collectNames(placeHierarchy, names, true);
        collectNames(placeHierarchy, names, false);

        return NetexPeliasMapperUtil.filterUnique(names);
    }

    private void collectNames(PlaceHierarchy<StopPlace> placeHierarchy, List<MultilingualString> names, boolean up) {
        StopPlace place = placeHierarchy.getPlace();
        if (place.getName() != null) {
            names.add(placeHierarchy.getPlace().getName());
        }

        if (place.getAlternativeNames() != null && !CollectionUtils.isEmpty(place.getAlternativeNames().getAlternativeName())) {
            place.getAlternativeNames().getAlternativeName().stream().filter(an -> an.getName() != null && (NameTypeEnumeration.LABEL.equals(an.getNameType()) || an.getName().getLang() != null)).forEach(n -> names.add(n.getName()));
        }

        if (up) {
            if (placeHierarchy.getParent() != null) {
                collectNames(placeHierarchy.getParent(), names, up);
            }
        } else {
            if (!CollectionUtils.isEmpty(placeHierarchy.getChildren())) {
                placeHierarchy.getChildren().forEach(child -> collectNames(child, names, up));
            }
        }
    }


    @Override
    protected void populateDocument(PlaceHierarchy<StopPlace> placeHierarchy, PeliasDocument document) {
        StopPlace place = placeHierarchy.getPlace();
        document.setSource(getSource(placeHierarchy));

        List<Pair<StopTypeEnumeration, Enum>> stopTypeAndSubModeList = aggregateStopTypeAndSubMode(placeHierarchy);

        document.setCategory(stopTypeAndSubModeList.stream().map(Pair::getLeft).filter(Objects::nonNull).map(StopTypeEnumeration::value).toList());

        if (place.getAlternativeNames() != null && !CollectionUtils.isEmpty(place.getAlternativeNames().getAlternativeName())) {
            place.getAlternativeNames().getAlternativeName().stream().filter(an -> NameTypeEnumeration.TRANSLATION.equals(an.getNameType()) && an.getName() != null && an.getName().getLang() != null).forEach(n -> document.addName(n.getName().getLang(), n.getName().getValue()));
        }
        addAlternativeNameLabels(document, placeHierarchy);
        if (document.getDefaultAlias() == null && document.getAliasMap() != null && !document.getAliasMap().isEmpty()) {
            String defaultAlias = Optional.of(document.getAliasMap().get(DEFAULT_LANGUAGE)).orElse(document.getAliasMap().values().iterator().next());
            document.getAliasMap().put("default", defaultAlias);
        }

        // Make stop place rank highest in autocomplete by setting popularity
        long popularity = boostConfiguration.getPopularity(stopTypeAndSubModeList, place.getWeighting());
        document.setPopularity(popularity);

        if (place.getTariffZones() != null && place.getTariffZones().getTariffZoneRef() != null) {
            document.setTariffZones(place.getTariffZones().getTariffZoneRef().stream()
                    .map(VersionOfObjectRefStructure::getRef)
                    .toList());


            // A bug in elasticsearch 2.3.4 used for pelias causes prefix queries for array values to fail, thus making it impossible to query by tariff zone prefixes. Instead adding
            // tariff zone authorities as a distinct indexed value.
            document.setTariffZoneAuthorities(place.getTariffZones().getTariffZoneRef().stream()
                    .map(zoneRef -> zoneRef.getRef().split(":")[0]).distinct()
                    .toList());
        }

        // Add parent info locality/county/country
        if (place.getTopographicPlaceRef() != null) {
            Parent parent = document.getParent();
            if (parent == null) {
                parent = new Parent();
                document.setParent(parent);
            }
            parent.setLocalityId(place.getTopographicPlaceRef().getRef());
        }
    }

    /**
     * Add alternative names with type 'label' to alias map. Use parents values if not set on stop place.
     */
    private void addAlternativeNameLabels(PeliasDocument document, PlaceHierarchy<StopPlace> placeHierarchy) {
        StopPlace place = placeHierarchy.getPlace();
        if (place.getAlternativeNames() != null && !CollectionUtils.isEmpty(place.getAlternativeNames().getAlternativeName())) {
            place.getAlternativeNames().getAlternativeName().stream().filter(an -> NameTypeEnumeration.LABEL.equals(an.getNameType()) && an.getName() != null).forEach(n -> document.addAlias(Optional.of(n.getName().getLang()).orElse("default"), n.getName().getValue()));
        }
        if ((document.getAliasMap() == null || document.getAliasMap().isEmpty()) && placeHierarchy.getParent() != null) {
            addAlternativeNameLabels(document, placeHierarchy.getParent());
        }
    }

    /**
     * Categorize multimodal stops with separate sources in order to be able to filter in queries.
     * <p>
     * Multimodal parents with one source
     * Multimodal children with another source
     * Non-multimodal stops with default soure
     *
     * @param hierarchy
     * @return
     */
    private String getSource(PlaceHierarchy<StopPlace> hierarchy) {
        if (hierarchy.getParent() != null) {
            return SOURCE_CHILD_STOP_PLACE;
        } else if (!CollectionUtils.isEmpty(hierarchy.getChildren())) {
            return SOURCE_PARENT_STOP_PLACE;
        }
        return PeliasDocument.DEFAULT_SOURCE;
    }

    @Override
    protected String getLayer(StopPlace place) {
        return STOP_PLACE_LAYER;
    }


    private List<Pair<StopTypeEnumeration, Enum>> aggregateStopTypeAndSubMode(PlaceHierarchy<StopPlace> placeHierarchy) {
        List<Pair<StopTypeEnumeration, Enum>> types = new ArrayList<>();

        StopPlace stopPlace = placeHierarchy.getPlace();

        types.add(new ImmutablePair<>(stopPlace.getStopPlaceType(), getStopSubMode(stopPlace)));

        if (!CollectionUtils.isEmpty(placeHierarchy.getChildren())) {
            types.addAll(placeHierarchy.getChildren().stream().map(this::aggregateStopTypeAndSubMode).flatMap(Collection::stream).toList());
        }

        return types;
    }


    private Enum getStopSubMode(StopPlace stopPlace) {

        if (stopPlace.getStopPlaceType() != null) {
            return switch (stopPlace.getStopPlaceType()) {
                case AIRPORT -> stopPlace.getAirSubmode();
                case HARBOUR_PORT, FERRY_STOP, FERRY_PORT -> stopPlace.getWaterSubmode();
                case BUS_STATION, COACH_STATION, ONSTREET_BUS -> stopPlace.getBusSubmode();
                case RAIL_STATION -> stopPlace.getRailSubmode();
                case METRO_STATION -> stopPlace.getMetroSubmode();
                case ONSTREET_TRAM, TRAM_STATION -> stopPlace.getTramSubmode();
                default -> null;
            };
        }
        return null;
    }

    private boolean isQuayLessNonParentStop(StopPlace place) {
        if (place.getQuays() == null || CollectionUtils.isEmpty(place.getQuays().getQuayRefOrQuay())) {
            return place.getKeyList() == null || place.getKeyList().getKeyValue().stream().noneMatch(kv -> KEY_IS_PARENT_STOP_PLACE.equals(kv.getKey()) && Boolean.TRUE.toString().equalsIgnoreCase(kv.getValue()));
        }
        return false;
    }
}
