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

package no.entur.kakka.geocoder.netex;

import org.rutebanken.netex.model.LocaleStructure;
import org.rutebanken.netex.model.ModificationEnumeration;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.SiteFrame;
import org.rutebanken.netex.model.TopographicPlace;
import org.rutebanken.netex.model.VersionFrameDefaultsStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

@Component
public class TopographicPlaceConverter {
    private static final int QUEUE_SIZE = 10000;
    private final Logger logger = LoggerFactory.getLogger(getClass());


    private final String defaultTimeZone;

    public TopographicPlaceConverter(@Value("${tiamat.netex.import.time.zone:CET}") String defaultTimeZone) {
        this.defaultTimeZone = defaultTimeZone;
    }

    public void toNetexFile(TopographicPlaceReader input, String targetPath) {
        try {
            BlockingQueue<TopographicPlace> topographicPlaceQueue = new LinkedBlockingDeque<>(QUEUE_SIZE);

            ReaderTask reader = new ReaderTask(topographicPlaceQueue, input);
            new Thread(reader).start();

            File target = new File(targetPath);
            TopographicPlaceNetexWriter netexWriter = new TopographicPlaceNetexWriter();
            String siteFrameId = input.getParticipantRef() + ":SiteFrame:" + System.currentTimeMillis();
            netexWriter.stream(createPublicationDeliveryStructure(input, siteFrameId), topographicPlaceQueue, new FileOutputStream(target));

            reader.verify();
            logger.info("Wrote TopographicPlace NeTEx file with SiteFrame id={}", siteFrameId);
        } catch (Exception e) {
            throw new RuntimeException("Conversion to Netex failed with exception: " + e.getMessage(), e);
        }

    }

    private PublicationDeliveryStructure createPublicationDeliveryStructure(TopographicPlaceReader input, String siteFrameId) {
        VersionFrameDefaultsStructure frameDefaultsStructure = new VersionFrameDefaultsStructure().withDefaultLocale(new LocaleStructure().withTimeZone(defaultTimeZone));
        SiteFrame siteFrame = new SiteFrame()
                .withCreated(LocalDateTime.now()).withId(siteFrameId)
                .withModification(ModificationEnumeration.NEW).withVersion("any").withFrameDefaults(frameDefaultsStructure);

        return new PublicationDeliveryStructure()
                .withParticipantRef(input.getParticipantRef())
                .withPublicationTimestamp(LocalDateTime.now())
                .withDescription(input.getDescription())
                .withDataObjects(new PublicationDeliveryStructure.DataObjects()
                        .withCompositeFrameOrCommonFrame(new ObjectFactory().createSiteFrame(siteFrame)));
    }


    private class ReaderTask implements Runnable {

        private final BlockingQueue<TopographicPlace> queue;

        private final TopographicPlaceReader input;

        private Exception exception;

        public ReaderTask(BlockingQueue<TopographicPlace> queue, TopographicPlaceReader input) {
            this.queue = queue;
            this.input = input;
        }

        @Override
        public void run() {
            try {
                input.addToQueue(queue);

            } catch (Exception e) {
                exception = e;
            } finally {
                try {
                    queue.put(createPoisonPill());
                } catch (InterruptedException ie) {
                    logger.info("Reading topographic places interrupted", ie);
                }
            }

        }

        public void verify() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }

        private TopographicPlace createPoisonPill() {
            TopographicPlace topographicPlace = new TopographicPlace();
            topographicPlace.setId("POISON");
            return topographicPlace;
        }

    }

}
