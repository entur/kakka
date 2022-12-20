package no.entur.kakka.geocoder.routes.util;


import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@EnableRuleMigrationSupport
class ExtendedKubernetesServiceTest {

    private static final String ES_DATA_PATH = "ES_DATA_PATH" ;
    private static final String ES_DATA_FILE_NAME = "data/es-data-20221208093933.tar.gz";
    @Rule
    public KubernetesServer server = new KubernetesServer();

    public KubernetesClient client = new DefaultKubernetesClient();


    @Test
    @DisplayName("Should test env variable in a container")
    void testContainerEnvVariables() throws FileNotFoundException {
        CronJob cronJob = client.batch().v1().cronjobs().load(new FileInputStream("src/test/resources/no/entur/kakka/geocoder/routes/util/cronjob2.yml")).get();
        final Job job = buildJobFromCronJobSpecTemplate(cronJob, "geocoder-job", getEnvVars(ES_DATA_FILE_NAME));

        Assertions.assertEquals("geocoder-acceptance-tests-predeploy",cronJob.getMetadata().getName());
        Assertions.assertEquals("geocoder-job",job.getMetadata().getName());

        final List<Container> containers = getContainers(job);
        Assertions.assertFalse(containers.isEmpty());
        Assertions.assertTrue(containers.stream().anyMatch(c -> c.getName().equals("elasticsearch")));
        Assertions.assertEquals(2,getContainerEnvVars(containers,"elasticsearch").size());
        Assertions.assertTrue(getContainerEnvVars(containers,"elasticsearch").stream().anyMatch(envVar -> envVar.getName().equals(ES_DATA_PATH)));
        Assertions.assertTrue(getContainerEnvVars(containers,"elasticsearch").stream().anyMatch(envVar -> envVar.getValue().equals(ES_DATA_FILE_NAME)));

    }



    private Job buildJobFromCronJobSpecTemplate(CronJob cronJob, String jobName, List<EnvVar> envVars) {
        JobSpec jobSpec = cronJob.getSpec().getJobTemplate().getSpec();
        Map<String,String> labels= new HashMap<>();
        labels.put("app",cronJob.getMetadata().getName());

        return new JobBuilder()
                .withSpec(jobSpec).
                withNewMetadata()
                .withName(jobName)
                .withLabels(labels)
                .endMetadata()
                .editOrNewSpec()
                .editTemplate()
                .editSpec()
                .editMatchingContainer(containerBuilder -> containerBuilder.getName().equals("elasticsearch"))
                .addAllToEnv(envVars)
                .endContainer()
                .editMatchingContainer(containerBuilder -> containerBuilder.getName().equals("geocoder-acceptance-tests"))
                .addAllToEnv(envVars)
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    private List<EnvVar> getEnvVars(String esDataFileName) {
        return List.of(
                new EnvVar(ES_DATA_PATH, esDataFileName, null));
    }

    private List<Container> getContainers(Job job) {
        return job.getSpec().getTemplate().getSpec().getContainers();
    }

    private List<EnvVar> getContainerEnvVars(List<Container> containers, String containerName){
        final Optional<Container> container = containers.stream().filter(c -> c.getName().equals(containerName)).findFirst();
        return container.map(Container::getEnv).orElse(Collections.emptyList());
    }

}