package com.example.demo;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class SpringApplication {

	public static void main(String[] args) {

		org.springframework.boot.SpringApplication.run(SpringApplication.class, args);
	}

}
