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

import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.TopographicPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import javax.xml.stream.XMLStreamException;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.BlockingQueue;

import static jakarta.xml.bind.JAXBContext.newInstance;

public class TopographicPlaceNetexWriter {

    private static final Logger logger = LoggerFactory.getLogger(TopographicPlaceNetexWriter.class);

    private static final JAXBContext publicationDeliveryContext = createContext(PublicationDeliveryStructure.class);
    private static final JAXBContext topographicPlaceContext = createContext(org.rutebanken.netex.model.TopographicPlace.class);
    private static final ObjectFactory netexObjectFactory = new ObjectFactory();

    private static JAXBContext createContext(Class clazz) {
        try {
            return newInstance(clazz);
        } catch (JAXBException e) {
            logger.warn("Could not create instance of jaxb context for class {}", clazz, e);
            throw new RuntimeException(e);
        }
    }

    public void stream(PublicationDeliveryStructure publicationDeliveryStructure, BlockingQueue<TopographicPlace> topographicPlacesQueue, OutputStream outputStream) throws JAXBException, XMLStreamException, IOException, InterruptedException {
        String publicationDeliveryStructureXml = writePublicationDeliverySkeletonToString(publicationDeliveryStructure);
        stream(publicationDeliveryStructureXml, topographicPlacesQueue, outputStream);
    }

    private String writePublicationDeliverySkeletonToString(PublicationDeliveryStructure publicationDeliveryStructure) throws JAXBException {
        JAXBElement<PublicationDeliveryStructure> jaxPublicationDelivery = netexObjectFactory.createPublicationDelivery(publicationDeliveryStructure);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        Marshaller publicationDeliveryMarshaller = publicationDeliveryContext.createMarshaller();

        publicationDeliveryMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        publicationDeliveryMarshaller.marshal(jaxPublicationDelivery, byteArrayOutputStream);
        return byteArrayOutputStream.toString();
    }


    private Marshaller createTopographicPlaceMarshaller() throws JAXBException {
        Marshaller topographicPlaceMarshaller = topographicPlaceContext.createMarshaller();
        topographicPlaceMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        topographicPlaceMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        topographicPlaceMarshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "");
        return topographicPlaceMarshaller;
    }


    /**
     * In order to not hold all topographic places in memory at once, we need to marshal topographic place from a queue.
     * Requires a publication delivery xml that contains newlines.
     */
    public void stream(String publicationDeliveryStructureXml, BlockingQueue<org.rutebanken.netex.model.TopographicPlace> topographicPlaces, OutputStream outputStream) throws JAXBException, XMLStreamException, IOException, InterruptedException {

        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
        BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);

        try {
            Marshaller topographicPlaceMarshaller = createTopographicPlaceMarshaller();

            String lineSeparator = System.lineSeparator();
            String[] publicationDeliveryLines = publicationDeliveryStructureXml.split(lineSeparator);

            for (String publicationDeliveryLine : publicationDeliveryLines) {
                logger.debug("Line: {}", publicationDeliveryLine);

                if (publicationDeliveryLine.contains("</SiteFrame")) {
                    marshallTopographicPlaces(topographicPlaces, bufferedWriter, topographicPlaceMarshaller, lineSeparator);
                }

                bufferedWriter.write(publicationDeliveryLine);
                bufferedWriter.write(lineSeparator);
            }
        } finally {
            bufferedWriter.flush();
        }
    }

    private void marshallTopographicPlaces(BlockingQueue<org.rutebanken.netex.model.TopographicPlace> topographicPlaceQueue,
                                           BufferedWriter bufferedWriter,
                                           Marshaller topographicPlaceMarshaller,
                                           String lineSeparator) throws InterruptedException, JAXBException, IOException {
        logger.info("Marshaling topographic places");

        int count = 0;
        while (true) {
            org.rutebanken.netex.model.TopographicPlace topographicPlace = topographicPlaceQueue.take();

            if (topographicPlace.getId().equals("POISON")) {
                logger.debug("Got poison pill from topographic place queue. Finished marshaling {} topographic places.", count);
                break;
            }

            if (count == 0) {
                bufferedWriter.write("<topographicPlaces>");
                bufferedWriter.write(lineSeparator);
            }

            ++count;
            logger.debug("Marshalling topographic place {}: {}", count, topographicPlace);
            JAXBElement<TopographicPlace> jaxBStopPlace = netexObjectFactory.createTopographicPlace(topographicPlace);
            topographicPlaceMarshaller.marshal(jaxBStopPlace, bufferedWriter);
            bufferedWriter.write(lineSeparator);
        }
        if (count > 0) {
            bufferedWriter.write("</topographicPlaces>");
            bufferedWriter.write(lineSeparator);
        }
    }
}
