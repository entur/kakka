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


import no.entur.kakka.geocoder.routes.pelias.json.AddressParts;
import no.entur.kakka.geocoder.routes.pelias.json.GeoPoint;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.Place_VersionStructure;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public abstract class AbstractNetexPlaceToPeliasDocumentMapper<T extends Place_VersionStructure> {

    protected static final String DEFAULT_LANGUAGE = "nor";

    /**
     * Map single place hierarchy to (potentially) multiple pelias documents, one per alias/alternative name.
     * <p>
     * Pelias does not yet support queries in multiple languages / for aliases. When support for this is ready this mapping should be
     * refactored to produce a single document per place hierarchy.
     */
    public List<PeliasDocument> toPeliasDocuments(PlaceHierarchy<T> placeHierarchy) {
        T place = placeHierarchy.getPlace();
        if (!isValid(place)) {
            return new ArrayList<>();
        }
        AtomicInteger cnt = new AtomicInteger();

        return getNames(placeHierarchy).stream()
                .map(name -> toPeliasDocument(placeHierarchy, name, cnt.getAndAdd(1)))
                .collect(Collectors.toList());
    }

    private PeliasDocument toPeliasDocument(PlaceHierarchy<T> placeHierarchy, MultilingualString name, int idx) {
        T place = placeHierarchy.getPlace();

        String idSuffix = idx > 0 ? "-" + idx : "";

        PeliasDocument document = new PeliasDocument(getLayer(place), place.getId() + idSuffix);
        if (name != null) {
            document.setDefaultNameAndPhrase(name.getValue());
        }

        // Add official name as display name. Not a part of standard pelias model, will be copied to name.default before deducting and labelling in Entur-pelias API.
        MultilingualString displayName = getDisplayName(placeHierarchy);
        if (displayName != null) {
            document.getNameMap().put("display", displayName.getValue());
            if (displayName.getLang() != null) {
                document.addName(displayName.getLang(), displayName.getValue());
            }
        }

        if (place.getCentroid() != null) {
            LocationStructure loc = place.getCentroid().getLocation();
            document.setCenterPoint(new GeoPoint(loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue()));
        }

        if (place.getPolygon() != null) {
            // TODO issues with shape validation in elasticsearch. duplicate coords + intersections cause document to be discarded. is shape even used by pelias?
            document.setShape(NetexPeliasMapperUtil.toPolygon(place.getPolygon().getExterior().getAbstractRing().getValue()));
        }

        addIdToStreetNameToAvoidFalseDuplicates(place, document);

        if (place.getDescription() != null && !StringUtils.isEmpty(place.getDescription().getValue())) {
            String lang = place.getDescription().getLang();
            if (lang == null) {
                lang = DEFAULT_LANGUAGE;
            }
            document.addDescription(lang, place.getDescription().getValue());
        }

        populateDocument(placeHierarchy, document);
        return document;
    }

    /**
     * Get name from current place or, if not set, on closest parent with name set.
     */
    protected MultilingualString getDisplayName(PlaceHierarchy<T> placeHierarchy) {
        if (placeHierarchy.getPlace().getName() != null) {
            return placeHierarchy.getPlace().getName();
        }
        if (placeHierarchy.getParent() != null) {
            return getDisplayName(placeHierarchy.getParent());
        }
        return null;
    }


    protected abstract List<MultilingualString> getNames(PlaceHierarchy<T> placeHierarchy);

    protected boolean isValid(T place) {
        String layer = getLayer(place);

        if (layer == null) {
            return false;
        }
        return NetexPeliasMapperUtil.isValid(place);
    }

    /**
     * The Pelias APIs deduper will throw away results with identical name, layer, parent and address. Setting unique ID in street part of address to avoid unique
     * topographic places with identical names being deduped.
     */
    private void addIdToStreetNameToAvoidFalseDuplicates(T place, PeliasDocument document) {
        if (document.getAddressParts() == null) {
            document.setAddressParts(new AddressParts());
        }
        document.getAddressParts().setStreet("NOT_AN_ADDRESS-" + place.getId());
    }


    protected abstract void populateDocument(PlaceHierarchy<T> placeHierarchy, PeliasDocument document);

    protected abstract String getLayer(T place);

}
