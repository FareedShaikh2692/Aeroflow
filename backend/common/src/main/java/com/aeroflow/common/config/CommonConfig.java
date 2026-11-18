package com.aeroflow.common.config;

import com.aeroflow.common.events.Topics;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;

/** Shared wiring picked up by component scanning in every service (base package com.aeroflow). */
@Configuration
@EnableScheduling
@EntityScan("com.aeroflow")
@EnableJpaRepositories("com.aeroflow")
public class CommonConfig {

    @Bean
    KafkaAdmin.NewTopics aeroflowTopics(@Value("${aeroflow.events.topic-partitions:3}") int partitions,
                                        @Value("${aeroflow.events.topic-replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(Topics.ALL.stream()
                .map(t -> TopicBuilder.name(t).partitions(partitions).replicas(replicas).build())
                .toArray(NewTopic[]::new));
    }

    /** Retry with backoff, then park poison messages on <topic>.DLT. */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxElapsedTime(10_000);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    java.time.Clock clock() {
        return java.time.Clock.systemUTC();
    }

    @Bean
    OpenAPI aeroflowOpenApi(@Value("${spring.application.name}") String name) {
        return new OpenAPI()
                .info(new Info().title("AeroFlow " + name + " API").version("1.0.0")
                        .description("AeroFlow Airline Operations & Passenger Management Platform"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
