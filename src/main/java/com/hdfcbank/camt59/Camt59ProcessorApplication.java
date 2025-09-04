package com.hdfcbank.camt59;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.hdfcbank"})
public class Camt59ProcessorApplication {

	public static void main(String[] args) {
		SpringApplication.run(Camt59ProcessorApplication.class, args);
	}

}
