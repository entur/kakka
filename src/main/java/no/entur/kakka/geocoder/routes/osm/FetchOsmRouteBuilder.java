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

package no.entur.kakka.geocoder.routes.osm;


import no.entur.kakka.Constants;
import no.entur.kakka.exceptions.KakkaException;
import no.entur.kakka.exceptions.Md5ChecksumValidationException;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

import static no.entur.kakka.Constants.FILE_HANDLE;
import static no.entur.kakka.geocoder.GeoCoderConstants.PELIAS_UPDATE_START;


/**
 * Fetch data file as listed on: <a href="https://download.geofabrik.de/europe/norway.html">...</a>
 * This is expected to be https://download.geofabrik.de/europe/norway-latest.osm.pbf
 *
 * <p>
 * The MD5 sum is found by adding <code>.md5</code> to the URL
 * </p>
 * <p>
 *     <ul>
 *         <li>download pbf</li>
 *         <li>give to blob store route</li>
 *     </ul>
 * </p>
 */
@Component
public class FetchOsmRouteBuilder extends BaseRouteBuilder {

    private static final String NEED_TO_REFETCH = "needToRefetchMapFile";

    private static final String FINISHED = "FINISHED";

    /**
     * One time per 24H on MON-FRI
     */
    @Value("${fetch.osm.cron.schedule:0+*+*/23+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${fetch.osm.map.url:https://download.geofabrik.de/europe/norway-latest.osm.pbf}")
    private String osmMapUrl;

    /**
     * Into which subdirectory should the map be stored. The default is osm.
     */
    @Value("${osm.pbf.blobstore.subdirectory:osm}")
    private String blobStoreSubdirectoryForOsm;

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(KakkaException.class)
                .log(LoggingLevel.ERROR, "Failed while fetching OSM file.")
                .handled(true);

        singletonFrom("quartz://kakka/fetchOsmMap?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{osm.download.autoStartup:true}}")
                .filter(e -> shouldQuartzRouteTrigger(e,cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers fetch of OSM map over Norway.")
                .to("direct:considerToFetchOsmMapOverNorway")
                .log(LoggingLevel.INFO, "Quartz processing done.")
                .routeId("osm-trigger-fetching");


        from("direct:fetchOsmMapOverNorway")
                .log(LoggingLevel.DEBUG, "Fetching OSM map over Norway.")
                .to("direct:fetchOsmMapOverNorwayMd5")
                // Storing the MD5
                .convertBodyTo(InputStream.class)
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForOsm + "/" + "norway-latest.osm.pbf.md5"))
                .to("direct:uploadBlob")
                // Fetch the actual file
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.GET))
                .streamCaching()
                .to(osmMapUrl)
                .convertBodyTo(InputStream.class)
                .process(p -> {
                    // Throw exception if the expected MD5 does not match MD5 from body
                    InputStream body = (InputStream) p.getIn().getBody();
                    String md5 = DigestUtils.md5Hex(body);
                    String md5FromFile = (String) p.getIn().getHeader(Constants.FILE_TARGET_MD5);
                    if (!md5.equals(md5FromFile)) {
                        throw new Md5ChecksumValidationException("MD5 of body (" + md5 + ") does not match MD5 which was read from source (" + md5FromFile + ").");
                    }
                })
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForOsm + "/" + "norway-latest.osm.pbf"))
                .to("direct:uploadBlob")
                .setBody(simple("File fetched, and blob store has been correctly updated"))
                .setHeader(FINISHED, constant("true"))
                .log(LoggingLevel.INFO, "Map was updated, therefore triggering Geocoder POI update")
                .setBody(constant(PELIAS_UPDATE_START))
                .to(ExchangePattern.InOnly,"direct:geoCoderStart")
                .log(LoggingLevel.DEBUG, "Processing of OSM map finished")
                .routeId("osm-fetch-map");

        from("direct:fetchOsmMapOverNorwayMd5")
                .log(LoggingLevel.DEBUG, "Fetching MD5 sum for map over Norway")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.GET))
                .to(osmMapUrl + ".md5")
                .convertBodyTo(String.class)
                .process(p -> {
                    String body = (String) p.getIn().getBody();
                    String md5 = body.split(" ")[0];
                    p.getMessage().setHeader(Constants.FILE_TARGET_MD5, md5);
                    p.getMessage().setBody(body);
                })
                .log(LoggingLevel.DEBUG, getClass().getName(), "MD5 sum fetched and set in header")
                .routeId("osm-fetch-md5sum");

        from("direct:considerToFetchOsmMapOverNorway")
                .log(LoggingLevel.DEBUG, "Route which figures out whether to fetch OSM map or not")
                .to("direct:fetchOsmMapOverNorwayMd5")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForOsm + "/" + "norway-latest.osm.pbf.md5"))
                .to("direct:getBlob")
                .convertBodyTo(String.class)
                .process(p -> {
                    String md5 = (String) p.getIn().getBody();
                    if (md5 == null || md5.isEmpty()) {
                        md5 = "flag that we need to fetch the data, as the file did not exist";
                    }
                    md5 = md5.split(" ")[0];
                    String md5FromHead = (String) p.getIn().getHeader(Constants.FILE_TARGET_MD5);
                    p.getIn().setHeader(NEED_TO_REFETCH, Boolean.toString(!md5.equals(md5FromHead)));
                })
                .choice()
                .when(header(NEED_TO_REFETCH).isEqualTo("false"))
                .log(LoggingLevel.INFO, "There is no update of the map file. No need to fetch external file")
                .setBody(simple("No need to updated the map file, as the MD5 sum has not changed"))
                .otherwise()
                .log(LoggingLevel.INFO, "Need to update the map file. Calling the update map route")
                .to(ExchangePattern.InOnly,"direct:fetchOsmMapOverNorway")
                .setBody(simple("Need to fetch map file. Called update map route"))
                .end()
                .routeId("osm-check-for-newer-map");


    }

}
