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

package no.entur.kakka.routes.blobstore;

import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

@Component
public class BlobStoreRoute extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {

        from("direct:uploadBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(header(Constants.BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                .setHeader(Constants.BLOBSTORE_MAKE_BLOB_PUBLIC, constant(false))     //defaulting to false if not specified
                .end()
                .bean("blobStoreService", "uploadBlob")
                .setBody(simple(""))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, "Stored file ${header." + Constants.FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-upload");

        from("direct:getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .bean("blobStoreService", "getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, "Returning from fetching file ${header." + Constants.FILE_HANDLE + "} from blob store.")
                .routeId("blobstore-download");


        from("direct:copyBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(header(Constants.BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                .setHeader(Constants.BLOBSTORE_MAKE_BLOB_PUBLIC, constant(false))     //defaulting to false if not specified
                .end()
                .bean("blobStoreService", "copyBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO,  "Returning from copying file ${header." + Constants.FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-copy");


    }
}
