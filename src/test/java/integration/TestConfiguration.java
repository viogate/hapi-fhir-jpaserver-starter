package integration;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import ca.uhn.fhir.rest.server.interceptor.consent.ConsentOutcome;
import ca.uhn.fhir.rest.server.interceptor.consent.IConsentContextServices;
import com.cegeka.vconsult.fhir.server.SecurityInterceptor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestConfiguration {
	@Primary
	@Bean
	public SecurityInterceptor securityInterceptor() throws IOException {
		SecurityInterceptor securityInterceptorMock = mock(SecurityInterceptor.class);
		when(securityInterceptorMock.buildRuleList(any(RequestDetails.class))).thenReturn(
			new RuleBuilder()
				.allowAll()
				.build());
		when(securityInterceptorMock.canSeeResource(any(RequestDetails.class), any(IBaseResource.class), any(IConsentContextServices.class)))
			.thenReturn(ConsentOutcome.AUTHORIZED);
		when(securityInterceptorMock.startOperation(any(RequestDetails.class), any(IConsentContextServices.class)))
			.thenReturn(ConsentOutcome.AUTHORIZED);
		when(securityInterceptorMock.willSeeResource(any(RequestDetails.class), any(IBaseResource.class), any(IConsentContextServices.class))).thenReturn(ConsentOutcome.AUTHORIZED);

		return securityInterceptorMock;
	}
}
