package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import org.hl7.fhir.r4.model.Meta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class, properties =
	{
		"spring.datasource.url=jdbc:h2:mem:dbr4-mt",
		"hapi.fhir.path_prefix=/fhirstorm",
		"hapi.fhir.fhir_version=r4",
	})
public class PathPrefixIT {
	private IGenericClient ourClient;
	private FhirContext ourCtx;

	@LocalServerPort
	private int port;

	@Test
	public void testReachable() {
		Meta meta = ourClient
			.meta()
			.get(Meta.class)
			.fromServer()
			.execute();

		assertNotNull(meta);
	}

	@BeforeEach
	void beforeEach() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		String ourServerBase = "http://localhost:" + port + "/fhirstorm/";
		ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
	}
}
