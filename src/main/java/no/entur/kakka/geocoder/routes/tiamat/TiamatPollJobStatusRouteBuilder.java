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
import no.entur.kakka.geocoder.routes.tiamat.xml.JobStatus;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.entur.kakka.geocoder.GeoCoderConstants.GEOCODER_RESCHEDULE_TASK;
import static no.entur.kakka.geocoder.GeoCoderConstants.TIAMAT_EXPORT_POLL;

@Component
public class TiamatPollJobStatusRouteBuilder extends BaseRouteBuilder {

    public static final Logger logger= LoggerFactory.getLogger(TiamatPollJobStatusRouteBuilder.class);

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        from(TIAMAT_EXPORT_POLL.getEndpoint())
                .validate(header(Constants.JOB_ID).isNotNull())
                .validate(header(Constants.JOB_URL).isNotNull())
                .validate(header(Constants.JOB_STATUS_ROUTING_DESTINATION).isNotNull())
                .to("direct:checkTiamatJobStatus")
                .routeId("tiamat-validate-job-status-parameters");

        from("direct:checkTiamatJobStatus")
                .removeHeaders("CamelHttp*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.GET))
                .doTry()
                .toD(tiamatUrl + "${header." + Constants.JOB_URL + "}/status")
                .convertBodyTo(ExportJob.class)
                .setHeader("current_status", simple("${body.status}"))
                .doCatch(HttpOperationFailedException.class).onWhen(exchange -> {
                    HttpOperationFailedException ex = exchange.getException(HttpOperationFailedException.class);
                    logger.debug("Got some exception : " + ex.getStatusCode());
                    return (
                            ex.getStatusCode() == 404);
                })
                    .log(LoggingLevel.WARN, correlation() + "got 404 from Tiamat. Something is wrong... giving up")
                    .setHeader("current_status", constant(JobStatus.FAILED.toString()))
                .end()
                .choice()
                .when(simple("${header.current_status} != '" + JobStatus.PROCESSING + "'"))
                .to("direct:tiamatJobStatusDone")
                .otherwise()
                .setProperty(GEOCODER_RESCHEDULE_TASK, constant(true))
                .end()
                .routeId("tiamat-get-job-status");


        from("direct:tiamatJobStatusDone")
                .log(LoggingLevel.DEBUG, correlation() + " exited retry loop with status ${header.current_status}")
                .choice()
                .when(simple("${header.current_status} == '" + JobStatus.FAILED + "'"))
                .log(LoggingLevel.WARN, correlation() + " ended in state ${header.current_status}. Not rescheduling.")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .otherwise()
                .toD("${header." + Constants.JOB_STATUS_ROUTING_DESTINATION + "}")
                .end()
                .routeId("tiamat-process-job-status-done");
    }


    protected String correlation() {
        return "TiamatExport [id:${header." + Constants.JOB_ID + "}] ";
    }
}
