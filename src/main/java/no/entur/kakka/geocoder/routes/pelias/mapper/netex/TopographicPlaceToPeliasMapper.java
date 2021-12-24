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


import no.entur.kakka.domain.OSMPOIFilter;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import org.rutebanken.netex.model.KeyValueStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.TopographicPlace;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

import static org.rutebanken.netex.model.TopographicPlaceTypeEnumeration.PLACE_OF_INTEREST;

public class TopographicPlaceToPeliasMapper extends AbstractNetexPlaceToPeliasDocumentMapper<TopographicPlace> {

    private final long popularity;

    private final List<String> typeFilter;

    private final List<OSMPOIFilter> osmpoiFilters;

    public TopographicPlaceToPeliasMapper(long popularity, List<String> typeFilter, List<OSMPOIFilter> osmpoiFilters) {
        super();
        this.popularity = popularity;
        this.typeFilter = typeFilter;
        this.osmpoiFilters = osmpoiFilters;
    }

    @Override
    protected void populateDocument(PlaceHierarchy<TopographicPlace> placeHierarchy, PeliasDocument document) {
        TopographicPlace place = placeHierarchy.getPlace();
        if (place.getAlternativeDescriptors() != null && !CollectionUtils.isEmpty(place.getAlternativeDescriptors().getTopographicPlaceDescriptor())) {
            place.getAlternativeDescriptors().getTopographicPlaceDescriptor().stream().filter(an -> an.getName() != null && an.getName().getLang() != null).forEach(n -> document.addName(n.getName().getLang(), n.getName().getValue()));
        }

        if (PLACE_OF_INTEREST.equals(place.getTopographicPlaceType())) {
            document.setPopularity(popularity * getPopularityBoost(place));
            setPOICategories(document, place.getKeyList().getKeyValue());
        } else {
            document.setPopularity(popularity);
        }
    }

    private void setPOICategories(PeliasDocument document, List<KeyValueStructure> keyValue) {
        List<String> categories = new ArrayList<>();
        categories.add("poi");
        for (KeyValueStructure keyValueStructure : keyValue) {
            var key = keyValueStructure.getKey();
            var value = keyValueStructure.getValue();
            var category = osmpoiFilters.stream()
                    .filter(f -> key.equals(f.getKey()) && value.equals(f.getValue()))
                    .map(OSMPOIFilter::getValue)
                    .findFirst();
            category.ifPresent(categories::add);
        }
        document.setCategory(categories);
    }

    private int getPopularityBoost(TopographicPlace place) {
        return osmpoiFilters.stream().filter(f ->
                place.getKeyList().getKeyValue().stream()
                        .anyMatch(key -> key.getKey().equals(f.getKey()) && key.getValue().equals(f.getValue()))
        ).max(OSMPOIFilter::sort).map(OSMPOIFilter::getPriority).orElse(1);
    }

    @Override
    protected MultilingualString getDisplayName(PlaceHierarchy<TopographicPlace> placeHierarchy) {
        TopographicPlace place = placeHierarchy.getPlace();
        if (place.getName() != null) {
            return placeHierarchy.getPlace().getName();
        }    // Use descriptor.name if name is not set
        else if (place.getDescriptor() != null && place.getDescriptor().getName() != null) {
            return place.getDescriptor().getName();
        }
        return null;
    }

    @Override
    protected List<MultilingualString> getNames(PlaceHierarchy<TopographicPlace> placeHierarchy) {
        List<MultilingualString> names = new ArrayList<>();
        TopographicPlace place = placeHierarchy.getPlace();

        MultilingualString displayName = getDisplayName(placeHierarchy);
        if (displayName != null) {
            names.add(displayName);
        }

        if (place.getAlternativeDescriptors() != null && !CollectionUtils.isEmpty(place.getAlternativeDescriptors().getTopographicPlaceDescriptor())) {
            place.getAlternativeDescriptors().getTopographicPlaceDescriptor().stream().filter(an -> an.getName() != null && an.getName().getLang() != null).forEach(n -> names.add(n.getName()));
        }
        return NetexPeliasMapperUtil.filterUnique(names);
    }


    @Override
    protected boolean isValid(TopographicPlace place) {
        return isFilterMatch(place) && super.isValid(place);
    }

    private boolean isFilterMatch(TopographicPlace place) {
        if (CollectionUtils.isEmpty(typeFilter)) {
            return true;
        }
        if (place.getKeyList() == null || place.getKeyList().getKeyValue() == null) {
            return false;
        }

        return typeFilter.stream()
                .anyMatch(filter -> place.getKeyList().getKeyValue().stream()
                        .map(key -> key.getKey() + "=" + key.getValue())
                        .anyMatch(tag -> filter.startsWith(tag)));
    }

    @Override
    protected String getLayer(TopographicPlace place) {
        switch (place.getTopographicPlaceType()) {

            case PLACE_OF_INTEREST:
                return "address";
            case MUNICIPALITY:
                return "locality";

            case COUNTY:
                return "county";

            case COUNTRY:
                return "country";

            case AREA:
                return "borough";
            default:
                return null;

        }
    }


}
