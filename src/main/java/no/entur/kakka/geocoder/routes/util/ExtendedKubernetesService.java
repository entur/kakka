package no.entur.kakka.geocoder.routes.util;


import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.api.model.batch.v1beta1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1beta1.CronJobSpec;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExtendedKubernetesService {
    private static final Logger log = LoggerFactory.getLogger(ExtendedKubernetesService.class);

    private static final String COMPLETE = "Complete";

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
        Integer noOfAvailableReplicas = deployment.getStatus().getAvailableReplicas();
        return noOfAvailableReplicas == null ? 0 : noOfAvailableReplicas;

    }

    public void startESDataUploadJob() {
        //TODO: Current Kubernetes version 1.20 does not support manually created jobs, there manually deleting previously completed jobs.
        log.info("Deleting previously completed jobs.");
        deleteCompletedJobs();

        CronJobSpec specTemplate = getCronJobSpecTemplate(kubernetesClient);
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(Date.from(Instant.now()));
        String jobName = esDataUploadCronJobName + '-' + timestamp;

        log.info("Creating es build job with name {} ", jobName);
        Job job = buildJobFromCronJobSpecTemplate(specTemplate, jobName);
        kubernetesClient.batch().v1().jobs().inNamespace(kubernetesNamespace).create(job);
    }

    protected CronJobSpec getCronJobSpecTemplate(KubernetesClient client) {
        CronJob matchingJob = client.batch().v1beta1().cronjobs().inNamespace(kubernetesNamespace).withName(esDataUploadCronJobName).get();
        if (matchingJob == null) {
            throw new RuntimeException("Job with label=" + esDataUploadCronJobName + " not found in namespace " + kubernetesNamespace);
        }

        return matchingJob.getSpec();
    }

    protected Job buildJobFromCronJobSpecTemplate(CronJobSpec specTemplate, String jobName) {

        JobSpec jobSpec = specTemplate.getJobTemplate().getSpec();
        Map<String,String> labels= new HashMap<>();
        labels.put("app",esDataUploadCronJobName);

        return new JobBuilder()
                .withSpec(jobSpec).
                withNewMetadata()
                .withName(jobName)
                .withLabels(labels)
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
    }

    private void deleteCompletedJobs() {
        try {
            final JobList jobList = kubernetesClient.batch().v1().jobs().inNamespace(kubernetesNamespace).withLabel("app",esDataUploadCronJobName).list();
            var completedJobs =jobList.getItems().stream()
                    .filter(job -> job.getStatus() != null && job.getStatus().getConditions().stream().anyMatch(jobCondition -> jobCondition.getType().equals(COMPLETE)))
                    .collect(Collectors.toList());
            log.info("Delete {} completed es-build-upload jobs", completedJobs.size());
            kubernetesClient.batch().v1().jobs().delete(completedJobs);
        } catch (Exception e) {
            log.warn("Error while deleting completed jobs; {}", e.getMessage());
        }
    }

}