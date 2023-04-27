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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@Interceptor
@Component
public class ServiceRequestInterceptor {
	private final RestTemplate restTemplate;
	private final String fhirSyncUrl;
	private final String fhirSyncDraftEndpoint;

	@Autowired
	public ServiceRequestInterceptor(RestTemplate restTemplate, @Value("${fhirSync.url}") String fhirSyncUrl, @Value("${fhirSync.draftEndpoint}") String fhirSyncDraftEndpoint) {
		this.restTemplate = restTemplate;
		this.fhirSyncUrl = fhirSyncUrl;
		this.fhirSyncDraftEndpoint = fhirSyncDraftEndpoint;
	}

	/**
	 * Interrupt HAPI processing and let fhir sync handle the creation instead if request is to create a service request
	 */
	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
	public boolean handleIncomingRequest(RequestDetails requestDetails, HttpServletResponse response) throws IOException {
		if (isRequestToCreateServiceRequest(requestDetails) && requestContainsPartition(requestDetails)) {
			handleServiceRequestCreation(requestDetails, response);
			return false;
		}

		return true;
	}

	private boolean isRequestToCreateServiceRequest(RequestDetails requestDetails) {
		return requestDetails.getResourceName() != null &&
			requestDetails.getResourceName().equals(ResourceType.ServiceRequest.name()) &&
			requestDetails.getRestOperationType() == RestOperationTypeEnum.CREATE;
	}

	private boolean requestContainsPartition(RequestDetails requestDetails) {
		return requestDetails.getTenantId() != null;
	}

	private void handleServiceRequestCreation(RequestDetails requestDetails, HttpServletResponse response) throws IOException {
		ResponseEntity<String> fhirSyncResponse = forwardToFhirSync(requestDetails);
		writeResponse(response, fhirSyncResponse);
	}

	/**
	 * The service request is forwarded to the fhir sync component which is responsible for persisting it
	 */
	private ResponseEntity<String> forwardToFhirSync(RequestDetails requestDetails) throws IOException {
		String body = IOUtils.toString(requestDetails.getReader());
		String partition = requestDetails.getTenantId();
		return restTemplate.exchange(fhirSyncUrl + fhirSyncDraftEndpoint + "/{partition}", HttpMethod.POST, newRequest(body), String.class, partition);
	}

	private void writeResponse(HttpServletResponse response, ResponseEntity<String> fhirSyncResponse) throws IOException {
		PrintWriter out = response.getWriter();
		response.setContentType("application/fhir+json");
		response.setCharacterEncoding("UTF-8");
		response.setStatus(fhirSyncResponse.getStatusCodeValue());
		out.print(fhirSyncResponse.getBody());
		out.flush();
	}

	private HttpEntity<String> newRequest(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.put("Content-Type", List.of(MediaType.APPLICATION_JSON));

		return new HttpEntity<>(body, headers);
	}

}
