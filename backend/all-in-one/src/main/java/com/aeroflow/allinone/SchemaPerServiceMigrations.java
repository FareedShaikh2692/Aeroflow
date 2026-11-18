package com.aeroflow.allinone;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs each service's own Flyway migrations into its own schema (auth, flight, ...), before JPA starts.
 * Connections use search_path = all service schemas, so unqualified SQL in each module still resolves.
 */
@Configuration
public class SchemaPerServiceMigrations {
    static final Map<String, String> SCHEMAS = new LinkedHashMap<>();
    static {
        for (String s : new String[]{"auth", "flight", "passenger", "booking", "checkin", "notification", "reporting"}) {
            SCHEMAS.put(s, "classpath:db/migration/" + s + "-service");
        }
    }

    @Bean
    Migrator schemaMigrator(DataSource dataSource) {
        return new Migrator(dataSource);
    }

    /** JPA must not initialise before the schemas exist. */
    @Bean
    static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnMigrator() {
        return new EntityManagerFactoryDependsOnPostProcessor("schemaMigrator");
    }

    static class Migrator implements InitializingBean {
        private static final Logger log = LoggerFactory.getLogger(Migrator.class);
        private final DataSource dataSource;

        Migrator(DataSource dataSource) { this.dataSource = dataSource; }

        @Override
        public void afterPropertiesSet() {
            SCHEMAS.forEach((schema, location) -> {
                var result = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                        .createSchemas(true).locations(location).baselineOnMigrate(true).load().migrate();
                log.info("Schema '{}': {} migration(s) applied", schema, result.migrationsExecuted);
            });
        }
    }
}
