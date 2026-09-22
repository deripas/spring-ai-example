package com.girhub.deripas.ai.example;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(PGVECTOR_IMAGE)
                .withDatabaseName("ragdb")
                .withUsername("postgres")
                .withPassword("postgres")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("postgres/scripts/01-create-tables.sql"),
                        "/docker-entrypoint-initdb.d/01-create-tables.sql");
    }
}
