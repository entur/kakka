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

package no.entur.kakka.geocoder.netex.sosi;


import no.entur.kakka.geocoder.netex.TopographicPlaceMapper;
import no.entur.kakka.geocoder.netex.TopographicPlaceReader;
import no.entur.kakka.geocoder.sosi.SosiElementWrapperFactory;
import no.entur.kakka.geocoder.sosi.SosiTopographicPlaceAdapterReader;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.TopographicPlace;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;

public class SosiTopographicPlaceReader implements TopographicPlaceReader {
    private static final String LANGUAGE = "en";

    private static final String PARTICIPANT_REF = "KVE";
    private final Collection<File> sosiFiles;

    private final SosiElementWrapperFactory wrapperFactory;

    public SosiTopographicPlaceReader(SosiElementWrapperFactory wrapperFactory, Collection<File> sosiFiles) {
        this.sosiFiles = sosiFiles;
        this.wrapperFactory = wrapperFactory;
    }

    public void addToQueue(BlockingQueue<TopographicPlace> queue) throws IOException, InterruptedException {
        for (File file : sosiFiles) {
            new SosiTopographicPlaceAdapterReader(wrapperFactory, new FileInputStream(file)).read().forEach(a -> queue.add(new TopographicPlaceMapper(a, getParticipantRef()).toTopographicPlace()));
        }
    }


    @Override
    public String getParticipantRef() {
        return PARTICIPANT_REF;
    }

    @Override
    public MultilingualString getDescription() {
        return new MultilingualString().withLang(LANGUAGE).withValue("Kartverket administrative units");
    }
}
