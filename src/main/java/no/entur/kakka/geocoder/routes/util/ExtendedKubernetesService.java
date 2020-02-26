package no.entur.kakka.geocoder.routes.util;

import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.CronJobSpec;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.JobSpec;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class ExtendedKubernetesService {
    private static final Logger log = LoggerFactory.getLogger(ExtendedKubernetesService.class);
    private final KubernetesClient kubernetesClient;

    @Value("${kakka.remote.kubernetes.namespace:default}")
    private String kubernetesNamespace;

    @Value("${kakka.remote.kubernetes.cronjob:es-build-job}")
    private String esDataUploadCronJobName;

    @Value("${elasticsearch.scratch.deployment.name:es-scratch}")
    private String elasticsearchScratchDeploymentName;

    public ExtendedKubernetesService() {
        this.kubernetesClient = new DefaultKubernetesClient();
    }

    private void scaleDeployment(int noOfReplicas) {
        kubernetesClient.apps().deployments().inNamespace(kubernetesNamespace).withName(elasticsearchScratchDeploymentName).scale(noOfReplicas, true);
    }

    public void scaleUpDeployment() {
        log.info("Scaling up es-scratch");
        scaleDeployment(1);

    }

    public void scaleDownDeployment() {
        log.info("Scaling down es-scratch");
         scaleDeployment(0);
    }

    public int getNoOfAvailableReplicas() {
        var deployment = kubernetesClient.apps().deployments().withName("es-scratch").get();
        Integer noOfAvailableReplicas= deployment.getStatus().getAvailableReplicas();
        return noOfAvailableReplicas == null ? 0 : noOfAvailableReplicas;

    }

    public void startESDataUploadJob() {
        CronJobSpec specTemplate = getCronJobSpecTemplate(kubernetesClient);
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(Date.from(Instant.now()));
        String jobName = esDataUploadCronJobName + '-' + timestamp;

        log.info("Creating es build job with name {} ", jobName);
        Job job = buildJobFromCronJobSpecTemplate(specTemplate, jobName);
        kubernetesClient.batch().jobs().inNamespace(kubernetesNamespace).create(job);
    }

    protected CronJobSpec getCronJobSpecTemplate(KubernetesClient client) {
        List<CronJob> matchingJobs = client.batch().cronjobs().inNamespace(kubernetesNamespace).withLabel("app", esDataUploadCronJobName).list().getItems();
        if (matchingJobs.isEmpty()) {
            throw new RuntimeException("Job with label=" + esDataUploadCronJobName + " not found in namespace " + kubernetesNamespace);
        }
        if (matchingJobs.size() > 1) {
            throw new RuntimeException("Found multiple jobs matching label app=" + esDataUploadCronJobName + " in namespace " + kubernetesNamespace);
        }
        return matchingJobs.get(0).getSpec();
    }

    protected Job buildJobFromCronJobSpecTemplate(CronJobSpec specTemplate, String jobName) {

        JobSpec jobSpec = specTemplate.getJobTemplate().getSpec();

        Job job = new JobBuilder()
                .withSpec(jobSpec).
                        withNewMetadata()
                .withName(jobName)
                .endMetadata()
                .editOrNewSpec()
                .editTemplate()
                .editSpec()
                .editFirstContainer()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        return job;

    }

}