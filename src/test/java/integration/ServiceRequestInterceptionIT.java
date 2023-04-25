package integration;

import ca.uhn.fhir.jpa.starter.Application;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@WireMockTest
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ServiceRequestInterceptionIT {

	private final static String FHIR_SYNC_RESPONSE_BODY =
		"{" +
			"\"resourceType\": \"ServiceRequest\"," +
			"\"id\": \"001234234\"" +
		"}";
	private static final String LOCALHOST = "http://localhost:";

	@LocalServerPort
	private int localPort;

	@Autowired
	private TestRestTemplate restTemplate;

	@Value("${fhirSync.draftPath}")
	private String fhirSyncDraftPath;

	private static int fhirSyncPort;

	@BeforeAll
	static void init(WireMockRuntimeInfo wireMockRuntimeInfo) {
		fhirSyncPort = wireMockRuntimeInfo.getHttpPort();
	}

	@DynamicPropertySource
	static void registerDynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("fhirSync.endpoint", () -> "http://localhost:" + fhirSyncPort);
	}

	@BeforeEach
	public void initForEach() {
		stubFor(post(fhirSyncDraftPath).willReturn(ok(FHIR_SYNC_RESPONSE_BODY)));
	}

	@Test
	public void serviceRequestPostIsInterceptedAndForwardedToFhirSync() {
		String requestBody = "{ \"resourceType\": \"ServiceRequest\" }";
		ResponseEntity<String> response = this.restTemplate.exchange(LOCALHOST + localPort + "/fhir/ServiceRequest", HttpMethod.POST, newRequest(requestBody), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(FHIR_SYNC_RESPONSE_BODY);
		verify(postRequestedFor(urlEqualTo(fhirSyncDraftPath)));
	}

	@Test
	public void serviceRequestPutIsNotIntercepted() {
		String requestBody =
			"{" +
				"\"resourceType\": \"ServiceRequest\"," +
				"\"id\": \"A001234234\"" +
			"}";

		ResponseEntity<String> response = this.restTemplate.exchange(LOCALHOST + localPort + "/fhir/ServiceRequest/{id}", HttpMethod.PUT, newRequest(requestBody), String.class, Map.of("id", "A001234234"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotEqualTo(FHIR_SYNC_RESPONSE_BODY);
		verify(exactly(0), anyRequestedFor(urlEqualTo(fhirSyncDraftPath)));
	}

	@Test
	public void otherResourcePostIsNotIntercepted() {
		String requestBody = "{ \"resourceType\": \"Organization\" }";

		ResponseEntity<String> response = this.restTemplate.exchange(LOCALHOST + localPort + "/fhir/Organization", HttpMethod.POST, newRequest(requestBody), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotEqualTo(FHIR_SYNC_RESPONSE_BODY);
		verify(exactly(0), anyRequestedFor(urlEqualTo(fhirSyncDraftPath)));
	}

	@Test
	public void postContainingTransactionIsNotIntercepted() {
		String requestBody =
			"{" +
				"\"resourceType\": \"Bundle\"," +
				"\"type\": \"transaction\"," +
				"\"entry\": [{" +
					"\"resource\" : {" +
						"\"resourceType\": \"ServiceRequest\"" +
					"}," +
					"\"request\" : {" +
						"\"method\": \"POST\"," +
						"\"url\": \"ServiceRequest\"" +
					"}" +
				"}]" +
			"}";

		ResponseEntity<String> response = this.restTemplate.exchange(LOCALHOST + localPort + "/fhir", HttpMethod.POST, newRequest(requestBody), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotEqualTo(FHIR_SYNC_RESPONSE_BODY);
		verify(exactly(0), anyRequestedFor(urlEqualTo(fhirSyncDraftPath)));
	}

	private HttpEntity<String> newRequest(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.put("Content-Type", List.of("application/fhir+json"));

		return new HttpEntity<>(body, headers);
	}

}
