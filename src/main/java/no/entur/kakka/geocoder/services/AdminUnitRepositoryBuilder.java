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
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Unmarshaller;
import net.opengis.gml._3.AbstractRingPropertyType;
import net.opengis.gml._3.DirectPositionListType;
import net.opengis.gml._3.LinearRingType;
import no.entur.kakka.domain.BlobStoreFiles;
import no.entur.kakka.exceptions.FileValidationException;
import no.entur.kakka.geocoder.netex.TopographicPlaceAdapter;
import no.entur.kakka.repository.BlobStoreRepository;
import no.entur.kakka.routes.file.ZipFileUtils;
import org.apache.commons.io.FileUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.rutebanken.netex.model.Common_VersionFrameStructure;
import org.rutebanken.netex.model.GroupOfStopPlaces;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.Site_VersionFrameStructure;
import org.rutebanken.netex.model.TopographicPlace;
import org.rutebanken.netex.model.TopographicPlaceTypeEnumeration;
import org.rutebanken.netex.model.ValidBetween;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static jakarta.xml.bind.JAXBContext.newInstance;

@Service
public class AdminUnitRepositoryBuilder {
    @Autowired
    BlobStoreRepository repository;
    @Autowired
    Storage storage;
    @Value("${blobstore.gcs.container.name}")
    String containerName;
    @Value("${admin.units.cache.max.size:30000}")
    private Integer cacheMaxSize;
    @Value("${tiamat.geocoder.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;
    @Value("${pelias.download.directory:files/adminUnitCache}")
    private String localWorkingDirectory;

    @PostConstruct
    public void init() {
        repository.setStorage(storage);
        repository.setContainerName(containerName);
    }

    public AdminUnitRepository build() throws IOException {
        RefreshCache refreshJob = new RefreshCache();
        refreshJob.buildNewCache();
        return new CacheAdminUnitRepository(refreshJob.tmpCache, refreshJob.localities, refreshJob.countries, refreshJob.groupOfStopPlaces);
    }

    private class CacheAdminUnitRepository implements AdminUnitRepository {

        private final Cache<String, String> idCache;

        private final List<TopographicPlaceAdapter> localities;

        private final List<TopographicPlaceAdapter> countries;

        private final List<GroupOfStopPlaces> groupOfStopPlaces;

        public CacheAdminUnitRepository(Cache<String, String> idCache, List<TopographicPlaceAdapter> localities, List<TopographicPlaceAdapter> countries, List<GroupOfStopPlaces> groupOfStopPlaces) {
            this.idCache = idCache;
            this.localities = localities;
            this.countries = countries;
            this.groupOfStopPlaces = groupOfStopPlaces;

        }

        @Override
        public GroupOfStopPlaces getGroupOfStopPlaces(String name) {
            return groupOfStopPlaces.stream().filter(gosp -> gosp.getName().getValue().equals(name)).findFirst().orElse(null);
        }

        @Override
        public String getAdminUnitName(String id) {
            return idCache.getIfPresent(id);
        }

        @Override
        public TopographicPlaceAdapter getLocality(String id) {
            for (TopographicPlaceAdapter topographicPlace : localities) {
                var topographicPlaceId = topographicPlace.getId();
                if (topographicPlaceId != null && topographicPlaceId.equals(id)) {
                    return topographicPlace;
                }
            }
            return null;
        }

        @Override
        public TopographicPlaceAdapter getLocality(Point point) {
            return getTopographicPlaceAdapter(point, localities);
        }

        @Override
        public TopographicPlaceAdapter getCountry(Point point) {
            return getTopographicPlaceAdapter(point, countries);
        }

        private TopographicPlaceAdapter getTopographicPlaceAdapter(Point point, List<TopographicPlaceAdapter> topographicPlaces) {
            if (topographicPlaces == null) {
                return null;
            }

            for (TopographicPlaceAdapter topographicPlace : topographicPlaces) {
                var polygon = topographicPlace.getDefaultGeometry();
                if (polygon != null && polygon.covers(point)) {
                    return topographicPlace;
                }
            }
            return null;
        }
    }


    private class RefreshCache {

        private Cache<String, String> tmpCache;

        private List<TopographicPlaceAdapter> localities;

        private List<TopographicPlaceAdapter> countries;

        private List<GroupOfStopPlaces> groupOfStopPlaces;

        public void buildNewCache() throws IOException {
            BlobStoreFiles blobs = repository.listBlobs(blobStoreSubdirectoryForTiamatGeoCoderExport);

            localities = new ArrayList<>();
            countries = new ArrayList<>();
            groupOfStopPlaces = new ArrayList<>();

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
                    .forEach(f -> {

                        List<GroupOfStopPlaces> gosp = new ArrayList<>();

                        List<TopographicPlace> tp = new ArrayList<>();
                        fromDeliveryPublicationStructure(tp, gosp, f);

                        if (!gosp.isEmpty()) {
                            gosp.forEach(this::addGroupOfStopPlaces);
                        }
                        if (!tp.isEmpty()) {
                            tp.forEach(this::addAdminUnit);
                        }
                    });

            FileUtils.deleteDirectory(new File(localWorkingDirectory));
        }

        private void addGroupOfStopPlaces(GroupOfStopPlaces gosp) {
            groupOfStopPlaces.add(gosp);
        }

        private void addAdminUnit(TopographicPlace topographicPlace) {
            if (isCurrent(topographicPlace)) {
                var polygon = topographicPlace.getPolygon();
                final LocalDateTime toDate = topographicPlace.getValidBetween().getFirst().getToDate();
                if (toDate == null || toDate.isAfter(LocalDateTime.now())) {
                    if (polygon != null) {
                        final Polygon geometry = new GeometryFactory().createPolygon(convertToCoordinateSequence(polygon.getExterior()));
                        var topographicPlaceAdapter = netexTopographicPlaceAdapter(topographicPlace, geometry);
                        if (topographicPlace.getTopographicPlaceType().equals(TopographicPlaceTypeEnumeration.MUNICIPALITY)) {
                            localities.add(topographicPlaceAdapter);
                            tmpCache.put(topographicPlaceAdapter.getId(), topographicPlaceAdapter.getName());
                        }
                        if (topographicPlace.getTopographicPlaceType().equals(TopographicPlaceTypeEnumeration.COUNTY)) {
                            tmpCache.put(topographicPlaceAdapter.getId(), topographicPlaceAdapter.getName());
                        }
                        if (topographicPlace.getTopographicPlaceType().equals(TopographicPlaceTypeEnumeration.COUNTRY)) {
                            if (!topographicPlace.getCountryRef().getRef().equals(IanaCountryTldEnumeration.RU)) {
                                countries.add(topographicPlaceAdapter);
                            }
                        }

                    }
                }

            }

        }

        private void fromDeliveryPublicationStructure(List<TopographicPlace> topographicPlaces, List<GroupOfStopPlaces> groupOfStopPlaces, File publicationDeliveryStream) {
            try {
                PublicationDeliveryStructure deliveryStructure = unmarshall(new FileInputStream(publicationDeliveryStream));
                for (JAXBElement<? extends Common_VersionFrameStructure> frameStructureElmt : deliveryStructure.getDataObjects().getCompositeFrameOrCommonFrame()) {
                    Common_VersionFrameStructure frameStructure = frameStructureElmt.getValue();
                    if (frameStructure instanceof Site_VersionFrameStructure siteFrame) {
                        if (siteFrame.getTopographicPlaces() != null) {
                            topographicPlaces.addAll(siteFrame.getTopographicPlaces().getTopographicPlace());
                        }

                        if (siteFrame.getGroupsOfStopPlaces() != null) {
                            groupOfStopPlaces.addAll(siteFrame.getGroupsOfStopPlaces().getGroupOfStopPlaces());
                        }

                    }
                }
            } catch (Exception e) {
                throw new FileValidationException("Parsing of DeliveryPublications failed: " + e.getMessage(), e);
            }
        }

        private PublicationDeliveryStructure unmarshall(InputStream in) throws Exception {
            JAXBContext publicationDeliveryContext = newInstance(PublicationDeliveryStructure.class);
            Unmarshaller unmarshaller = publicationDeliveryContext.createUnmarshaller();

            JAXBElement<PublicationDeliveryStructure> jaxbElement = unmarshaller.unmarshal(new StreamSource(in), PublicationDeliveryStructure.class);
            return jaxbElement.getValue();
        }

        private boolean isCurrent(TopographicPlace topographicPlace) {
            ValidBetween validBetween = null;
            if (!topographicPlace.getValidBetween().isEmpty()) {
                validBetween = topographicPlace.getValidBetween().getFirst();
            }
            if (validBetween == null) {
                return false;
            }
            final LocalDateTime fromDate = validBetween.getFromDate();
            final LocalDateTime toDate = validBetween.getToDate();
            if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
                //Invalid Validity toDate < fromDate
                return false;
            } else return fromDate != null && toDate == null;
        }

        private TopographicPlaceAdapter netexTopographicPlaceAdapter(TopographicPlace topographicPlace, Polygon geometry) {
            var id = topographicPlace.getId();
            var isoCode = topographicPlace.getIsoCode();
            final String parentId;
            if (topographicPlace.getParentTopographicPlaceRef() != null) {
                parentId = topographicPlace.getParentTopographicPlaceRef().getRef();
            } else {
                parentId = null;
            }
            var name = topographicPlace.getDescriptor().getName().getValue();
            var topographicPlaceType = topographicPlace.getTopographicPlaceType();

            var countryRef = Locale.of("en", topographicPlace.getCountryRef().getRef().name());
            return new TopographicPlaceAdapter() {


                @Override
                public String getId() {
                    return id;
                }

                @Override
                public String getIsoCode() {
                    return isoCode;
                }

                @Override
                public String getParentId() {
                    return parentId;
                }

                @Override
                public String getName() {
                    return name;
                }

                @Override
                public Type getType() {
                    return switch (topographicPlaceType) {
                        case COUNTRY -> Type.COUNTRY;
                        case COUNTY -> Type.COUNTY;
                        case MUNICIPALITY -> Type.LOCALITY;
                        default -> null;
                    };

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
                    return countryRef.getISO3Country();
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

            Coordinate[] coordinates = new Coordinate[coordinateValues.size() / 2];
            int coordinateIndex = 0;
            for (int index = 0; index < coordinateValues.size(); index += 2) {
                Coordinate coordinate = new Coordinate(coordinateValues.get(index + 1), coordinateValues.get(index));
                coordinates[coordinateIndex++] = coordinate;
            }
            return new CoordinateArraySequence(coordinates);
        }
    }
}
