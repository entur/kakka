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


import no.entur.kakka.domain.CustomConfiguration;
import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.netex.boost.StopPlaceBoostConfiguration;
import no.entur.kakka.services.CustomConfigurationService;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DeliveryPublicationStreamToElasticsearchCommandsTest {

    private static final Long POI_POPULARITY = 5l;


    @Test
    public void testTransform() throws Exception {
        CustomConfigurationService customConfigurationService = new CustomConfigurationService() {
            @Override
            public List<CustomConfiguration> findAllCustomConfigurations() {
                return null;
            }

            @Override
            public CustomConfiguration getCustomConfigurationByKey(String key) {
                final CustomConfiguration customConfiguration = new CustomConfiguration();
                customConfiguration.setId(100l);
                customConfiguration.setKey("poiFilter");
                customConfiguration.setValue("leisure=stadium,building=church");
                return customConfiguration;
            }

            @Override
            public CustomConfiguration updateCustomConfiguration(CustomConfiguration updatedConfiguration) {
                return null;
            }

            @Override
            public CustomConfiguration saveCustomConfiguration(CustomConfiguration customConfiguration) {
                return null;
            }

            @Override
            public void deleteCustomConfiguration(String key) {

            }
        };
        DeliveryPublicationStreamToElasticsearchCommands mapper =
                new DeliveryPublicationStreamToElasticsearchCommands(new StopPlaceBoostConfiguration("{\"defaultValue\":1000, \"stopTypeFactors\":{\"airport\":{\"*\":3},\"onstreetBus\":{\"*\":2}}}"),
                                                                            POI_POPULARITY, customConfigurationService, 1.0, true);

        Collection<ElasticsearchCommand> commands = mapper
                                                            .transform(new FileInputStream("src/test/resources/no/entur/kakka/geocoder/netex/tiamat-export.xml"));

        Assert.assertEquals(16, commands.size());
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

    private PeliasDocument byId(Collection<ElasticsearchCommand> commands, String sourceId) {
        return commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> d.getSourceId().equals(sourceId)).collect(Collectors.toList()).get(0);
    }

    private void assertNotMapped(Collection<ElasticsearchCommand> commands, String sourceId) {
        Assert.assertTrue("Id should not have been matched", commands.stream().map(c -> (PeliasDocument) c.getSource()).noneMatch(d -> d.getSourceId().equals(sourceId)));
    }

    private void assertKnownMultimodalStopPlaceParent(PeliasDocument known) throws Exception {
        Assert.assertEquals("QuayLessParentStop", known.getDefaultName());
        Assert.assertEquals(StopPlaceToPeliasMapper.STOP_PLACE_LAYER, known.getLayer());
        Assert.assertEquals(StopPlaceToPeliasMapper.SOURCE_PARENT_STOP_PLACE, known.getSource());
        Assert.assertEquals(known.getCategory().size(), 2);
        Assert.assertTrue(known.getCategory().containsAll(Arrays.asList("airport", "onstreetBus")));
        Assert.assertEquals("Expected popularity to be default (1000) boosted by sum of stop type boosts (airport=3, onstreetBus=2)", 5000, known.getPopularity().longValue());
    }

    private void assertKnownMultimodalStopPlaceChild(PeliasDocument known) throws Exception {
        Assert.assertEquals("Child stop - airport", known.getDefaultName());
        Assert.assertEquals(StopPlaceToPeliasMapper.STOP_PLACE_LAYER, known.getLayer());
        Assert.assertEquals(StopPlaceToPeliasMapper.SOURCE_CHILD_STOP_PLACE, known.getSource());
        Assert.assertEquals("Parent label", known.getAliasMap().get("nor"));
        Assert.assertEquals(known.getCategory().size(), 1);
        Assert.assertTrue(known.getCategory().containsAll(Arrays.asList("airport")));
        Assert.assertEquals("Expected popularity to be default (1000) boosted by stop type boosts (airport=3)", 3000, known.getPopularity().longValue());
    }

    private void assertKnownStopPlace(PeliasDocument known, String defaultName) throws Exception {
        Assert.assertEquals(defaultName, known.getDefaultName());
        Assert.assertEquals("Harstad/Narvik Lufthavn", known.getNameMap().get("nor"));
        Assert.assertEquals("Harstad/Narvik Lufthavn", known.getNameMap().get("display"));
        Assert.assertEquals("Evenes", known.getAliasMap().get("nor"));
        Assert.assertEquals(StopPlaceToPeliasMapper.STOP_PLACE_LAYER, known.getLayer());
        Assert.assertEquals(PeliasDocument.DEFAULT_SOURCE, known.getSource());
        Assert.assertEquals(Arrays.asList("airport"), known.getCategory());
        Assert.assertEquals(68.490412, known.getCenterPoint().getLat(), 0.0001);
        Assert.assertEquals(16.687364, known.getCenterPoint().getLon(), 0.0001);
        Assert.assertEquals(Arrays.asList("AKT:TariffZone:505"), known.getTariffZones());
        Assert.assertEquals(Arrays.asList("AKT"), known.getTariffZoneAuthorities());
        Assert.assertEquals("Expected popularity to be default (1000) boosted by stop type (airport)", 3000, known.getPopularity().longValue());
        Assert.assertEquals("Norsk beskrivelse", known.getDescriptionMap().get("nor"));
    }


    private void assertKnownGroupOfStopPlaces(PeliasDocument known, String defaultName) throws Exception {
        Assert.assertEquals(defaultName, known.getDefaultName());
        Assert.assertEquals("GoS Name", known.getNameMap().get("nor"));
        Assert.assertEquals("GoS Name", known.getNameMap().get("display"));
        Assert.assertEquals("address", known.getLayer());
        Assert.assertEquals(PeliasDocument.DEFAULT_SOURCE, known.getSource());
        Assert.assertEquals(Arrays.asList("GroupOfStopPlaces"), known.getCategory());
        Assert.assertEquals(60.002417, known.getCenterPoint().getLat(), 0.0001);
        Assert.assertEquals(10.272200, known.getCenterPoint().getLon(), 0.0001);

        Assert.assertEquals((3000 * 5000), known.getPopularity().longValue());
        Assert.assertEquals("GoS description", known.getDescriptionMap().get("nor"));
    }


    private void assertKnownPoi(PeliasDocument known) throws Exception {
        Assert.assertEquals("Stranda kyrkje", known.getDefaultName());
        Assert.assertEquals("Stranda kyrkje", known.getNameMap().get("nor"));
        Assert.assertEquals("address", known.getLayer());
        Assert.assertEquals(PeliasDocument.DEFAULT_SOURCE, known.getSource());
        Assert.assertEquals(Arrays.asList("poi"), known.getCategory());
        Assert.assertEquals(62.308413, known.getCenterPoint().getLat(), 0.0001);
        Assert.assertEquals(6.947573, known.getCenterPoint().getLon(), 0.0001);
        Assert.assertEquals(POI_POPULARITY, known.getPopularity());
    }


    private void assertCommand(ElasticsearchCommand command) {
        Assert.assertNotNull(command.getIndex());
        Assert.assertEquals("pelias", command.getIndex().getIndex());
        Assert.assertNotNull(command.getIndex().getType());
    }
}

