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

package no.entur.kakka.task;

import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.TestApp;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.file.cluster.FileLockClusterService;
import org.apache.camel.component.master.MasterConsumer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.cluster.ClusterServiceHelper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Singleton routes (camel-master) and the quartz schedules that run on them.
 * The kingu export cron fires every second here so the schedule can be observed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class, properties = {
        "kingu.export.autoStartup=true",
        "tiamat.publish.export.cron.schedule=0/1+*+*+*+*+?"
})
class SingletonRouteIntegrationTest extends KakkaRouteBuilderIntegrationTestBase {

    private static final Duration LEADER_TIMEOUT = Duration.ofSeconds(60);

    @EndpointInject("mock:startNetexExport")
    protected MockEndpoint startNetexExport;

    @Test
    void singletonRoutesBecomeLeader() throws Exception {
        context.start();

        assertThat(ClusterServiceHelper.lookupService(context))
                .get()
                .isInstanceOf(FileLockClusterService.class);

        await().atMost(LEADER_TIMEOUT).until(() -> isLeader("task-main-route"));
        await().atMost(LEADER_TIMEOUT).until(() -> isLeader("kingu-publish-export-quartz"));
    }

    @Test
    void quartzScheduleTriggersExportOnLeader() throws Exception {
        AdviceWith.adviceWith(context, "kingu-publish-export-quartz",
                a -> a.weaveByToUri("direct:startNetexExport").replace().to("mock:startNetexExport"));
        startNetexExport.expectedMinimumMessageCount(1);
        startNetexExport.setResultWaitTime(LEADER_TIMEOUT.toMillis());

        context.start();

        startNetexExport.assertIsSatisfied();
    }

    private boolean isLeader(String routeId) {
        return context.getRoute(routeId).getConsumer() instanceof MasterConsumer consumer && consumer.isMaster();
    }
}
