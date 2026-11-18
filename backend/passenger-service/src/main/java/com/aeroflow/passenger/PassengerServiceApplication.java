package com.aeroflow.passenger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.aeroflow")
public class PassengerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PassengerServiceApplication.class, args);
    }
}
