package no.entur.kakka.geocoder.routes.util;


import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import no.entur.kakka.Constants;
import no.entur.kakka.exceptions.KakkaException;
import org.apache.camel.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ExtendedKubernetesService {
    private static final Logger log = LoggerFactory.getLogger(ExtendedKubernetesService.class);

    private static final String COMPLETE = "Complete";

    private final KubernetesClient kubernetesClient;

    @Value("${kakka.remote.kubernetes.namespace:default}")
    private String kubernetesNamespace;

    @Value("${kakka.remote.kubernetes.cronjob:es-build-job}")
    private String esDataUploadCronJobName;

    @Value("${kakka.remote.kubernetes.geocoder.cronjob:geocoder-acceptance-tests}")
    private String geoCoderSmokeTestCronJobName;

    @Value("${elasticsearch.scratch.deployment.name:es-scratch}")
    private String elasticsearchScratchDeploymentName;

    @Value("${elasticsearch.scratch.upload.job.enabled:true}")
    private boolean elasticsearchUploadJobEnabled;

    @Value("${geocoder.smoke.test.job.enabled:true}")
    private boolean geoCodeSmokeTestJobEnabled;

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
        var deployment = kubernetesClient.apps().deployments().withName(elasticsearchScratchDeploymentName).get();
        Integer noOfAvailableReplicas = deployment.getStatus().getAvailableReplicas();
        return noOfAvailableReplicas == null ? 0 : noOfAvailableReplicas;

    }

    public void startESDataUploadJob() {
        if (elasticsearchUploadJobEnabled) {
            creatCronJob(esDataUploadCronJobName);
        } else
        {
            log.info("elasticsearchUploadJobEnabled is set to false, not running upload job");
        }

    }

    public void rolloutDeployment(@Header(Constants.DEPLOYMENT_NAME) String deploymentName){
        try(kubernetesClient) {
            kubernetesClient.apps().deployments().inNamespace(kubernetesNamespace).withName(deploymentName)
                    .rolling()
                    .restart();
        } catch (Exception ex) {
            throw new KakkaException("Failed to redeploy: " + deploymentName, ex);
        }
    }

    public void startGeoCoderSmokeTestJob() {
        if (geoCodeSmokeTestJobEnabled) {
           creatCronJob(geoCoderSmokeTestCronJobName);
        } else {
            log.info("geoCodeSmokeTestJobEnabled is set to false, not running geocoder smoke tests");
        }

    }

    private void creatCronJob(String cronJobName) {
        CronJob cronJob = kubernetesClient.batch().v1().cronjobs().inNamespace(kubernetesNamespace).withName(cronJobName).get();
        if (cronJob == null) {
            throw new RuntimeException("Job with label=" + cronJobName + " not found in namespace " + kubernetesNamespace);
        }
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(Date.from(Instant.now()));
        String jobName = cronJobName + '-' + timestamp;

        log.info("Creating es build job with name {} ", jobName);
        Job job = buildJobFromCronJobSpecTemplate(cronJob, jobName,cronJobName);
        kubernetesClient.batch().v1().jobs().inNamespace(kubernetesNamespace).create(job);


    }

    private Job buildJobFromCronJobSpecTemplate(CronJob cronJob, String jobName, String cronJobName) {
        JobSpec jobSpec = cronJob.getSpec().getJobTemplate().getSpec();
        Map<String,String> labels= new HashMap<>();
        labels.put("app",cronJobName);

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

}