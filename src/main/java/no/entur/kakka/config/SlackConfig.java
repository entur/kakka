package no.entur.kakka.config;

import org.rutebanken.helper.slack.SlackPostService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackConfig {
    @Bean
    SlackPostService slackPostService( @Value("${kakka.slack.end.point}")String slackEndpoint) {
        return new SlackPostService(slackEndpoint);
    }

}
