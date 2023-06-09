package com.cegeka.vconsult.fhir.server;

import be.cegeka.vconsult.security.api.EnableSecurityLib;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@ConditionalOnProperty(
	value="securitylib.load.production",
	havingValue = "true",
	matchIfMissing = true)
@ComponentScan
@EnableSecurityLib
public class FhirServerConfiguration {

}
