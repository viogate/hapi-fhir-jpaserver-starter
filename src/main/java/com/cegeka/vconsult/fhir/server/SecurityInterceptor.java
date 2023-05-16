package com.cegeka.vconsult.fhir.server;

import be.cegeka.vconsult.security.AuthorizationException;
import be.cegeka.vconsult.security.api.Context;
import be.cegeka.vconsult.security.api.ContextProvider;
import be.cegeka.vconsult.security.api.Verification;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import ca.uhn.fhir.rest.server.interceptor.consent.ConsentOutcome;
import ca.uhn.fhir.rest.server.interceptor.consent.IConsentContextServices;
import ca.uhn.fhir.rest.server.interceptor.consent.IConsentService;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.List;

import static be.cegeka.vconsult.security.api.Verification.*;

@Component
public class SecurityInterceptor extends AuthorizationInterceptor implements IConsentService {
	private static final Verification FHIR_ALL = anyPermission("FHIR_ALL");

	private final ContextProvider contextProvider;

	public SecurityInterceptor(ContextProvider contextProvider) {
		this.contextProvider = contextProvider;
	}

	@Override
	public List<IAuthRule> buildRuleList(RequestDetails requestDetails) {
		Context context = getContext(requestDetails);

		if (context.matches(FHIR_ALL)) {
			return new RuleBuilder()
				.allowAll()
				.build();
		} else if (context.matches(anyDoctor())) {
			if (!isAllowedToSeePartition(requestDetails, context)) {
				return new RuleBuilder()
					.denyAll()
					.build();
			}

			return new RuleBuilder()
				.allow().read().resourcesOfType(Organization.class).withAnyId().forTenantIds("root").andThen()
				.allow().read().resourcesOfType(PractitionerRole.class).withAnyId().forTenantIds("root").andThen()
				.allow().read().resourcesOfType(Endpoint.class).withAnyId().forTenantIds("root").andThen()
				.allow().read().resourcesOfType(Practitioner.class).withAnyId().andThen()
				.allow().read().resourcesOfType(ServiceRequest.class).withAnyId().notForTenantIds("root").andThen()
				.allow().create().resourcesOfType(ServiceRequest.class).withAnyId().notForTenantIds("root").andThen()
				.denyAll()
				.build();
		} else {
			return new RuleBuilder()
				.denyAll()
				.build();
		}
	}

	private boolean isAllowedToSeePartition(RequestDetails requestDetails, Context context) {
		String tenantId = requestDetails.getTenantId();
		if (tenantId.equals("root")) {
			return true;
		}
		if (!tenantId.contains("D")) {
			return false;
		}
		String archiveNumber = tenantId.substring(tenantId.indexOf("D") + 1);
		ConsentOutcome consentOutcome = verify(context, consultingDoctor(archiveNumber).or(prescribingDoctor(archiveNumber)));
		if (consentOutcome == ConsentOutcome.REJECT) {
			return false;
		}

		return true;
	}


	@Override
	public ConsentOutcome canSeeResource(RequestDetails theRequestDetails, IBaseResource theResource, IConsentContextServices theContextServices) {
		Context context = getContext(theRequestDetails);
		if (resourceTypeOnlyPresentInPrivateDoctorPartition(theResource)) {
			return ConsentOutcome.PROCEED;
		}

		return isResourceVisibleForRequester(theResource, context);
	}

	private ConsentOutcome isResourceVisibleForRequester(IBaseResource theResource, Context context) {
		if (theResource instanceof Basic) {
			return canSee(context);
		} else if (theResource instanceof Organization) {
			return canSee(context, (Organization) theResource);
		} else if (theResource instanceof Practitioner) {
			return canSee(context, (Practitioner) theResource);
		} else if (theResource instanceof PractitionerRole) {
			return canSee(context, (PractitionerRole) theResource);
		} else if (theResource instanceof Endpoint) {
			return canSee(context, (Endpoint) theResource);
		} else if (theResource instanceof CapabilityStatement || theResource instanceof Parameters) {
			return ConsentOutcome.AUTHORIZED;
		} else {
			return ConsentOutcome.REJECT;
		}
	}

	private boolean resourceTypeOnlyPresentInPrivateDoctorPartition(IBaseResource theResource) {
		if (theResource instanceof ServiceRequest) {
			return true;
		}

		return false;
	}

	private ConsentOutcome canSee(Context context) {
		return verify(context, FHIR_ALL);
	}

	private ConsentOutcome canSee(Context context, Organization organization) {
		String masterId = organization.getIdentifier().stream()
			.filter(i -> "http://viollier.ch/fhir/system/master-id".equals(i.getSystem()))
			.map(Identifier::getValue)
			.findFirst()
			.orElseThrow(); //TODO

		return verify(context, masterId(masterId).or(FHIR_ALL));
	}

	private ConsentOutcome canSee(Context context, Practitioner practitioner) {
		String archiveNumber = practitioner.getIdentifier().stream()
			.filter(i -> "http://viollier.ch/fhir/system/archive-number".equals(i.getSystem()))
			.map(Identifier::getValue)
			.findFirst()
			.orElseThrow(); //TODO

		return verify(context, consultingDoctor(archiveNumber).or(prescribingDoctor(archiveNumber)).or(FHIR_ALL));
	}

	private ConsentOutcome canSee(Context context, PractitionerRole practitionerRole) {
		String practitionerReference = practitionerRole
			.getPractitioner()
			.getReference();
		String organizationReference = practitionerRole
			.getOrganization()
			.getReference();

		String archiveNumberPractitioner = practitionerReference.substring(practitionerReference.indexOf("-") + 1);
		String organizationId = organizationReference.substring(organizationReference.indexOf("-") + 1);

		return verify(context,
			consultingDoctor(archiveNumberPractitioner)
				.or(prescribingDoctor(archiveNumberPractitioner))
				.or(masterId(organizationId))
				.or(FHIR_ALL));
	}

	private ConsentOutcome canSee(Context context, Endpoint endpoint) {
		String address = endpoint.getAddress();
		String archiveNumber = address.substring(address.lastIndexOf("/") + 2);

		return verify(context,
			consultingDoctor(archiveNumber)
				.or(prescribingDoctor(archiveNumber))
				.or(FHIR_ALL));
	}

	private ConsentOutcome verify(Context context, Verification verification) {
		if (context.matches(verification)) {
			return ConsentOutcome.PROCEED;
		} else {
			return ConsentOutcome.REJECT;
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
}
