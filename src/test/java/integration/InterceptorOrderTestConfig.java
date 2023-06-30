package integration;

import com.cegeka.vconsult.fhir.server.order.ServiceRequestInterceptor;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InterceptorOrderTestConfig extends ServiceRequestInterceptionTestConfig {

	@Bean
	public ServiceRequestInterceptor serviceRequestInterceptor() throws IOException {
		ServiceRequestInterceptor serviceRequestInterceptor = mock(ServiceRequestInterceptor.class);
		when(serviceRequestInterceptor.incomingRequestPostProcessed(any(), any())).thenReturn(true);

		return serviceRequestInterceptor;
	}
}
