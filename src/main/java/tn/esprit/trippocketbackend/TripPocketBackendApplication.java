package tn.esprit.trippocketbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TripPocketBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripPocketBackendApplication.class, args);
    }

}
