package no.entur.kakka.geocoder.netex.osm;


import org.openstreetmap.osm.Osm;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.UnmarshallerHandler;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;

/**
 * Unmarshaller that makes it possible to read OSM XML files without namespace.
 */
public class OsmUnmarshaller {

    private static final String OSM_NAMESPACE = "http://openstreetmap.org/osm/0.6";

    private final XMLFilter namespaceFilter;

    private final UnmarshallerHandler unmarshallerHandler;

    public OsmUnmarshaller(boolean performValidation) throws ParserConfigurationException, SAXException, JAXBException, IOException {
        JAXBContext osmContext = JAXBContext.newInstance(Osm.class);
        namespaceFilter = new NamespaceFilter(OSM_NAMESPACE);
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        SAXParser saxParser = saxParserFactory.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();
        namespaceFilter.setParent(xmlReader);
        Unmarshaller osmContextUnmarshaller = osmContext.createUnmarshaller();

        if(performValidation) {
            OsmSchemaValidator osmSchemaValidator = new OsmSchemaValidator();
            osmContextUnmarshaller.setSchema(osmSchemaValidator.getSchema());
        }
        unmarshallerHandler = osmContextUnmarshaller.getUnmarshallerHandler();
        namespaceFilter.setContentHandler(unmarshallerHandler);
    }

    public Osm unmarshall(InputSource source) throws JAXBException, IOException, SAXException {
        namespaceFilter.parse(source);
        return (Osm) unmarshallerHandler.getResult();
    }

}
