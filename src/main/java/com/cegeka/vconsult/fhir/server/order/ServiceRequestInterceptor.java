package com.cegeka.vconsult.fhir.server.order;

import be.cegeka.vconsult.security.AuthorizationException;
import be.cegeka.vconsult.security.api.Context;
import be.cegeka.vconsult.security.api.ContextProvider;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@Interceptor
@Component
public class ServiceRequestInterceptor {
	public static final String REQUEST_PARAMETER_HOSPITAL_ID = "hospitalId";
	public static final String REQUEST_PARAMETER_USER_SHORT_ID = "userShortId";
	private final RestTemplate restTemplate;
	private final String fhirSyncUrl;
	private final String fhirSyncDraftEndpoint;

	private final ContextProvider contextProvider;

	@Autowired
	public ServiceRequestInterceptor(RestTemplate restTemplate,
												ContextProvider contextProvider,
												@Value("${fhirSync.url}") String fhirSyncUrl,
												@Value("${fhirSync.draftEndpoint}") String fhirSyncDraftEndpoint) {
		this.restTemplate = restTemplate;
		this.contextProvider = contextProvider;
		this.fhirSyncUrl = fhirSyncUrl;
		this.fhirSyncDraftEndpoint = fhirSyncDraftEndpoint;
	}

	/**
	 * Interrupt HAPI processing and let fhir sync handle the creation instead if request is to create a service request
	 */
	@Hook(value = Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED, order = 200)
	public boolean incomingRequestPostProcessed(RequestDetails requestDetails, HttpServletResponse response) throws IOException {
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

		UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder
			.fromHttpUrl(fhirSyncUrl + fhirSyncDraftEndpoint + "/{partition}");

		String shortId = getShortId(requestDetails);
		String hospitalId = getHospitalId(requestDetails);
		if (StringUtils.isNotBlank(shortId)) {
			uriComponentsBuilder.queryParam(REQUEST_PARAMETER_USER_SHORT_ID, shortId);
		}
		if (StringUtils.isNotBlank(hospitalId)) {
			uriComponentsBuilder.queryParam(REQUEST_PARAMETER_HOSPITAL_ID, hospitalId);
		}

		UriComponents uriComponents = uriComponentsBuilder.buildAndExpand(partition);
		return restTemplate.exchange(uriComponents.toUriString(), HttpMethod.POST, newRequest(body), String.class, partition);
	}

	private String getHospitalId(RequestDetails requestDetails) {
		Context context = getContext(requestDetails);
		try {
			if (context.getHospital() == null) {
				return "";
			}
			return String.valueOf(context.getHospital());
		} catch (AuthorizationException e) {
			return "";
		}
	}

	private String getShortId(RequestDetails requestDetails) {
		Context context = getContext(requestDetails);
		try {
			if (context.getUserShortId() == null) {
				return "";
			}
			return String.valueOf(context.getUserShortId());
		} catch (AuthorizationException e) {
			return "";
		}
	}

	private Context getContext(RequestDetails theRequestDetails) {
		ServletRequestDetails servletRequestDetails = (ServletRequestDetails) theRequestDetails;
		try {
			return contextProvider.getContext(servletRequestDetails.getServletRequest());
		} catch (AuthorizationException e) {
			throw new AuthenticationException(e.getMessage());
		}
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
