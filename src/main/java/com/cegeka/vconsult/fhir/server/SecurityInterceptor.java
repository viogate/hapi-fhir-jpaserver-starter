package com.cegeka.vconsult.fhir.server;

import be.cegeka.vconsult.security.AuthorizationException;
import be.cegeka.vconsult.security.api.Context;
import be.cegeka.vconsult.security.api.ContextProvider;
import be.cegeka.vconsult.security.spring.AuthorizationExceptionHandler;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static be.cegeka.vconsult.security.api.Verification.*;

@Component
public class SecurityInterceptor {
	@Autowired
	private ContextProvider contextProvider;
	@Autowired
	private AuthorizationExceptionHandler authorizationExceptionHandler;

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
	public void hook(ServletRequestDetails requestDetails) {
		try {
			early_hook_safe(requestDetails);
		} catch (AuthorizationException e) {
			try {
				authorizationExceptionHandler.defaultErrorHandler(requestDetails.getServletRequest(), e);
			} catch (Throwable ex) {
				throw new AuthenticationException(e.getMessage(), ex);
			}
			throw new AuthenticationException(e.getMessage(), e);
		}
	}

	private void early_hook_safe(ServletRequestDetails requestDetails) throws AuthorizationException {
		String partition = requestDetails.getTenantId();

		Context context = contextProvider.getContext(requestDetails.getServletRequest());

		if(partition == null) {
			throw new AuthenticationException("Unknown partition");
		} else if("root".equals(partition)) {
			// all good for now
		} else if("DEFAULT".equals(partition)) {
			// check special back-end stuff
		} else if(partition.startsWith("D")) {
			String doctorNumber = partition.substring(1);
			context.verify(prescribingDoctor(doctorNumber).or(consultingDoctor(doctorNumber)).or(anyPermission("FHIR_ALL")));
		} else {
			throw new AuthenticationException("Unknown partition");
		}
	}

	@Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
	public void late_hook(ServletRequestDetails request, ResponseDetails responseDetails) {
		try {
			IBaseResource modified = late_hook_safe(request, responseDetails.getResponseResource());
			responseDetails.setResponseResource(modified);
		} catch (AuthorizationException e) {
			try {
				authorizationExceptionHandler.defaultErrorHandler(request.getServletRequest(), e);
			} catch (Throwable ex) {
				throw new AuthenticationException(e.getMessage(), ex);
			}
			throw new AuthenticationException(e.getMessage(), e);
		}
	}

	private IBaseResource late_hook_safe(ServletRequestDetails request, IBaseResource resource) throws AuthorizationException {
		String partition = request.getTenantId();
		Context context = contextProvider.getContext(request.getServletRequest());

		if("root".equals(partition)) {
			return checkResource(resource, null, context);
		} else if("DEFAULT".equals(partition)) {
			// no need to check stuff
			return resource;
		} else if(partition.startsWith("D")) {
			String doctorNumber = partition.substring(1);
			return checkResource(resource, doctorNumber, context);
		} else {
			throw new AuthenticationException("Unknown partition");
		}
	}

	private IBaseResource checkResource(IBaseResource resource, String doctorNumber, Context context) throws AuthorizationException {
		if(resource instanceof ServiceRequest) {
			boolean canSeeServiceRequest = context.matches(prescribingDoctor(doctorNumber));
			if (canSeeServiceRequest) {
				return resource;
			} else {
				return new ServiceRequest(); // dummy
			}
		} else if(resource instanceof Organization) {
			Organization organization = (Organization) resource;
			// Bad way to do this
			String masterId = organization.getIdentifier().get(0).getValue();
			context.verify(masterId(masterId));
			return resource;
		} else if(resource instanceof Basic) {
			// check that we are the backend
			return resource;
		} else if(resource instanceof Bundle) {
			Bundle bundle = (Bundle) resource;
			for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
				Resource modified = (Resource)checkResource(entry.getResource(), doctorNumber, context);
				entry.setResource(modified);
			}
			DomainResource basic = new Basic().addExtension(new Extension("http://daan.se", new StringType("aaa")));
			Bundle.BundleEntryComponent t = new Bundle.BundleEntryComponent();
			t.setResource(basic);
			bundle.addEntry(t);
			return bundle;
		} else {
			throw new AuthorizationException("Unsupported type " + resource.getClass().getSimpleName());
		}
	}
}
