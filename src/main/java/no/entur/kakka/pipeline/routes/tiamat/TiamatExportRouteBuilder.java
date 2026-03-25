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

package no.entur.kakka.pipeline.routes.tiamat;

import no.entur.kakka.pipeline.BaseRouteBuilder;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Route for triggering and publishing Tiamat export.
 */
@Component
public class TiamatExportRouteBuilder extends BaseRouteBuilder {

    @Value("${tiamat.export.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;
    @Value("${tiamat.export.cron.schedule.mid.day:0+0+14+?+*+MON-FRI}")
    private String cronScheduleMidDay;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz://kakka/tiamatExport?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{tiamat.export.autoStartup:false}}")
                .filter(e -> shouldQuartzRouteTrigger(e,cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers netex export.")
                .to(ExchangePattern.InOnly, "direct:startNetexExport")
                .routeId("tiamat-export-quartz");

        singletonFrom("quartz://kakka/tiamatExportMidDay?cron=" + cronScheduleMidDay + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{tiamat.export.mid.day.autoStartup:false}}")
                .filter(e -> shouldQuartzRouteTrigger(e,cronScheduleMidDay))
                .log(LoggingLevel.INFO, "Quartz triggers mid day netex export.")
                .to(ExchangePattern.InOnly,"direct:startNetexExport")
                .routeId("tiamat-export-mid-day-quartz");
    }
}
