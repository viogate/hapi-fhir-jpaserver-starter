package integration;

import com.cegeka.vconsult.fhir.server.TestableApplication;
import com.cegeka.vconsult.fhir.server.order.ServiceRequestInterceptor;
import com.cegeka.vconsult.fhir.server.security.SecurityInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
	classes = {TestableApplication.class, InterceptorOrderTestConfig.class},
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = {"security.insecure=/**", "security.secure="})
public class InterceptorOrderTest {

	private static final String LOCALHOST = "http://localhost:";
	private static final String FHIR = "/fhir/";

	@LocalServerPort
	private int localPort;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private SecurityInterceptor securityInterceptor;

	@Autowired
	private ServiceRequestInterceptor serviceRequestInterceptor;

	@Test
	public void securityInterceptorShouldRunFirst_postRequest() throws IOException {

		restTemplate.exchange(LOCALHOST + localPort + FHIR + "root/ServiceRequest", HttpMethod.POST, null, String.class);

		InOrder inOrder = Mockito.inOrder(securityInterceptor, serviceRequestInterceptor);
		inOrder.verify(securityInterceptor).incomingRequestPostProcessed(Mockito.any(), Mockito.any());
		inOrder.verify(serviceRequestInterceptor).incomingRequestPostProcessed(Mockito.any(), Mockito.any());
	}

	@Test
	public void securityInterceptorShouldRunFirst_getRequest() throws IOException {
		
		restTemplate.exchange(LOCALHOST + localPort + FHIR + "root/ServiceRequest", HttpMethod.GET, null, String.class);

		InOrder inOrder = Mockito.inOrder(securityInterceptor, serviceRequestInterceptor);
		inOrder.verify(securityInterceptor).incomingRequestPostProcessed(Mockito.any(), Mockito.any());
		inOrder.verify(serviceRequestInterceptor).incomingRequestPostProcessed(Mockito.any(), Mockito.any());
	}
}
