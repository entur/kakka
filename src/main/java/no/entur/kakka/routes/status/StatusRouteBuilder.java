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

package no.entur.kakka.routes.status;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StatusRouteBuilder extends RouteBuilder {

    @Value("${nabu.job.event.topic}")
    private String jobEventQueue;

    @Override
    public void configure() throws Exception {
        from("direct:updateStatus")
                .log(LoggingLevel.INFO, getClass().getName(), "Sending off job status event: ${body}")
                .to(jobEventQueue)
                .routeId("update-status").startupOrder(1);
    }


}
