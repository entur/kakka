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

package no.entur.kakka;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import no.entur.kakka.config.GcsStorageConfig;
import no.entur.kakka.config.IdempotentRepositoryConfig;
import no.entur.kakka.config.TransactionManagerConfig;
import org.apache.camel.builder.RouteBuilder;
import org.entur.pubsub.camel.config.GooglePubSubCamelComponentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.EnumSet;
import java.util.Set;

/**
 * A spring-boot application that includes a Camel route builder to setup the Camel routes
 */
@SpringBootApplication
@EnableScheduling
@Import({GcsStorageConfig.class, TransactionManagerConfig.class, IdempotentRepositoryConfig.class, GooglePubSubCamelComponentConfig.class})
public class App extends RouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(App.class);
    @Value("${shutdown.timeout:300}")
    private Long shutdownTimeout;

    // must have a main method spring-boot can run
    public static void main(String... args) {
        logger.info("Starting Kakka...");

        configureJsonPath();

        SpringApplication.run(App.class, args);
    }

    private static void configureJsonPath() {
        Configuration.setDefaults(new Configuration.Defaults() {

            private final JsonProvider jsonProvider = new JacksonJsonProvider();
            private final MappingProvider mappingProvider = new JacksonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });
    }

    @Override
    public void configure() throws Exception {
        getContext().getShutdownStrategy().setTimeout(shutdownTimeout);
        getContext().setUseMDCLogging(true);
    }

}
