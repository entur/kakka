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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import org.apache.camel.Exchange;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobEvent {

    public String name;
    public String correlationId;
    public Long providerId;
    public JobDomain domain;
    public Long externalId;
    public String action;
    public State state;
    public Instant eventTime;
    public String referential;

    private JobEvent() {
    }

    public static JobEvent fromString(String string) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.readValue(string, JobEvent.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder systemJobBuilder(Exchange exchange) {
        return new ExchangeStatusBuilder(exchange).initSystemJob();
    }

    public String toString() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.registerModule(new JavaTimeModule());
            mapper.writeValue(writer, this);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public enum JobDomain {GEOCODER, TIAMAT}

    public enum State {PENDING, STARTED, TIMEOUT, FAILED, OK, DUPLICATE, CANCELLED}

    public static class Builder {

        protected JobEvent jobEvent = new JobEvent();

        private Builder() {
        }

        public Builder jobDomain(JobDomain jobDomain) {
            jobEvent.domain = jobDomain;
            return this;
        }

        public Builder startGeocoder(GeoCoderTaskType action) {
            jobEvent = new JobEvent();
            newCorrelationId();
            jobDomain(JobDomain.GEOCODER);
            state(State.STARTED);
            return action(action.toString());
        }


        public Builder action(String action) {
            jobEvent.action = action;
            return this;
        }

        public Builder state(JobEvent.State state) {
            jobEvent.state = state;
            return this;
        }

        public Builder jobId(Long jobId) {
            jobEvent.externalId = jobId;
            return this;
        }

        public Builder fileName(String fileName) {
            jobEvent.name = fileName;
            return this;
        }

        public Builder newCorrelationId() {
            jobEvent.correlationId = UUID.randomUUID().toString();
            return this;
        }

        public Builder correlationId(String correlationId) {
            jobEvent.correlationId = correlationId;
            return this;
        }

        public Builder referential(String referential) {
            jobEvent.referential = referential;
            return this;
        }

        public JobEvent build() {
            if (jobEvent.correlationId == null) {
                throw new IllegalArgumentException("No correlation id");
            }

            if (jobEvent.action == null) {
                throw new IllegalArgumentException("No action");
            }

            if (jobEvent.state == null) {
                throw new IllegalArgumentException("No state");
            }
            if (jobEvent.domain == null) {
                throw new IllegalArgumentException("No job domain");
            }
            jobEvent.eventTime = Instant.now();
            return jobEvent;
        }
    }

    public static class ExchangeStatusBuilder extends Builder {

        private final Exchange exchange;

        private ExchangeStatusBuilder(Exchange exchange) {
            super();
            this.exchange = exchange;
        }

        private Builder initSystemJob() {

            String currentStatusString = exchange.getIn().getHeader(Constants.SYSTEM_STATUS, String.class);

            if (currentStatusString != null) {
                JobEvent currentJobEvent = JobEvent.fromString(currentStatusString);
                jobEvent.correlationId = currentJobEvent.correlationId;
                jobEvent.domain = currentJobEvent.domain;
                jobEvent.action = currentJobEvent.action;
                jobEvent.name = currentJobEvent.name;
                jobEvent.externalId = currentJobEvent.externalId;
            }
            return this;
        }

        @Override
        public JobEvent build() {
            if (exchange == null) {
                throw new IllegalStateException(this.getClass() + " does not hold an instance of exchange.");
            }

            JobEvent jobEvent = super.build();

            exchange.getIn().setHeader(Constants.SYSTEM_STATUS, jobEvent.toString());
            exchange.getMessage().setBody(jobEvent.toString());
            exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
            return jobEvent;
        }
    }

}
