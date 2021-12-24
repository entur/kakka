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


import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.netex.boost.StopPlaceBoostConfiguration;
import no.entur.kakka.repository.OSMPOIFilterRepository;
import no.entur.kakka.services.OSMPOIFilterRepositoryStub;
import no.entur.kakka.services.OSMPOIFilterService;
import no.entur.kakka.services.OSMPOIFilterServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DeliveryPublicationStreamToElasticsearchCommandsTest {

    private static final Long POI_POPULARITY = 5l;


    @Test
    public void testTransform() throws Exception {
        OSMPOIFilterRepository osmpoiFilterRepository = new OSMPOIFilterRepositoryStub();
        OSMPOIFilterService osmpoiFilterService = new OSMPOIFilterServiceImpl(osmpoiFilterRepository, 1);
        DeliveryPublicationStreamToElasticsearchCommands mapper =
                new DeliveryPublicationStreamToElasticsearchCommands(new StopPlaceBoostConfiguration("{\"defaultValue\":1000, \"stopTypeFactors\":{\"airport\":{\"*\":3},\"onstreetBus\":{\"*\":2}}}"),
                                                                            POI_POPULARITY, Arrays.asList("leisure=stadium", "building=church"), 1.0, true, osmpoiFilterService, true);

        Collection<ElasticsearchCommand> commands = mapper
                                                            .transform(new FileInputStream("src/test/resources/no/entur/kakka/geocoder/netex/tiamat-export.xml"));

        Assertions.assertEquals(16, commands.size());
        commands.forEach(c -> assertCommand(c));

        assertKnownPoi(byId(commands, "NSR:TopographicPlace:724"));
        assertKnownStopPlace(byId(commands, "NSR:StopPlace:39231"), "Harstad/Narvik Lufthavn");
        assertKnownStopPlace(byId(commands, "NSR:StopPlace:39231-1"), "AliasName");
        assertKnownGroupOfStopPlaces(byId(commands, "NSR:GroupOfStopPlaces:1"), "GoS Name");
        assertKnownGroupOfStopPlaces(byId(commands, "NSR:GroupOfStopPlaces:1-1"), "GoS AliasName");

        assertKnownMultimodalStopPlaceParent(byId(commands, "NSR:StopPlace:1000"));
        assertKnownMultimodalStopPlaceChild(byId(commands, "NSR:StopPlace:1000a"));

        // Rail replacement bus stop should not be mapped
        assertNotMapped(commands, "NSR:StopPlace:1001");
        // Stop without quay should not be mapped
        assertNotMapped(commands, "NSR:StopPlace:1002");
        // Outdated stop should not be mapped
        assertNotMapped(commands, "NSR:StopPlace:1003");

        // POI not matching filter should not be mapped
        assertNotMapped(commands, "NSR:TopographicPlace:725");
    }

    @Test
    public void testPOIFilterProperty() throws Exception {
        OSMPOIFilterRepository osmpoiFilterRepository = new OSMPOIFilterRepositoryStub();
        OSMPOIFilterService osmpoiFilterService = new OSMPOIFilterServiceImpl(osmpoiFilterRepository, 1);
        DeliveryPublicationStreamToElasticsearchCommands mapper =
                new DeliveryPublicationStreamToElasticsearchCommands(new StopPlaceBoostConfiguration("{\"defaultValue\":1000, \"stopTypeFactors\":{\"airport\":{\"*\":3},\"onstreetBus\":{\"*\":2}}}"),
                        POI_POPULARITY, Arrays.asList("leisure=stadium", "building=church"), 1.0, true, osmpoiFilterService, false);

        Collection<ElasticsearchCommand> commands = mapper
                .transform(new FileInputStream("src/test/resources/no/entur/kakka/geocoder/netex/tiamat-export.xml"));

        Assertions.assertEquals(15, commands.size());
        commands.forEach(this::assertCommand);


        final List<PeliasDocument> collect = commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(p -> p.getCategory().contains("poi")).collect(Collectors.toList());

        Assertions.assertTrue(collect.isEmpty());
    }

    private PeliasDocument byId(Collection<ElasticsearchCommand> commands, String sourceId) {
        return commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> d.getSourceId().equals(sourceId)).collect(Collectors.toList()).get(0);
    }

    private void assertNotMapped(Collection<ElasticsearchCommand> commands, String sourceId) {
        Assertions.assertTrue(commands.stream().map(c -> (PeliasDocument) c.getSource()).noneMatch(d -> d.getSourceId().equals(sourceId)),"Id should not have been matched");
    }

    private void assertKnownMultimodalStopPlaceParent(PeliasDocument known) throws Exception {
        Assertions.assertEquals("QuayLessParentStop", known.getDefaultName());
        Assertions.assertEquals(StopPlaceToPeliasMapper.STOP_PLACE_LAYER, known.getLayer());
        Assertions.assertEquals(StopPlaceToPeliasMapper.SOURCE_PARENT_STOP_PLACE, known.getSource());
        Assertions.assertEquals(known.getCategory().size(), 2);
        Assertions.assertTrue(known.getCategory().containsAll(Arrays.asList("airport", "onstreetBus")));
        Assertions.assertTrue(known.getCategoryFilter().containsAll(Arrays.asList("airport", "onstreetbus")));
        Assertions.assertEquals(5000, known.getPopularity().longValue(),"Expected popularity to be default (1000) boosted by sum of stop type boosts (airport=3, onstreetBus=2)");
    }

    private void assertKnownMultimodalStopPlaceChild(PeliasDocument known) throws Exception {
        Assertions.assertEquals("Child stop - airport", known.getDefaultName());
        Assertions.assertEquals(StopPlaceToPeliasMapper.STOP_PLACE_LAYER, known.getLayer());
        Assertions.assertEquals(StopPlaceToPeliasMapper.SOURCE_CHILD_STOP_PLACE, known.getSource());
        Assertions.assertEquals("Parent label", known.getAliasMap().get("nor"));
        Assertions.assertEquals(known.getCategory().size(), 1);
        Assertions.assertTrue(known.getCategory().containsAll(Arrays.asList("airport")));
        Assertions.assertTrue(known.getCategoryFilter().containsAll(Arrays.asList("airport")));
        Assertions.assertEquals(3000, known.getPopularity().longValue(),"Expected popularity to be default (1000) boosted by stop type boosts (airport=3)");
    }

    private void assertKnownStopPlace(PeliasDocument known, String defaultName) throws Exception {
        Assertions.assertEquals(defaultName, known.getDefaultName());
        Assertions.assertEquals("Harstad/Narvik Lufthavn", known.getNameMap().get("nor"));
        Assertions.assertEquals("Harstad/Narvik Lufthavn", known.getNameMap().get("display"));
        Assertions.assertEquals("Evenes", known.getAliasMap().get("nor"));
        Assertions.assertEquals(StopPlaceToPeliasMapper.STOP_PLACE_LAYER, known.getLayer());
        Assertions.assertEquals(PeliasDocument.DEFAULT_SOURCE, known.getSource());
        Assertions.assertEquals(Arrays.asList("airport"), known.getCategory());
        Assertions.assertEquals(Arrays.asList("airport"), known.getCategoryFilter());
        Assertions.assertEquals(68.490412, known.getCenterPoint().getLat(), 0.0001);
        Assertions.assertEquals(16.687364, known.getCenterPoint().getLon(), 0.0001);
        Assertions.assertEquals(Arrays.asList("AKT:TariffZone:505"), known.getTariffZones());
        Assertions.assertEquals(Arrays.asList("AKT"), known.getTariffZoneAuthorities());
        Assertions.assertEquals(3000, known.getPopularity().longValue(),"Expected popularity to be default (1000) boosted by stop type (airport)");
        Assertions.assertEquals("Norsk beskrivelse", known.getDescriptionMap().get("nor"));
    }


    private void assertKnownGroupOfStopPlaces(PeliasDocument known, String defaultName) throws Exception {
        Assertions.assertEquals(defaultName, known.getDefaultName());
        Assertions.assertEquals("GoS Name", known.getNameMap().get("nor"));
        Assertions.assertEquals("GoS Name", known.getNameMap().get("display"));
        Assertions.assertEquals("address", known.getLayer());
        Assertions.assertEquals(PeliasDocument.DEFAULT_SOURCE, known.getSource());
        Assertions.assertEquals(Arrays.asList("GroupOfStopPlaces"), known.getCategory());
        Assertions.assertEquals(Arrays.asList("groupofstopplaces"), known.getCategoryFilter());
        Assertions.assertEquals(60.002417, known.getCenterPoint().getLat(), 0.0001);
        Assertions.assertEquals(10.272200, known.getCenterPoint().getLon(), 0.0001);

        Assertions.assertEquals((3000 * 5000), known.getPopularity().longValue());
        Assertions.assertEquals("GoS description", known.getDescriptionMap().get("nor"));
    }


    private void assertKnownPoi(PeliasDocument known) throws Exception {
        Assertions.assertEquals("Stranda kyrkje", known.getDefaultName());
        Assertions.assertEquals("Stranda kyrkje", known.getNameMap().get("nor"));
        Assertions.assertEquals("address", known.getLayer());
        Assertions.assertEquals(PeliasDocument.DEFAULT_SOURCE, known.getSource());
        Assertions.assertEquals(Arrays.asList("poi"), known.getCategory());
        Assertions.assertEquals(Arrays.asList("poi"), known.getCategoryFilter());
        Assertions.assertEquals(62.308413, known.getCenterPoint().getLat(), 0.0001);
        Assertions.assertEquals(6.947573, known.getCenterPoint().getLon(), 0.0001);
        Assertions.assertEquals(POI_POPULARITY, known.getPopularity());
    }


    private void assertCommand(ElasticsearchCommand command) {
        Assertions.assertNotNull(command.getIndex());
        Assertions.assertEquals("pelias", command.getIndex().getIndex());
        Assertions.assertNotNull(command.getIndex().getType());
    }
}

