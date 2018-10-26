package no.entur.kakka.geocoder.routes.pelias.mapper;

import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.gtfs.GtfsStopPlaceStreamToElasticsearchCommands;
import no.entur.kakka.geocoder.routes.pelias.mapper.gtfs.GtfsStopPlaceToPeliasMapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GtfsStopPlaceStreamToElasticsearchCommandsTest {

    private static final long POPULARITY = 5;

    @Test
    public void testStreamAddressesToIndexCommands() throws Exception {
        GtfsStopPlaceStreamToElasticsearchCommands transformer = new GtfsStopPlaceStreamToElasticsearchCommands(new GtfsStopPlaceToPeliasMapper(POPULARITY));

        Collection<ElasticsearchCommand> commands = transformer
                                                            .transform(new FileInputStream("src/test/resources/no/entur/kakka/geocoder/gtfs/flb.zip"), "target/","flb.zip");


        Assert.assertEquals(9, commands.size());

        Map<String, PeliasDocument> documents = commands.stream().map(ec -> (PeliasDocument) ec.getSource()).collect(Collectors.toMap(PeliasDocument::getSourceId, Function.identity()));

        Assert.assertTrue(documents.values().stream().allMatch(d -> d.getPopularity() == POPULARITY));
        Assert.assertTrue(documents.values().stream().allMatch(d -> d.getLayer() == "venue"));

        assertKnownStopPlace(documents.get("NSR:StopPlace:457"));

    }

    private void assertKnownStopPlace(PeliasDocument known) {
        Assert.assertNotNull("Known stop place not found in mapped data", known);
        Assert.assertEquals("NSR:StopPlace:457", known.getSourceId());
        Assert.assertEquals("Berekvam stasjon", known.getDefaultName());

        Assert.assertEquals(60.78827, known.getCenterPoint().getLat(), 0.0001);
        Assert.assertEquals(7.095725, known.getCenterPoint().getLon(), 0.0001);
    }

}
