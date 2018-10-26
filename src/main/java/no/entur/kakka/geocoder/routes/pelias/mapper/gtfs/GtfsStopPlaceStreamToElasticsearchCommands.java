package no.entur.kakka.geocoder.routes.pelias.mapper.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import org.apache.camel.Header;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

@Component
public class GtfsStopPlaceStreamToElasticsearchCommands {

    private GtfsStopPlaceToPeliasMapper mapper;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public GtfsStopPlaceStreamToElasticsearchCommands(@Autowired GtfsStopPlaceToPeliasMapper mapper) {
        this.mapper = mapper;
    }

    public Collection<ElasticsearchCommand> transform(InputStream gtfsStream, @Header(value = Constants.WORKING_DIRECTORY) String workingDirPath, @Header(value = Constants.FILE_HANDLE) String fileName) {
        if (!fileName.toLowerCase().endsWith(".zip")) {
            logger.info("Ignored non-zip file when transforming gtfs stop places to Elasticsearch commands: " + fileName);
            return new ArrayList<>();
        }
        File gtfsFile;

        try {
            File workingDir = new File(workingDirPath);
            if (!workingDir.exists()) {
                workingDir.mkdir();
            }

            gtfsFile = File.createTempFile("tmp_", Paths.get(fileName).getFileName().toString(), workingDir);
            FileUtils.copyInputStreamToFile(gtfsStream, gtfsFile);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create tmp file for gtfs: " + ioe.getMessage(), ioe);
        }

        GTFSFeed feed = GTFSFeed.fromFile(gtfsFile.getAbsolutePath());

        return feed.stops.values().stream()
                       .map(w -> ElasticsearchCommand.peliasIndexCommand(mapper.toPeliasDocument(w))).filter(d -> d != null).collect(Collectors.toList());
    }
}
