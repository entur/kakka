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

import no.entur.kakka.exceptions.FileValidationException;
import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.netex.boost.StopPlaceBoostConfiguration;
import no.entur.kakka.services.OSMPOIFilterService;
import org.rutebanken.netex.model.Common_VersionFrameStructure;
import org.rutebanken.netex.model.GroupOfStopPlaces;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.Site_VersionFrameStructure;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.TopographicPlace;
import org.rutebanken.netex.model.TopographicPlaceTypeEnumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.xml.bind.JAXBContext.newInstance;

@Service
public class DeliveryPublicationStreamToElasticsearchCommands {

    private OSMPOIFilterService osmpoiFilterService;

    public final static Logger logger= LoggerFactory.getLogger(DeliveryPublicationStreamToElasticsearchCommands.class);

    private StopPlaceBoostConfiguration stopPlaceBoostConfiguration;

    private final long poiBoost;

    private final double gosBoostFactor;

    private boolean gosInclude;

    private final List<String> poiFilter;

    private final boolean mapPOIFromNetex;

    private final boolean ignoreRailReplacementBus;

    public DeliveryPublicationStreamToElasticsearchCommands(@Autowired StopPlaceBoostConfiguration stopPlaceBoostConfiguration, @Value("${pelias.poi.boost:1}") long poiBoost,
                                                            @Value("#{'${pelias.poi.filter:}'.split(',')}") List<String> poiFilter, @Value("${pelias.gos.boost.factor.:1.0}") double gosBoostFactor,
                                                            @Value("${pelias.gos.include:true}") boolean gosInclude, @Autowired OSMPOIFilterService osmpoiFilterService,@Value("${pelias.poi.include:true}") boolean mapPOIFromNetex,
                                                            @Value("${pelias.ignore-rail-replacement-bus:true}") boolean ignoreRailReplacementBus
    ) {
        this.stopPlaceBoostConfiguration = stopPlaceBoostConfiguration;
        this.poiBoost = poiBoost;
        this.gosBoostFactor = gosBoostFactor;
        this.gosInclude = gosInclude;
        this.osmpoiFilterService = osmpoiFilterService;
        this.mapPOIFromNetex = mapPOIFromNetex;
        this.ignoreRailReplacementBus = ignoreRailReplacementBus;
        if (poiFilter != null) {
            this.poiFilter = poiFilter.stream().filter(filter -> !StringUtils.isEmpty(filter)).collect(Collectors.toList());
            logger.info("pelias poiFilter is set to: " + poiFilter );
        } else {
            this.poiFilter = new ArrayList<>();
            logger.info("No pelias poiFilter found");
        }
    }

    public Collection<ElasticsearchCommand> transform(InputStream publicationDeliveryStream) {
        try {
            PublicationDeliveryStructure deliveryStructure = unmarshall(publicationDeliveryStream);
            return fromDeliveryPublicationStructure(deliveryStructure);
        } catch (Exception e) {
            throw new FileValidationException("Parsing of DeliveryPublications failed: " + e.getMessage(), e);
        }
    }


    Collection<ElasticsearchCommand> fromDeliveryPublicationStructure(PublicationDeliveryStructure deliveryStructure) {
        List<ElasticsearchCommand> commands = new ArrayList<>();
        List<ElasticsearchCommand> stopPlaceCommands = null;
        List<GroupOfStopPlaces> groupOfStopPlaces = null;
        for (JAXBElement<? extends Common_VersionFrameStructure> frameStructureElmt : deliveryStructure.getDataObjects().getCompositeFrameOrCommonFrame()) {
            Common_VersionFrameStructure frameStructure = frameStructureElmt.getValue();
            if (frameStructure instanceof Site_VersionFrameStructure) {
                Site_VersionFrameStructure siteFrame = (Site_VersionFrameStructure) frameStructure;

                if (siteFrame.getStopPlaces() != null) {
                    stopPlaceCommands = addStopPlaceCommands(siteFrame.getStopPlaces().getStopPlace());
                    commands.addAll(stopPlaceCommands);
                }
                if (siteFrame.getTopographicPlaces() != null) {
                    commands.addAll(addTopographicPlaceCommands(siteFrame.getTopographicPlaces().getTopographicPlace(),mapPOIFromNetex));
                }
                if (siteFrame.getGroupsOfStopPlaces() != null) {
                    groupOfStopPlaces = siteFrame.getGroupsOfStopPlaces().getGroupOfStopPlaces();
                }
            }
        }

        if (gosInclude && groupOfStopPlaces != null) {
            commands.addAll(addGroupsOfStopPlacesCommands(groupOfStopPlaces, mapPopularityPerStopPlaceId(stopPlaceCommands)));
        }

        return commands;
    }

    private Map<String, Long> mapPopularityPerStopPlaceId(List<ElasticsearchCommand> stopPlaceCommands) {
        Map<String, Long> popularityPerStopPlaceId = new HashMap<>();
        if (!CollectionUtils.isEmpty(stopPlaceCommands)) {
            for (ElasticsearchCommand command : stopPlaceCommands) {
                PeliasDocument pd = (PeliasDocument) command.getSource();
                popularityPerStopPlaceId.put(pd.getSourceId(), pd.getPopularity());
            }
        }
        return popularityPerStopPlaceId;
    }

    private List<ElasticsearchCommand> addGroupsOfStopPlacesCommands(List<GroupOfStopPlaces> groupsOfStopPlaces, Map<String, Long> popularityPerStopPlaceId) {

        if (!CollectionUtils.isEmpty(groupsOfStopPlaces)) {
            GroupOfStopPlacesToPeliasMapper mapper = new GroupOfStopPlacesToPeliasMapper();
            return groupsOfStopPlaces.stream().map(gos -> mapper.toPeliasDocuments(gos, getPopularityForGroupOfStopPlaces(gos, popularityPerStopPlaceId))).flatMap(documents -> documents.stream()).sorted(new PeliasDocumentPopularityComparator()).filter(d -> d != null).map(p -> ElasticsearchCommand.peliasIndexCommand(p)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private Long getPopularityForGroupOfStopPlaces(GroupOfStopPlaces groupOfStopPlaces, Map<String, Long> popularityPerStopPlaceId) {
        if (groupOfStopPlaces.getMembers() == null) {
            return null;
        }
        try {
            double popularity = gosBoostFactor * groupOfStopPlaces.getMembers().getStopPlaceRef().stream().map(sp -> popularityPerStopPlaceId.get(sp.getRef())).filter(Objects::nonNull).reduce(1l, Math::multiplyExact);
            return (long) popularity;
        } catch (ArithmeticException ae) {
            return Long.MAX_VALUE;
        }
    }

    private List<ElasticsearchCommand> addTopographicPlaceCommands(List<TopographicPlace> places, boolean mapPOIFromNetex) {
        if (!CollectionUtils.isEmpty(places)) {
            logger.info("Total number of topographical places from tiamat: " + places.size());

            TopographicPlaceToPeliasMapper mapper = new TopographicPlaceToPeliasMapper(poiBoost, poiFilter, osmpoiFilterService.getFilters());
            final List<ElasticsearchCommand> collect = places.stream()
                    .filter(mapPOIFromNetex ? p -> true : p -> p.getTopographicPlaceType() != TopographicPlaceTypeEnumeration.PLACE_OF_INTEREST)
                    .map(p -> mapper.toPeliasDocuments(new PlaceHierarchy<>(p)))
                    .flatMap(documents -> documents.stream())
                    .sorted(new PeliasDocumentPopularityComparator())
                    .filter(d -> d != null)
                    .map(p -> ElasticsearchCommand.peliasIndexCommand(p))
                    .collect(Collectors.toList());
            logger.info("Total topographical places mapped forElasticsearchCommand: " + collect.size());
            return collect;
        }
        return new ArrayList<>();
    }

    private List<ElasticsearchCommand> addStopPlaceCommands(List<StopPlace> places) {
        if (!CollectionUtils.isEmpty(places)) {
            StopPlaceToPeliasMapper mapper = new StopPlaceToPeliasMapper(stopPlaceBoostConfiguration, ignoreRailReplacementBus);

            Set<PlaceHierarchy<StopPlace>> stopPlaceHierarchies = toPlaceHierarchies(places);

            return stopPlaceHierarchies.stream().map(p -> mapper.toPeliasDocuments(p)).flatMap(documents -> documents.stream()).sorted(new PeliasDocumentPopularityComparator()).map(p -> ElasticsearchCommand.peliasIndexCommand(p)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }


    private void expandStopPlaceHierarchies(Collection<PlaceHierarchy<StopPlace>> hierarchies, Set<PlaceHierarchy<StopPlace>> target) {
        if (hierarchies != null) {
            for (PlaceHierarchy<StopPlace> stopPlacePlaceHierarchy : hierarchies) {
                target.add(stopPlacePlaceHierarchy);
                expandStopPlaceHierarchies(stopPlacePlaceHierarchy.getChildren(), target);
            }
        }
    }


    /**
     * Map list of stop places to list of hierarchies.
     */
    protected Set<PlaceHierarchy<StopPlace>> toPlaceHierarchies(List<StopPlace> places) {
        Map<String, List<StopPlace>> childrenByParentIdMap = places.stream().filter(sp -> sp.getParentSiteRef() != null).collect(Collectors.groupingBy(sp -> sp.getParentSiteRef().getRef()));
        Set<PlaceHierarchy<StopPlace>> allStopPlaces = new HashSet<>();
        expandStopPlaceHierarchies(places.stream().filter(sp -> sp.getParentSiteRef() == null).map(sp -> createHierarchyForStopPlace(sp, null, childrenByParentIdMap)).collect(Collectors.toList()), allStopPlaces);
        return allStopPlaces;
    }


    private PlaceHierarchy<StopPlace> createHierarchyForStopPlace(StopPlace stopPlace, PlaceHierarchy<StopPlace> parent, Map<String, List<StopPlace>> childrenByParentIdMap) {
        List<StopPlace> children = childrenByParentIdMap.get(stopPlace.getId());
        List<PlaceHierarchy<StopPlace>> childHierarchies = new ArrayList<>();
        PlaceHierarchy<StopPlace> hierarchy = new PlaceHierarchy<>(stopPlace, parent);
        if (children != null) {
            childHierarchies = children.stream().map(child -> createHierarchyForStopPlace(child, hierarchy, childrenByParentIdMap)).collect(Collectors.toList());
        }
        hierarchy.setChildren(childHierarchies);
        return hierarchy;
    }


    private PublicationDeliveryStructure unmarshall(InputStream in) throws Exception {
        JAXBContext publicationDeliveryContext = newInstance(PublicationDeliveryStructure.class);
        Unmarshaller unmarshaller = publicationDeliveryContext.createUnmarshaller();
        JAXBElement<PublicationDeliveryStructure> jaxbElement = unmarshaller.unmarshal(new StreamSource(in), PublicationDeliveryStructure.class);
        return jaxbElement.getValue();
    }

    private class PeliasDocumentPopularityComparator implements Comparator<PeliasDocument> {

        @Override
        public int compare(PeliasDocument o1, PeliasDocument o2) {
            Long p1 = o1 == null || o1.getPopularity() == null ? 1l : o1.getPopularity();
            Long p2 = o2 == null || o2.getPopularity() == null ? 1l : o2.getPopularity();
            return -p1.compareTo(p2);
        }
    }
}
