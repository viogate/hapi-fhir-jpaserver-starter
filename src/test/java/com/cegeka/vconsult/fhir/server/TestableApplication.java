package com.cegeka.vconsult.fhir.server;

import ca.uhn.fhir.jpa.starter.Application;
import org.springframework.context.annotation.Import;

@Import({
	FhirServerTestConfiguration.class
})
public class TestableApplication extends Application {
}
