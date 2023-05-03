package com.cegeka.vconsult.fhir.server;

import be.cegeka.vconsult.security.api.EnableSecurityLib;
import be.cegeka.vconsult.security.test.EnableMockSecurityLib;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan
@EnableMockSecurityLib // TODO VO-12583
public class FhirServerConfiguration {
}
