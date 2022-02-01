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

package no.entur.kakka.routes.file;


import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.routes.file.beans.FileTypeClassifierBean;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.springframework.stereotype.Component;

import static no.entur.kakka.Constants.FILE_HANDLE;
import static no.entur.kakka.Constants.FILE_NAME;
import static no.entur.kakka.Constants.FILE_TYPE;
import static no.entur.kakka.Constants.PROVIDER_ID;


/**
 * Code is copied from marduk
 * Receives file handle, pulls file from blob store, classifies files and performs initial validation.
 */
@Component
public class FileClassificationRouteBuilder extends BaseRouteBuilder {


    @Override
    public void configure() throws Exception {
        super.configure();

        onException(ValidationException.class)
                .handled(true)
                .log(LoggingLevel.INFO, correlation() + "Could not process file ${header." + FILE_HANDLE + "}")
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .setBody(simple(""))      //remove file data from body
                .to("entur-google-pubsub:DeadLetterQueue");

        from("entur-google-pubsub:ProcessTariffZoneFileQueue")
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_TRANSFER).state(JobEvent.State.OK).build())
                .to("direct:updateStatus")
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_CLASSIFICATION).state(JobEvent.State.STARTED).build())
                .to("direct:updateStatus")
                .to("direct:getBlob")
                .convertBodyTo(byte[].class)
                .validate().method(FileTypeClassifierBean.class, "validateFile")
                .choice()

                .when(header(FILE_TYPE).isEqualTo(FileType.UNKNOWN_FILE_EXTENSION.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} does not end with a .zip or .ZIP extension")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_FILE_UNKNOWN_FILE_EXTENSION))
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.UNKNOWN_FILE_TYPE.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} cannot be processed: unknown file type")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_UNKNOWN_FILE_TYPE))
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.NOT_A_ZIP_FILE.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} is not a valid zip archive")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_FILE_NOT_A_ZIP_FILE))
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.ZIP_CONTAINS_SUBDIRECTORIES.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} contains one or more subdirectories")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_FILE_ZIP_CONTAINS_SUB_DIRECTORIES))
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.ZIP_CONTAINS_MORE_THAN_ONE_FILE.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} contains more than one file")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_FILE_ZIP_CONTAINS_SUB_DIRECTORIES))
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.INVALID_ZIP_FILE_ENTRY_CONTENT_ENCODING.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} contains one or more invalid XML files: invalid encoding")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_INVALID_XML_ENCODING))
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.INVALID_ZIP_FILE_ENTRY_XML_CONTENT.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} contains one or more invalid XML files: unparseable XML")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_INVALID_XML_CONTENT))
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.INVALID_ZIP_FILE_ENTRY_NAME_ENCODING.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} contains one or more invalid zip entry names: invalid encoding")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_INVALID_ZIP_ENTRY_ENCODING))
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.INVALID_FILE_NAME.name()))
                .log(LoggingLevel.WARN, correlation() + "File with invalid characters in file name ${header." + FILE_HANDLE + "}")
                .to("direct:sanitizeFileName")

                .otherwise()
                .log(LoggingLevel.INFO, correlation() + "Posting " + FILE_HANDLE + " ${header." + FILE_HANDLE + "} and " + FILE_TYPE + " ${header." + FILE_TYPE + "} on tiamat import queue.")
                .setBody(simple(""))   //remove file data from body since this is in blobstore
                .to("entur-google-pubsub:TiamatTariffZoneImportQueue")
                //todo leval 2 validation
                //.to("direct:antuNetexPreValidation")
                .end()
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.FILE_CLASSIFICATION).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .routeId("file-classify");

        from("direct:sanitizeFileName")
                .process(e -> {
                    String originalFileName = e.getIn().getHeader(FILE_NAME, String.class);
                    String sanitizedFileName = KakkaFileUtils.sanitizeFileName(originalFileName);
                   //todo fixme
                    e.getIn().setHeader(FILE_HANDLE,"tariffzones/"
                                                             + e.getIn().getHeader(PROVIDER_ID, Long.class)
                                                             + "/" + sanitizedFileName);
                    e.getIn().setHeader(FILE_NAME, sanitizedFileName);
                })
                .log(LoggingLevel.INFO, correlation() + "Uploading file with new file name ${header." + FILE_HANDLE + "}")
                .to("direct:uploadBlob")
                .to("entur-google-pubsub:ProcessTariffZoneFileQueue")
                .routeId("file-sanitize-filename");

        from("direct:antuNetexPreValidation")
                .filter(header(FILE_TYPE).isEqualTo(FileType.NETEXPROFILE))
                //todo fixme
                /*
                .process(e -> {
                    Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                    e.getIn().setHeader(DATASET_REFERENTIAL, provider.chouetteInfo.referential);
                })


                .setHeader(VALIDATION_STAGE_HEADER, constant(VALIDATION_STAGE_PREVALIDATION))
                .setHeader(VALIDATION_CLIENT_HEADER, constant(VALIDATION_CLIENT_MARDUK))
                .to("entur-google-pubsub:AntuNetexValidationQueue")
                 */
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.PREVALIDATION).state(JobEvent.State.PENDING).build())
                .to("direct:updateStatus")
                .routeId("antu-netex-pre-validation");
    }

}
