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

package no.entur.kakka.geocoder.routes.tiamat;


import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.routes.tiamat.xml.ExportJob;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.entur.kakka.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.entur.kakka.geocoder.GeoCoderConstants.GEOCODER_NEXT_TASK;
import static no.entur.kakka.geocoder.GeoCoderConstants.TIAMAT_EXPORT_POLL;

/**
 * Common functionality shared between tiamat exports.
 */
@Component
public class TiamatExportRouteBuilder extends BaseRouteBuilder {


    @Value("${tiamat-exporter.url}")
    private String tiamatUrl;

    @Value("${tiamat.publication.delivery.path:/services/stop_places/netex}")
    private String tiamatPublicationDeliveryPath;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:tiamatExport")
                .log(LoggingLevel.DEBUG, "Start Tiamat export")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_QUERY,header(Constants.QUERY_STRING))
                .to(tiamatUrl + tiamatPublicationDeliveryPath + "/export/initiate/")
                .convertBodyTo(ExportJob.class)
                .setHeader(Constants.JOB_ID, simple("${body.id}"))
                .setHeader(Constants.JOB_URL, simple(tiamatPublicationDeliveryPath + "/${body.jobUrl}"))
                .log(LoggingLevel.INFO, "Started Tiamat export of file: ${body.fileName}")
                .setProperty(GEOCODER_NEXT_TASK, constant(TIAMAT_EXPORT_POLL))
                .end()
                .routeId("tiamat-export");

        from("direct:tiamatExportMoveFileToBlobStore")
                .to("direct:tiamatExportDownloadFile")
                .to("direct:uploadBlob")
                .routeId("tiamat-export-move-file");

        from("direct:tiamatExportDownloadFile")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching tiamat export file ...")

                .toD(tiamatUrl + "/${header." + Constants.JOB_URL + "}/content")
                .routeId("tiamat-export-download-file");


        from("direct:tiamatExportUploadFile")
                .to("direct:uploadBlob")
                .routeId("tiamat-export-upload-file");

        from("direct:tiamatExportUploadFileExternal")
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
                .to("direct:copyBlob")
                .routeId("tiamat-export-upload-file-external");

        from("direct:kinguExportUploadFileExternal")
                .log(LoggingLevel.INFO,"kingu export upload external")
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
                .to("direct:copyKinguBlob")
                .routeId("kingu-export-upload-file-external");

    }


}
