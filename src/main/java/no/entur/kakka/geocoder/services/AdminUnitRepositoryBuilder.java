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

package no.entur.kakka.geocoder.services;

import com.google.cloud.storage.Storage;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.opengis.gml._3.AbstractRingPropertyType;
import net.opengis.gml._3.DirectPositionListType;
import net.opengis.gml._3.LinearRingType;
import no.entur.kakka.domain.BlobStoreFiles;
import no.entur.kakka.exceptions.FileValidationException;
import no.entur.kakka.geocoder.netex.TopographicPlaceAdapter;
import no.entur.kakka.repository.BlobStoreRepository;
import no.entur.kakka.routes.file.ZipFileUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.rutebanken.netex.model.Common_VersionFrameStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.Site_VersionFrameStructure;
import org.rutebanken.netex.model.TopographicPlace;
import org.rutebanken.netex.model.TopographicPlaceTypeEnumeration;
import org.rutebanken.netex.model.ValidBetween;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static javax.xml.bind.JAXBContext.newInstance;

@Service
public class AdminUnitRepositoryBuilder {
    @Value("${admin.units.cache.max.size:30000}")
    private Integer cacheMaxSize;

    @Value("${tiamat.geocoder.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;

    @Value("${pelias.download.directory:files/adminUnitCache}")
    private String localWorkingDirectory;

    @Autowired
    BlobStoreRepository repository;

    @Autowired
    Storage storage;

    @Value("${blobstore.gcs.container.name}")
    String containerName;

    @PostConstruct
    public void init() {
        repository.setStorage(storage);
        repository.setContainerName(containerName);
    }

    public AdminUnitRepository build() {
        RefreshCache refreshJob = new RefreshCache();
        refreshJob.buildNewCache();
        return new CacheAdminUnitRepository(refreshJob.tmpCache, refreshJob.localities);
    }

    private class CacheAdminUnitRepository implements AdminUnitRepository {

        private Cache<String, String> idCache;

        private List<TopographicPlaceAdapter> localities;

        public CacheAdminUnitRepository(Cache<String, String> idCache, List<TopographicPlaceAdapter> localities) {
            this.idCache = idCache;
            this.localities = localities;
        }

        @Override
        public String getAdminUnitName(String id) {
            return idCache.getIfPresent(id);
        }

        @Override
        public TopographicPlaceAdapter getLocality(Point point) {
            return getTopographicPlaceAdapter(point, localities);
        }

        private TopographicPlaceAdapter getTopographicPlaceAdapter(Point point, List<TopographicPlaceAdapter> topographicPlaces) {
            if (topographicPlaces == null) {
                return null;
            }

            for (TopographicPlaceAdapter topographicPlace : topographicPlaces) {
                var polygon=topographicPlace.getDefaultGeometry();
                if (polygon != null) {
                    if (polygon.covers(point)) {
                        return topographicPlace;
                    }
                }
            }
            return null;
        }
    }


   private class RefreshCache {

        private Cache<String, String> tmpCache;

        private List<TopographicPlaceAdapter> localities;

        public void buildNewCache() {
            BlobStoreFiles blobs = repository.listBlobs(blobStoreSubdirectoryForTiamatGeoCoderExport);

            localities = new ArrayList<>();
            tmpCache = CacheBuilder.newBuilder().maximumSize(cacheMaxSize).build();

            for (BlobStoreFiles.File blob : blobs.getFiles()) {
                if (blob.getName().endsWith(".zip")) {
                    ZipFileUtils.unzipFile(repository.getBlob(blob.getName()), localWorkingDirectory);
                } else if (blob.getName().endsWith(".xml")) {
                    try {
                        FileUtils.copyInputStreamToFile(repository.getBlob(blob.getName()), new File(localWorkingDirectory + "/" + blob.getName()));
                    } catch (IOException ioe) {
                        throw new RuntimeException("Failed to download admin units file: " + ioe.getMessage(), ioe);
                    }
                }
            }
            FileUtils.listFiles(
                    new File(localWorkingDirectory), new String[]{"xml"}, true)
                    .forEach(f -> fromDeliveryPublicationStructure(f).forEach(this::addAdminUnit));

            new File(localWorkingDirectory).delete();
        }
        private List<TopographicPlace> fromDeliveryPublicationStructure(File publicationDeliveryStream) {
            try {
                PublicationDeliveryStructure deliveryStructure = unmarshall(new FileInputStream(publicationDeliveryStream));
                for (JAXBElement<? extends Common_VersionFrameStructure> frameStructureElmt : deliveryStructure.getDataObjects().getCompositeFrameOrCommonFrame()) {
                    Common_VersionFrameStructure frameStructure = frameStructureElmt.getValue();
                    if (frameStructure instanceof Site_VersionFrameStructure) {
                        Site_VersionFrameStructure siteFrame = (Site_VersionFrameStructure) frameStructure;
                        if (siteFrame.getTopographicPlaces() != null) {
                            return siteFrame.getTopographicPlaces().getTopographicPlace();
                        }

                    }
                }
            } catch (Exception e) {
                throw new FileValidationException("Parsing of DeliveryPublications failed: " + e.getMessage(), e);
            }

            return Collections.emptyList();
        }
        private PublicationDeliveryStructure unmarshall(InputStream in) throws Exception {
            JAXBContext publicationDeliveryContext = newInstance(PublicationDeliveryStructure.class);
            Unmarshaller unmarshaller = publicationDeliveryContext.createUnmarshaller();

            JAXBElement<PublicationDeliveryStructure> jaxbElement = unmarshaller.unmarshal(new StreamSource(in), PublicationDeliveryStructure.class);
            return jaxbElement.getValue();
        }
        private void addAdminUnit(TopographicPlace topographicPlace) {
            if (isCurrent(topographicPlace)) {
                var polygon=topographicPlace.getPolygon();
                final LocalDateTime toDate = topographicPlace.getValidBetween().get(0).getToDate();
                if (toDate == null || toDate.isAfter(LocalDateTime.now())) {
                    if (polygon != null) {
                        final Polygon geometry = new GeometryFactory().createPolygon(convertToCoordinateSequence(polygon.getExterior()));
                        var topographicPlaceAdapter= netexTopographicPlaceAdapter(topographicPlace,geometry);
                        if (topographicPlace.getTopographicPlaceType().equals(TopographicPlaceTypeEnumeration.MUNICIPALITY)) {
                            localities.add(topographicPlaceAdapter);
                            tmpCache.put(topographicPlaceAdapter.getId(), topographicPlaceAdapter.getName());
                        }
                    }
                }

            }

        }

       private boolean isCurrent(TopographicPlace topographicPlace) {
           ValidBetween validBetween = null;
           if (!topographicPlace.getValidBetween().isEmpty()) {
               validBetween = topographicPlace.getValidBetween().get(0);
           }
           if (validBetween == null) {
               return false;
           }
           final LocalDateTime fromDate = validBetween.getFromDate();
           final LocalDateTime toDate = validBetween.getToDate();
           if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
               //Invalid Validity toDate < fromDate
               return false;
           } else return fromDate != null && toDate == null || Objects.requireNonNull(fromDate).isBefore(toDate);
       }

       private TopographicPlaceAdapter netexTopographicPlaceAdapter(TopographicPlace topographicPlace, Polygon geometry) {
            return new TopographicPlaceAdapter() {
                @Override
                public String getId() {
                    // We just need last part of id
                    return StringUtils.substringAfterLast(topographicPlace.getId(),":");
                }

                @Override
                public String getIsoCode() {
                    return topographicPlace.getIsoCode();
                }

                @Override
                public String getParentId() {
                    if (topographicPlace.getParentTopographicPlaceRef() != null) {
                        return topographicPlace.getParentTopographicPlaceRef().getRef();
                    }
                    return null;
                }

                @Override
                public String getName() {
                    return topographicPlace.getDescriptor().getName().getValue();
                }

                @Override
                public Type getType() {
                    switch (topographicPlace.getTopographicPlaceType()) {
                        case COUNTRY:
                            return Type.COUNTRY;
                        case COUNTY:
                            return Type.COUNTY;
                        case MUNICIPALITY:
                            return Type.LOCALITY;
                        default:
                            return null;
                    }

                }

                @Override
                public Geometry getDefaultGeometry() {
                    return geometry;
                }

                @Override
                public Map<String, String> getAlternativeNames() {
                    return null;
                }

                @Override
                public String getCountryRef() {
                    var locale = new Locale("en",topographicPlace.getCountryRef().getRef().name());
                    return locale.getISO3Country();
                }

                @Override
                public List<String> getCategories() {
                    return null;
                }

                @Override
                public boolean isValid() {
                    return false;
                }
            };

        }

        private CoordinateSequence convertToCoordinateSequence(AbstractRingPropertyType abstractRingPropertyType) {
            final List<Double> coordinateValues = Optional.of(abstractRingPropertyType)
                    .map(AbstractRingPropertyType::getAbstractRing)
                    .map(JAXBElement::getValue)
                    .map(abstractRing -> ((LinearRingType) abstractRing))
                    .map(LinearRingType::getPosList)
                    .map(DirectPositionListType::getValue)
                    .get();

            Coordinate[] coordinates = new Coordinate[coordinateValues.size()/2];
            int coordinateIndex = 0;
            for (int index = 0; index < coordinateValues.size(); index += 2) {
                Coordinate coordinate = new Coordinate(coordinateValues.get(index+1), coordinateValues.get(index));
                coordinates[coordinateIndex++] = coordinate;
            }
            return new CoordinateArraySequence(coordinates);
        }
    }
}
