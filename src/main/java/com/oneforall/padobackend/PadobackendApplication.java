package com.oneforall.padobackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PadobackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PadobackendApplication.class, args);
	}

}
