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

package no.entur.kakka.routes.etcd;

import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.routes.etcd.json.EtcdResponse;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.http.common.HttpMethods;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static no.entur.kakka.Utils.getHttp4;


/**
 * Get/ set values in etcd. Not using camel-etcd because timeout does not work (hangs indefinitely) with underlying etcd4j lib.
 */
@Component
@ConditionalOnProperty(name = "etcd.in.memory", havingValue = "false", matchIfMissing = true)
public class EtcdRouteBuilder extends BaseRouteBuilder {

    @Value("${etcd.url:http://etcd-client:2379}")
    private String etcdUrl;


    @Override
    public void configure() throws Exception {

        from("direct:getEtcdValue")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .doTry()
                .toD(getHttp4(etcdUrl) + "${header." + Constants.ETCD_KEY + "}")
                .unmarshal().json(JsonLibrary.Jackson, EtcdResponse.class)

                .process(e -> e.getIn().setBody(e.getIn().getBody(EtcdResponse.class).node.value))
                .doCatch(HttpOperationFailedException.class).onWhen(exchange -> {
            HttpOperationFailedException ex = exchange.getException(HttpOperationFailedException.class);
            return (ex.getStatusCode() == 404);
        })
                .log(LoggingLevel.INFO, "No value found in etcd for key: ${header." + Constants.ETCD_KEY + "}. Returning null")
                .setBody(constant(null))
                .end()

                .routeId("etcd-get-value");

        from("direct:setEtcdValue")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.PUT))
                .setProperty("etcdUrl", simple(getHttp4(etcdUrl) + "${header." + Constants.ETCD_KEY + "}?value=${body}"))
                .setBody(constant(null))
                .toD("${exchangeProperty.etcdUrl}")
                .routeId("set-sync-status-until");
    }
}
