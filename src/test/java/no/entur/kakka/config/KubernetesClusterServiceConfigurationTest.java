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

package no.entur.kakka.config;

import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.kubernetes.cluster.KubernetesClusterService;
import org.apache.camel.component.kubernetes.springboot.cluster.KubernetesClusterServiceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Leader election for the singleton routes runs on the Kubernetes cluster service in the cluster,
 * while the integration tests use the file-lock one. This checks the production setup instead:
 * the auto-configuration is on the classpath and the Helm configmap properties create the service.
 */
class KubernetesClusterServiceConfigurationTest {

    private static final Path HELM_CONFIGMAP = Path.of("helm/kakka/templates/configmap.yaml");

    @Test
    void autoConfigurationIsRegistered() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(KubernetesClusterServiceAutoConfiguration.class.getName());
    }

    @Test
    void helmConfigmapCreatesKubernetesClusterService() throws IOException {
        String[] properties = Files.readAllLines(HELM_CONFIGMAP).stream()
                .map(String::strip)
                .filter(line -> line.startsWith("camel.cluster.kubernetes."))
                .toArray(String[]::new);
        assertThat(properties).contains("camel.cluster.kubernetes.enabled=true");

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KubernetesClusterServiceAutoConfiguration.class))
                .withPropertyValues(properties)
                .run(context -> {
                    assertThat(context).hasSingleBean(CamelClusterService.class);
                    assertThat(context.getBean(CamelClusterService.class))
                            .isInstanceOfSatisfying(KubernetesClusterService.class, service -> {
                                assertThat(service.getConfigMapName()).isEqualTo("kakka-leaders");
                                assertThat(service.getClusterLabels()).containsEntry("app", "kakka");
                            });
                });
    }
}
