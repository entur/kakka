package no.entur.kakka.geocoder.routes.pelias.mapper.osm;

import no.entur.kakka.domain.OSMPOIFilter;
import no.entur.kakka.geocoder.netex.pbf.PbfTopographicPlaceReader;
import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.netex.PlaceHierarchy;
import no.entur.kakka.geocoder.routes.pelias.mapper.netex.TopographicPlaceToPeliasMapper;
import no.entur.kakka.services.OSMPOIFilterService;
import org.apache.commons.io.IOUtils;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.TopographicPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

@Service
public class PbfToElasticsearchCommands {

    public static final Logger logger = LoggerFactory.getLogger(PbfToElasticsearchCommands.class);

    private final OSMPOIFilterService osmpoiFilterService;

    private final long poiBoost;

    private final List<String> poiFilter;

    public PbfToElasticsearchCommands(@Autowired OSMPOIFilterService osmpoiFilterService,
                                      @Value("${pelias.poi.boost:1}") long poiBoost,
                                      @Value("#{'${pelias.poi.filter:}'.split(',')}") List<String> poiFilter) {
        this.osmpoiFilterService = osmpoiFilterService;
        this.poiBoost = poiBoost;
        if (poiFilter != null) {
            this.poiFilter = poiFilter.stream().filter(filter -> !ObjectUtils.isEmpty(filter)).collect(Collectors.toList());
            logger.info("pelias poiFilter is set to: {}", poiFilter);
        } else {
            this.poiFilter = new ArrayList<>();
            logger.info("No pelias poiFilter found");
        }
    }

    public Collection<ElasticsearchCommand> transform(InputStream poiStream) throws IOException {
        List<OSMPOIFilter> osmPoiFilter = osmpoiFilterService.getFilters();
        File tmpPoiFile = getFile(poiStream);
        var reader = new PbfTopographicPlaceReader(osmPoiFilter, IanaCountryTldEnumeration.NO, tmpPoiFile);
        BlockingQueue<TopographicPlace> queue = new LinkedBlockingDeque<>();
        reader.addToQueue(queue);
        List<TopographicPlace> topographicPlaceList = new ArrayList<>(queue);
        return new ArrayList<>(addTopographicPlaceCommands(topographicPlaceList));
    }

    private File getFile(InputStream poiStream) throws IOException {
        File tmpPoiFile = File.createTempFile("tmp", "poi");
        tmpPoiFile.deleteOnExit();
        var out = new FileOutputStream(tmpPoiFile);
        IOUtils.copy(poiStream, out);
        return tmpPoiFile;
    }

    private List<ElasticsearchCommand> addTopographicPlaceCommands(List<TopographicPlace> places) {
        if (!CollectionUtils.isEmpty(places)) {
            logger.info("Total number of topographical places from osm: {}", places.size());

            TopographicPlaceToPeliasMapper mapper = new TopographicPlaceToPeliasMapper(poiBoost, poiFilter, osmpoiFilterService.getFilters());
            final List<ElasticsearchCommand> collect = places.stream()
                    .map(p -> mapper.toPeliasDocuments(new PlaceHierarchy<>(p)))
                    .flatMap(Collection::stream)
                    .sorted(new PeliasDocumentPopularityComparator())
                    .filter(Objects::nonNull)
                    .map(ElasticsearchCommand::peliasIndexCommand)
                    .collect(Collectors.toList());
            logger.info("Total topographical places mapped forElasticsearchCommand: {}", collect.size());
            return collect;
        }
        return new ArrayList<>();
    }

    private static class PeliasDocumentPopularityComparator implements Comparator<PeliasDocument> {

        @Override
        public int compare(PeliasDocument o1, PeliasDocument o2) {
            Long p1 = o1 == null || o1.getPopularity() == null ? 1L : o1.getPopularity();
            Long p2 = o2 == null || o2.getPopularity() == null ? 1L : o2.getPopularity();
            return -p1.compareTo(p2);
        }
    }

}

