package no.entur.kakka.geocoder.netex.osm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.net.URL;

public class OsmSchemaValidator {

    private static final Logger logger = LoggerFactory.getLogger(OsmSchemaValidator.class);

    private final Schema schema;

    public OsmSchemaValidator() throws IOException, SAXException {
        schema = createOsmSchema();
    }

    private Schema createOsmSchema() throws IOException, SAXException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        String resourceName = "xsd/OSMSchema.xsd";
        logger.info("Loading resource: {}", resourceName);
        URL resource = getClass().getClassLoader().getResource(resourceName);
        if (resource == null) {
            throw new IOException("Cannot load resource " + resourceName);
        }
        return factory.newSchema(resource);
    }

    public void validate(Source source) throws IOException, SAXException {
        schema.newValidator().validate(source);
    }

    public Schema getSchema() {
        return schema;
    }
}
