package com.cegeka.vconsult.fhir.server;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.cegeka.vconsult.fhir.server.order.ServiceRequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestConfiguration {
	@Primary
	@Bean
	public ServiceRequestInterceptor serviceRequestInterceptor() throws IOException {
		ServiceRequestInterceptor serviceRequestInterceptorMock = mock(ServiceRequestInterceptor.class);
		when(serviceRequestInterceptorMock.handleIncomingRequest(any(RequestDetails.class), any(HttpServletResponse.class)))
			.thenReturn(true);
		return serviceRequestInterceptorMock;
	}
}
