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
	private static final String APPLICATION_FHIR_JSON = "application/fhir+json";
	private static final String PARTITION = "D123";

	@LocalServerPort
	private int localPort;

	@Autowired
	private TestRestTemplate restTemplate;

	@Value("${fhirSync.draftEndpoint}")
	private String fhirSyncDraftEndpoint;

	private static int fhirSyncPort;

	@BeforeAll
	static void init(WireMockRuntimeInfo wireMockRuntimeInfo) {
		fhirSyncPort = wireMockRuntimeInfo.getHttpPort();
	}

	@DynamicPropertySource
	static void registerDynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("fhirSync.url", () -> "http://localhost:" + fhirSyncPort);
	}

	@BeforeEach
	public void initForEach() {
		createPartition(PARTITION);
		stubFor(post(fhirSyncDraftEndpoint + "/" + PARTITION).willReturn(ok(FHIR_SYNC_RESPONSE_BODY)));
	}

	@Test
	public void serviceRequestPostWithPartitionIsInterceptedAndForwardedToFhirSync() {
		String requestBody = "{ \"resourceType\": \"ServiceRequest\" }";
		ResponseEntity<String> response = restTemplate.exchange(LOCALHOST + localPort + "/fhir/{partition}/ServiceRequest", HttpMethod.POST, newRequest(requestBody), String.class, PARTITION);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(FHIR_SYNC_RESPONSE_BODY);
		verify(postRequestedFor(urlEqualTo(fhirSyncDraftEndpoint + "/" + PARTITION)));
	}

	@Test
	public void serviceRequestPutIsNotIntercepted() {
		String requestBody =
			"{" +
				"\"resourceType\": \"ServiceRequest\"," +
				"\"id\": \"A001234234\"" +
			"}";

		ResponseEntity<String> response = restTemplate.exchange(LOCALHOST + localPort + "/fhir/{partition}/ServiceRequest/{id}", HttpMethod.PUT, newRequest(requestBody), String.class, Map.of("partition", PARTITION, "id", "A001234234"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotEqualTo(FHIR_SYNC_RESPONSE_BODY);
		verify(exactly(0), anyRequestedFor(urlEqualTo(fhirSyncDraftEndpoint + "/" + PARTITION)));
	}

	@Test
	public void otherResourcePostIsNotIntercepted() {
		String requestBody = "{ \"resourceType\": \"Organization\" }";

		ResponseEntity<String> response = restTemplate.exchange(LOCALHOST + localPort + "/fhir/{partition}/Organization", HttpMethod.POST, newRequest(requestBody), String.class, PARTITION);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotEqualTo(FHIR_SYNC_RESPONSE_BODY);
		verify(exactly(0), anyRequestedFor(urlEqualTo(fhirSyncDraftEndpoint + "/" + PARTITION)));
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

		ResponseEntity<String> response = restTemplate.exchange(LOCALHOST + localPort + "/fhir/{partition}", HttpMethod.POST, newRequest(requestBody), String.class, PARTITION);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotEqualTo(FHIR_SYNC_RESPONSE_BODY);
		verify(exactly(0), anyRequestedFor(urlEqualTo(fhirSyncDraftEndpoint + "/" + PARTITION)));
	}

	private void createPartition(String partitionName) {
		String requestBody =
			"{" +
				"\"resourceType\": \"Parameters\"," +
				"\"parameter\": [ {" +
				"\"name\": \"id\"," +
				"\"valueInteger\": 1" +
				"}, {" +
				"\"name\": \"name\"," +
				"\"valueCode\": \"" + partitionName + "\"" +
				"}, {" +
				"\"name\": \"description\"," +
				"\"valueString\": \"partition\"" +
				"} ]" +
			"}";
		restTemplate.exchange(LOCALHOST + localPort + "/fhir/root/$partition-management-create-partition", HttpMethod.POST, newRequest(requestBody), String.class);
	}

	private HttpEntity<String> newRequest(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.put(HttpHeaders.CONTENT_TYPE, List.of(APPLICATION_FHIR_JSON));

		return new HttpEntity<>(body, headers);
	}

}
