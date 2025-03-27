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

package no.entur.kakka.geocoder.routes.pelias.mapper.kartverket;


import no.entur.kakka.geocoder.routes.pelias.json.AddressParts;
import no.entur.kakka.geocoder.routes.pelias.json.GeoPoint;
import no.entur.kakka.geocoder.routes.pelias.json.Parent;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.kartverket.KartverketAddress;
import no.entur.kakka.geocoder.routes.pelias.mapper.coordinates.GeometryTransformer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;

@Service
public class AddressToPeliasMapper {


    // Use unique source for addresses to allow for filtering them out from pelias autocomplete
    private static final String SOURCE = "openaddresses";
    private final long popularity;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GeometryFactory factory = new GeometryFactory();

    public AddressToPeliasMapper(@Value("${pelias.address.boost:2}") long popularity) {
        this.popularity = popularity;
    }

    public PeliasDocument toPeliasDocument(KartverketAddress address) {
        PeliasDocument document = new PeliasDocument("address", SOURCE, address.getAddresseId());
        document.setAddressParts(toAddressParts(address));
        document.setCenterPoint(toCenterPoint(address));
        document.setParent(toParent(address));

        document.setDefaultNameAndPhrase(toName(address));
        document.setCategory(Collections.singletonList(address.getType()));
        document.setPopularity(popularity);
        return document;
    }

    private String toName(KartverketAddress address) {
        return address.getAddressenavn() + " " + address.getNr() + address.getBokstav();
    }

    private GeoPoint toCenterPoint(KartverketAddress address) {
        if (address.getNord() == null || address.getOst() == null) {
            return null;
        }
        String utmZone = KartverketCoordinatSystemMapper.toUTMZone(address.getKoordinatsystemKode());
        if (utmZone == null) {
            logger.info("Ignoring center point for address with non-utm coordinate system: {}", address.getKoordinatsystemKode());
            return null;
        }
        Point p = factory.createPoint(new Coordinate(address.getOst(), address.getNord()));
        try {
            Point conv = GeometryTransformer.fromUTM(p, utmZone);
            return new GeoPoint(conv.getY(), conv.getX());
        } catch (Exception e) {
            logger.info("Ignoring center point for address ({}) where geometry transformation failed: {}", address.getAddresseId(), address.getKoordinatsystemKode());
        }

        return null;
    }

    private Parent toParent(KartverketAddress address) {
        return Parent.builder().withPostalCodeId(address.getPostnrn())
                .withLocalityId("KVE:TopographicPlace:" + address.getFullKommuneNo())
                .withBoroughId(address.getGrunnkretsnr())
                .withBorough(formatName(address.getGrunnkretsnavn()))
                .build();
    }


    private String formatName(String name) {
        return WordUtils.capitalize(StringUtils.lowerCase((name)), ' ', '/');
    }

    private AddressParts toAddressParts(KartverketAddress address) {
        AddressParts addressParts = new AddressParts();
        addressParts.setName(address.getAddressenavn());
        addressParts.setStreet(address.getAddressenavn());
        addressParts.setNumber(address.getNr() + address.getBokstav());
        addressParts.setZip(address.getPostnrn());
        return addressParts;
    }

}
