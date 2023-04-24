package com.cegeka.vconsult.fhir.server.order;

import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceRequestInterceptorTest {

	@Mock
	private RestTemplate restTemplate;

	private ServiceRequestInterceptor serviceRequestInterceptor;

	@BeforeEach
	public void init() {
		serviceRequestInterceptor = new ServiceRequestInterceptor(restTemplate, "http://localhost:8080/fhirsync", "/internal/draft");
	}

	@Test
	void createsOfServiceRequestsAreInterruptedAndHandledByFhirSync_fhirSyncReturnsOK() throws IOException {
		doReturn(new ResponseEntity("updatedServiceRequest", HttpStatus.OK)).when(restTemplate).exchange(eq("http://localhost:8080/fhirsync/internal/draft"), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));

		ServletRequestDetails requestDetails = newServiceRequestDetails(RestOperationTypeEnum.CREATE, ResourceType.ServiceRequest, "initialServiceRequest");
		MockHttpServletResponse response = new MockHttpServletResponse();

		boolean continueHapiProcessing = serviceRequestInterceptor.handleIncomingRequest(requestDetails, response);

		assertHapiProcessingIsInterrupted(continueHapiProcessing, response, "updatedServiceRequest", 200);
	}

	@Test
	void createsOfServiceRequestsAreInterruptedAndHandledByFhirSync_fhirSyncReturnsBadRequest() throws IOException {
		doReturn(new ResponseEntity("invalid request", HttpStatus.BAD_REQUEST)).when(restTemplate).exchange(eq("http://localhost:8080/fhirsync/internal/draft"), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));

		ServletRequestDetails requestDetails = newServiceRequestDetails(RestOperationTypeEnum.CREATE, ResourceType.ServiceRequest, "initialServiceRequest");
		MockHttpServletResponse response = new MockHttpServletResponse();

		boolean continueHapiProcessing = serviceRequestInterceptor.handleIncomingRequest(requestDetails, response);

		assertHapiProcessingIsInterrupted(continueHapiProcessing, response, "invalid request", 400);
	}

	@Test
	void otherResourceTypesAreNotInterrupted() throws IOException {
		ServletRequestDetails requestDetails = newServiceRequestDetails(RestOperationTypeEnum.CREATE, ResourceType.DiagnosticReport, "initialDiagnosticReport");
		MockHttpServletResponse response = new MockHttpServletResponse();

		boolean continueHapiProcessing = serviceRequestInterceptor.handleIncomingRequest(requestDetails, response);

		assertHapiProcessingContinues(continueHapiProcessing);
	}

	@Test
	void otherRestOperationTypesOfServiceRequestsAreNotInterrupted() throws IOException {
		ServletRequestDetails requestDetails = newServiceRequestDetails(RestOperationTypeEnum.UPDATE, ResourceType.ServiceRequest, "updatedServiceRequest");
		MockHttpServletResponse response = new MockHttpServletResponse();

		boolean continueHapiProcessing = serviceRequestInterceptor.handleIncomingRequest(requestDetails, response);

		assertHapiProcessingContinues(continueHapiProcessing);
	}

	private void assertHapiProcessingContinues(boolean continueHapiProcessing) {
		assertThat(continueHapiProcessing).isTrue();
		verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
	}

	private void assertHapiProcessingIsInterrupted(boolean continueHapiProcessing, MockHttpServletResponse response, String updatedServiceRequest, int expected) throws UnsupportedEncodingException {
		assertThat(continueHapiProcessing).isFalse();
		assertThat(response.getContentAsString()).isEqualTo(updatedServiceRequest);
		assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
		assertThat(response.getStatus()).isEqualTo(expected);
	}

	private ServletRequestDetails newServiceRequestDetails(RestOperationTypeEnum create, ResourceType serviceRequest, String requestBody) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setContent("requestBody".getBytes(StandardCharsets.UTF_8));

		ServletRequestDetails requestDetails = new ServletRequestDetails();
		requestDetails.setServletRequest(request);
		requestDetails.setRestOperationType(create);
		requestDetails.setResourceName(serviceRequest.name());
		return requestDetails;
	}


}