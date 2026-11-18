package com.aeroflow.allinone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Hosts all seven domain services in one JVM and one Spring context, for free-tier hosting.
 * Module boundaries are unchanged: each service keeps its own schema, migrations, outbox and event
 * subscribers, and they still talk through the event bus and the internal REST API (over localhost).
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.aeroflow",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootApplication.class))
public class AllInOneApplication {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(AllInOneApplication.class, args);
    }
}
