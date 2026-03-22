package org.ganjp.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GjpApiAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(GjpApiAdminApplication.class, args);
    }
}
