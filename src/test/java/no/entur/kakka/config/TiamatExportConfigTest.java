package no.entur.kakka.config;

import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TiamatExportConfigTest {

    @Autowired
    TiamatExportConfig tiamatExportConfig;

    @Test
    public void getProperties() {
        final List<ExportParams> exportJobs = tiamatExportConfig.exportJobs;

        assertEquals(exportJobs.size(),10);

    }
}