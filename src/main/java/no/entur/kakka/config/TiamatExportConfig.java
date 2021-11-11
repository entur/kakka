package no.entur.kakka.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;


@PropertySource(value = "${tiamat.exports.config.path:/etc/tiamat-config/tiamat-exports.yml}", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "tiamat")
@Configuration
public class TiamatExportConfig {
    List<ExportParams> exportJobs;

    public List<ExportParams> getExportJobs() {
        return exportJobs;
    }

    public void setExportJobs(List<ExportParams> exportJobs) {
        this.exportJobs = exportJobs;
    }
}
