package com.horairesync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point of the HoraireSync REST API. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class HoraireSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(HoraireSyncApplication.class, args);
    }
}
