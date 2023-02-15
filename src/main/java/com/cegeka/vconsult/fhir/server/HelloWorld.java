package com.cegeka.vconsult.fhir.server;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class HelloWorld {
	@PostConstruct
	public void helloWorld() {
		System.out.println("Viollier says: \"Hello world\"");
	}
}
