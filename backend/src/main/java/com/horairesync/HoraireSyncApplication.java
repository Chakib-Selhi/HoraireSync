package com.horairesync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the HoraireSync REST API. */
@SpringBootApplication
public class HoraireSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(HoraireSyncApplication.class, args);
    }
}
