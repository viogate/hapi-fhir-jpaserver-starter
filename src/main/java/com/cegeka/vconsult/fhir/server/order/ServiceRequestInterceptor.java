package com.cegeka.vconsult.fhir.server.order;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Interceptor
@Component
public class ServiceRequestInterceptor {
	private final RestTemplate restTemplate;
	private final String fhirSyncEndpoint;
	private final String fhirSyncDraftPath;

	@Autowired
	public ServiceRequestInterceptor(RestTemplate restTemplate, @Value("${fhirSync.endpoint}") String fhirSyncEndpoint, @Value("${fhirSync.draftPath}") String fhirSyncDraftPath) {
		this.restTemplate = restTemplate;
		this.fhirSyncEndpoint = fhirSyncEndpoint;
		this.fhirSyncDraftPath = fhirSyncDraftPath;
	}

	/**
	 * Interrupt HAPI processing and let fhir sync handle the creation instead if request is to create a service request
	 */
	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
	public boolean handleIncomingRequest(RequestDetails requestDetails, HttpServletResponse response) throws IOException {
		if (isRequestToCreateServiceRequest(requestDetails)) {
			handleServiceRequestCreation(requestDetails, response);
			return false;
		}

		return true;
	}

	private boolean isRequestToCreateServiceRequest(RequestDetails requestDetails) {
		return requestDetails.getResourceName().equals(ResourceType.ServiceRequest.name()) && requestDetails.getRestOperationType() == RestOperationTypeEnum.CREATE;
	}

	private void handleServiceRequestCreation(RequestDetails requestDetails, HttpServletResponse response) throws IOException {
		String serviceRequest = IOUtils.toString(requestDetails.getReader());
		ResponseEntity<String> fhirSyncResponse = forwardToFhirSync(serviceRequest);
		writeResponse(response, fhirSyncResponse);
	}

	/**
	 * The service request is forwarded to the fhir sync component which is responsible for persisting it
	 */
	private ResponseEntity<String> forwardToFhirSync(String serviceRequest) {
		return restTemplate.exchange(fhirSyncEndpoint + fhirSyncDraftPath, HttpMethod.POST, new HttpEntity<>(serviceRequest), String.class);
	}

	private void writeResponse(HttpServletResponse response, ResponseEntity<String> fhirSyncResponse) throws IOException {
		PrintWriter out = response.getWriter();
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.setStatus(fhirSyncResponse.getStatusCodeValue());
		out.print(fhirSyncResponse.getBody());
		out.flush();
	}

}
