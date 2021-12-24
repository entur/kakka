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

package no.entur.kakka.geocoder.routes.pelias.mapper;


import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ActionMetaData;
import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.GeoPoint;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class PeliasIndexValidCommandFilterTest {

    private final PeliasIndexValidCommandFilter filter = new PeliasIndexValidCommandFilter();

    @Test
    public void testInvalidCommandsAreRemoved() {

        PeliasDocument inValidDoc = validDoc();
        inValidDoc.setCenterPoint(null);
        ElasticsearchCommand validCmnd = ElasticsearchCommand.peliasIndexCommand(validDoc());

        validCmnd.getIndex().setId("idValid");

        List<ElasticsearchCommand> commandList = Arrays.asList(validCmnd, ElasticsearchCommand.peliasIndexCommand(inValidDoc));
        List<ElasticsearchCommand> filteredList = filter.removeInvalidCommands(commandList);

        Assertions.assertEquals(1, filteredList.size());
        Assertions.assertEquals(validCmnd.getIndex().getId(), filteredList.get(0).getIndex().getId());
    }

    @Test
    public void testNullCommandIsInvalid() {
        Assertions.assertFalse(filter.isValid(null));
    }

    @Test
    public void testNonIndexCommandIsInvalid() {
        Assertions.assertFalse(filter.isValid(new ElasticsearchCommand()));
    }

    @Test
    public void testIndexCommandWithoutIndexNameIsInvalid() {
        ElasticsearchCommand command = new ElasticsearchCommand();
        command.setCreate(new ActionMetaData(null, "type", null));
        Assertions.assertFalse(filter.isValid(command));
    }

    @Test
    public void testIndexCommandWithoutTypeIsInvalid() {
        ElasticsearchCommand command = new ElasticsearchCommand();
        command.setCreate(new ActionMetaData("pelias", null, null));
        Assertions.assertFalse(filter.isValid(command));
    }

    @Test
    public void testIndexCommandWithoutLayerIsInvalid() {
        PeliasDocument invalidDoc = validDoc();
        invalidDoc.setLayer(null);
        Assertions.assertFalse(filter.isValid(ElasticsearchCommand.peliasIndexCommand(invalidDoc)));
    }

    @Test
    public void testIndexCommandWithoutSourceIsInvalid() {
        PeliasDocument invalidDoc = validDoc();
        invalidDoc.setSource(null);
        Assertions.assertFalse(filter.isValid(ElasticsearchCommand.peliasIndexCommand(invalidDoc)));
    }

    @Test
    public void testIndexCommandWithoutSourceIdIsInvalid() {
        PeliasDocument invalidDoc = validDoc();
        invalidDoc.setSourceId(null);
        Assertions.assertFalse(filter.isValid(ElasticsearchCommand.peliasIndexCommand(invalidDoc)));
    }

    @Test
    public void testIndexCommandWithoutCenterPointIsInvalid() {
        PeliasDocument invalidDoc = validDoc();
        invalidDoc.setCenterPoint(null);
        Assertions.assertFalse(filter.isValid(ElasticsearchCommand.peliasIndexCommand(invalidDoc)));
    }

    @Test
    public void testValidCommand() {
        Assertions.assertTrue(filter.isValid(ElasticsearchCommand.peliasIndexCommand(validDoc())));
    }

    private PeliasDocument validDoc() {
        PeliasDocument valid = new PeliasDocument("layer", "sourceId");
        valid.setCenterPoint(new GeoPoint(1.0, 2.0));
        return valid;
    }
}
