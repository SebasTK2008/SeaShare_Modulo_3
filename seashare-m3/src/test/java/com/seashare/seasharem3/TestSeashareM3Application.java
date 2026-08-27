package com.seashare.seasharem3;

import org.springframework.boot.SpringApplication;

public class TestSeashareM3Application {

	public static void main(String[] args) {
		SpringApplication.from(SeashareM3Application::main).with(TestcontainersConfiguration.class).run(args);
	}

}
