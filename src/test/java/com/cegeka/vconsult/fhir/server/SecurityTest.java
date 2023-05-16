package com.cegeka.vconsult.fhir.server;

import be.cegeka.vconsult.security.test.MockContext;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.Application;
import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@WireMockTest
@SpringBootTest(classes = {Application.class, TestConfiguration.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SecurityTest {

	@Autowired
	private MockContext mockContext;

	@LocalServerPort
	private int port;

	private final static FhirContext ctx = FhirContext.forR4();

	@BeforeEach
	void beforeEach() {
		ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		mockContext.setPermissions(Set.of("FHIR_ALL"));
		removeAllResourcesFromServer();

		if (!hasPartition(-1)) {
			createPartition(-1, "root");
		}
		if (!hasPartition(57761)) {
			createPartition(57761, "D57761");
		}
		if (!hasPartition(666)) {
			createPartition(666, "D666");
		}

		// Reset after create
		resetMockContext();
	}


	@Test
	void whenIHaveNoCredentials_thenICanNotDoAnything() {
		mockContext.setPermissions(Set.of("FHIR_ALL"));

		assertThatThrownBy(() -> {
			getUnauthenticatedClient("DEFAULT")
				.search()
				.forResource(Patient.class)
				.execute();
		}).isInstanceOfAny(AuthenticationException.class, ForbiddenOperationException.class);
	}

	private org.hl7.fhir.r4.model.Basic getBasic(String partitionName, String id) {
		return getAuthenticatedClient(partitionName)
			.read()
			.resource(org.hl7.fhir.r4.model.Basic.class)
			.withId(id)
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	private Bundle searchBasic(String partitionName, String id) {
		return getAuthenticatedClient(partitionName)
			.<Bundle>search()
			.forResource(org.hl7.fhir.r4.model.Basic.class)
			.where(org.hl7.fhir.r4.model.Basic.IDENTIFIER.exactly().systemAndIdentifier("dummy", id))
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	private String createBasic(String partitionName, String systemValue) {
		org.hl7.fhir.r4.model.Basic basic = new org.hl7.fhir.r4.model.Basic();
		Identifier id = new Identifier();
		id.setSystem("dummy");
		id.setValue(systemValue);
		basic.addIdentifier(id);

		return getAuthenticatedClient(partitionName)
			.create()
			.resource(basic)
			.execute()
			.getId()
			.getIdPart();
	}

	private org.hl7.fhir.r4.model.Organization getOrganization(String partitionName, String id) {
		return getAuthenticatedClient(partitionName)
			.read()
			.resource(org.hl7.fhir.r4.model.Organization.class)
			.withId(id)
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	private String setUpOrganization(String partitionName, String masterId) {
		mockContext.setPermissions(Set.of("FHIR_ALL"));

		String id = createOrganization(partitionName, masterId);

		resetMockContext();

		return id;
	}

	private String createOrganization(String partitionName, String masterId) {
		org.hl7.fhir.r4.model.Organization organization = new org.hl7.fhir.r4.model.Organization();
		Identifier id = new Identifier();
		id.setSystem("http://viollier.ch/fhir/system/master-id");
		id.setValue(masterId);
		organization.addIdentifier(id);
		String organizationId = getFullOrganizationId(partitionName, masterId);
		organization.setId(organizationId);

		return getAuthenticatedClient(partitionName)
			.update()
			.resource(organization)
			.execute()
			.getId()
			.getIdPart();
	}

	private String getFullOrganizationId(String partitionName, String organizationIdPart) {
		return String.format("Organization/%s-%s", partitionName, organizationIdPart);
	}

	private Bundle searchOrganization(String partitionName, String masterId) {
		return getAuthenticatedClient(partitionName)
			.<Bundle>search()
			.forResource(org.hl7.fhir.r4.model.Organization.class)
			.where(org.hl7.fhir.r4.model.Organization.IDENTIFIER.exactly().systemAndIdentifier("http://viollier.ch/fhir/system/master-id", masterId))
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	private String setupPractitioner(String partitionName, String archiveNumber) {
		mockContext.setPermissions(Set.of("FHIR_ALL"));

		String idPart = createPractitioner(partitionName, archiveNumber);

		resetMockContext();

		return idPart;
	}

	private String createPractitioner(String partitionName, String archiveNumber) {
		org.hl7.fhir.r4.model.Practitioner practitioner = new org.hl7.fhir.r4.model.Practitioner();
		Identifier id = new Identifier();
		id.setSystem("http://viollier.ch/fhir/system/archive-number");
		id.setValue(archiveNumber);
		practitioner.addIdentifier(id);

		String fullPractitionerId = getFullPractitionerId(partitionName, archiveNumber);
		practitioner.setId(fullPractitionerId);

		String idPart = getAuthenticatedClient(partitionName)
			.update()
			.resource(practitioner)
			.execute()
			.getId()
			.getIdPart();
		return idPart;
	}

	private String getFullPractitionerId(String partitionName, String practitionerIdPart) {
		return String.format("Practitioner/%s-%s", partitionName, practitionerIdPart);
	}

	private org.hl7.fhir.r4.model.Practitioner getPractitioner(String partitionName, String id) {
		return getAuthenticatedClient(partitionName)
			.read()
			.resource(org.hl7.fhir.r4.model.Practitioner.class)
			.withId(id)
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	private Bundle searchPractitioner(String partitionName, String masterId) {
		return getAuthenticatedClient(partitionName)
			.<Bundle>search()
			.forResource(org.hl7.fhir.r4.model.Practitioner.class)
			.where(org.hl7.fhir.r4.model.Practitioner.IDENTIFIER.exactly().systemAndIdentifier("http://viollier.ch/fhir/system/archive-number", masterId))
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	private String setupPractitionerRole(String partitionName, String accountId, String archiveNumber) {
		mockContext.setPermissions(Set.of("FHIR_ALL"));

		String idPart = createPractitionerRole(partitionName, accountId, archiveNumber);

		resetMockContext();

		return idPart;
	}

	private String createPractitionerRole(String partitionName, String organizationIdPart, String practitionerIdPart) {
		org.hl7.fhir.r4.model.PractitionerRole practitionerRole = new org.hl7.fhir.r4.model.PractitionerRole();
		String organizationId = getFullOrganizationId(partitionName, organizationIdPart);
		String practitionerId = getFullPractitionerId(partitionName, practitionerIdPart);
		practitionerRole.setOrganization(new Reference(organizationId));
		practitionerRole.setPractitioner(new Reference(practitionerId));

		String idPart = getAuthenticatedClient(partitionName)
			.create()
			.resource(practitionerRole)
			.execute()
			.getId()
			.getIdPart();
		return idPart;
	}

	private org.hl7.fhir.r4.model.PractitionerRole getPractitionerRole(String partitionName, String id) {
		return getAuthenticatedClient(partitionName)
			.read()
			.resource(org.hl7.fhir.r4.model.PractitionerRole.class)
			.withId(id)
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	private Bundle searchPractitionerRole(String partitionName, String masterId, String archiveNumber) {
		return getAuthenticatedClient(partitionName)
			.<Bundle>search()
			.forResource(org.hl7.fhir.r4.model.PractitionerRole.class)
			.where(org.hl7.fhir.r4.model.PractitionerRole.ORGANIZATION.hasChainedProperty(org.hl7.fhir.r4.model.Organization.IDENTIFIER.exactly().systemAndIdentifier("http://viollier.ch/fhir/system/master-id", masterId)))
			.where(org.hl7.fhir.r4.model.PractitionerRole.PRACTITIONER.hasChainedProperty(org.hl7.fhir.r4.model.Practitioner.IDENTIFIER.exactly().systemAndIdentifier("http://viollier.ch/fhir/system/archive-number", archiveNumber)))
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	private Bundle searchEndpoint(String partitionName, String endpointId) {
		return getAuthenticatedClient(partitionName)
			.<Bundle>search()
			.forResource(org.hl7.fhir.r4.model.Endpoint.class)
			.where(IAnyResource.RES_ID.exactly().identifier(endpointId))
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	private String setupEndpoint(String partitionName, String masterId, String archiveNumber) {
		mockContext.setPermissions(Set.of("FHIR_ALL"));

		String idPart = createEndpoint(partitionName, masterId, archiveNumber);

		resetMockContext();

		return idPart;
	}

	private String createEndpoint(String partitionName, String masterId, String archiveNumber) {
		String endpointId = String.format("Endpoint/%s-%s-%s", partitionName, masterId, archiveNumber);
		org.hl7.fhir.r4.model.Endpoint endpoint = new org.hl7.fhir.r4.model.Endpoint();
		endpoint.setId(endpointId);
		String address = String.format("https://api.dev.viollier.ch/fhir/r4/D%s", archiveNumber);
		endpoint.setAddress(address);

		return getAuthenticatedClient(partitionName)
			.create()
			.resource(endpoint)
			.execute()
			.getId()
			.getIdPart();
	}

	private org.hl7.fhir.r4.model.Endpoint getEndpoint(String partitionName, String id) {
		return getAuthenticatedClient(partitionName)
			.read()
			.resource(org.hl7.fhir.r4.model.Endpoint.class)
			.withId(id)
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	private String setupServiceRequest(String partitionName, String orderId) {
		mockContext.setPermissions(Set.of("FHIR_ALL"));

		String idPart = updateServiceRequest(partitionName, orderId);

		resetMockContext();

		return idPart;
	}

	private String createServiceRequest(String partitionName) {
		org.hl7.fhir.r4.model.ServiceRequest serviceRequest = new org.hl7.fhir.r4.model.ServiceRequest();

		return getAuthenticatedClient(partitionName)
			.create()
			.resource(serviceRequest)
			.execute()
			.getId()
			.getIdPart();
	}

	private Bundle searchServiceRequest(String partitionName, String orderId) {
		return getAuthenticatedClient(partitionName)
			.<Bundle>search()
			.forResource(org.hl7.fhir.r4.model.ServiceRequest.class)
			.where(org.hl7.fhir.r4.model.ServiceRequest.IDENTIFIER.exactly().systemAndIdentifier("http://viollier.ch/fhir/system/order-number", orderId))
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	private String updateServiceRequest(String partitionName, String orderId) {
		String serviceRequestFullId = String.format("ServiceRequest/%s-%s", partitionName, orderId);
		ServiceRequest serviceRequest = new ServiceRequest();
		serviceRequest.setId(serviceRequestFullId);
		Identifier identifier = new Identifier();
		identifier.setSystem("http://viollier.ch/fhir/system/order-number");
		identifier.setValue(orderId);
		serviceRequest.addIdentifier(identifier);

		return getAuthenticatedClient(partitionName)
			.update()
			.resource(serviceRequest)
			.execute()
			.getId()
			.getIdPart();
	}

	private org.hl7.fhir.r4.model.ServiceRequest getServiceRequest(String partitionName, String id) {
		return getAuthenticatedClient(partitionName)
			.read()
			.resource(org.hl7.fhir.r4.model.ServiceRequest.class)
			.withId(id)
			.cacheControl(CacheControlDirective.noCache())
			.execute();
	}

	@Nested
	class Default {
		@Test
		void whenIHaveSpecialPermission_thenICanAccessTheDefaultPartition() {
			mockContext.setPermissions(Set.of("FHIR_ALL"));

			createPartition(-11, "dummy1");
		}

		@Test
		void whenIDoNotHaveSpecialPermission_thenICannotAccessTheDefaultPartition() {
			mockContext.setPermissions(Set.of());

			assertThatThrownBy(() -> {
				createPartition(-12, "dummy2");
			}).isInstanceOfAny(AuthenticationException.class, ForbiddenOperationException.class);
		}
	}

	@Nested
	class Root {

		private String partitionName = "root";

		@Nested
		class Basic {
			@Test
			void normalAccountsCanNotGet() {
				String id = setupBasic("Basic1");

				mockContext.setFailVerifyMasterId(true);

				assertThatThrownBy(() -> {
					getBasic(partitionName, id);
				}).isInstanceOfAny(ResourceNotFoundException.class, ForbiddenOperationException.class, AuthenticationException.class);
			}

			@Test
			void normalAccountsCanNotSearch() {
				setupBasic("Basic2");

				assertThatThrownBy(() -> {
					searchBasic(partitionName, "Basic2");
				}).isInstanceOfAny(ResourceNotFoundException.class, ForbiddenOperationException.class, AuthenticationException.class);
			}

			@Test
			void normalAccountsCanNotCreate() {
				setupBasic("Basic4");

				assertThatThrownBy(() -> {
					createBasic("Basic4");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCanSearchWhenIHaveSpecialPermissions() {
				setupBasic("Basic3");

				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle actual = searchBasic(partitionName, "Basic3");
				assertThat(actual.getEntry()).hasSize(1);
			}

			private String setupBasic(String systemValue) {
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				String idPart = createBasic(systemValue);

				resetMockContext();

				return idPart;
			}

			private String createBasic(String systemValue) {
				org.hl7.fhir.r4.model.Basic basic = new org.hl7.fhir.r4.model.Basic();
				Identifier id = new Identifier();
				id.setSystem("dummy");
				id.setValue(systemValue);
				basic.addIdentifier(id);

				return getAuthenticatedClient("root")
					.create()
					.resource(basic)
					.execute()
					.getId()
					.getIdPart();
			}
		}

		@Nested
		class Organization {
			@Test
			void iCanGet() {
				String id = setUpOrganization(partitionName, "ME1");

				org.hl7.fhir.r4.model.Organization organization = getOrganization(partitionName, id);

				assertThat(organization).isNotNull();
			}

			@Test
			void iCanSearch() {
				setUpOrganization(partitionName, "ME2");

				Bundle execute = searchOrganization(partitionName, "ME2");

				Resource resource = execute.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.Organization.class);
			}

			@Test
			void iCanNotGetAnother() {
				String id = setUpOrganization(partitionName, "OTHER1");

				mockContext.setFailVerifyMasterId(true);

				assertThatThrownBy(() -> {
					getOrganization(partitionName, id);
				}).isInstanceOfAny(ResourceNotFoundException.class);
			}

			@Test
			void iCanNotSearchAnother() {
				setUpOrganization(partitionName, "OTHER2");

				mockContext.setFailVerifyMasterId(true);

				Bundle actual = searchOrganization(partitionName, "OTHER2");
				assertThat(actual.getEntry()).isEmpty();
			}

			@Test
			void iCanNotCreate() {
				assertThatThrownBy(() -> {
					createOrganization(partitionName, "ME3");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCanSearchAnotherWhenIHaveSpecialPermissions() {
				setUpOrganization(partitionName, "OTHER3");

				mockContext.setFailVerifyMasterId(true);
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle actual = searchOrganization(partitionName, "OTHER3");
				assertThat(actual.getEntry()).hasSize(1);
			}
		}

		@Nested
		class Practitioner {
			@Test
			void iCanGetMyPrescribingDoctor() {
				String id = setupPractitioner(partitionName, "P1");

				mockContext.setFailVerifyConsultingDoctors(true);

				org.hl7.fhir.r4.model.Practitioner practitioner = getPractitioner(partitionName, id);

				assertThat(practitioner).isNotNull();
			}

			@Test
			void iCanGetMyConsultingDoctor() {
				String id = setupPractitioner(partitionName, "C1");

				mockContext.setFailVerifyPrescribingDoctors(true);

				org.hl7.fhir.r4.model.Practitioner practitioner = getPractitioner(partitionName, id);

				assertThat(practitioner).isNotNull();
			}

			@Test
			void iCanSearchMyPrescribingDoctor() {
				setupPractitioner(partitionName, "P2");

				mockContext.setFailVerifyConsultingDoctors(true);

				Bundle execute = searchPractitioner(partitionName, "P2");

				Resource resource = execute.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.Practitioner.class);
			}

			@Test
			void iCanSearchMyConsultingDoctor() {
				setupPractitioner(partitionName, "C2");

				mockContext.setFailVerifyPrescribingDoctors(true);

				Bundle execute = searchPractitioner(partitionName, "C2");

				Resource resource = execute.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.Practitioner.class);
			}

			@Test
			void iCanNotGetAnother() {
				String id = setupPractitioner(partitionName, "OTHER1");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				assertThatThrownBy(() -> {
					getPractitioner(partitionName, id);
				}).isInstanceOfAny(ResourceNotFoundException.class);
			}

			@Test
			void iCanNotSearchAnother() {
				setupPractitioner(partitionName, "OTHER2");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				Bundle actual = searchPractitioner(partitionName, "OTHER2");
				assertThat(actual.getEntry()).isEmpty();
			}

			@Test
			void iCanNotCreate() {
				assertThatThrownBy(() -> {
					createPractitioner(partitionName, "C3");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCanSearchAnotherOrganizationWhenIHaveSpecialPermissions() {
				setupPractitioner(partitionName, "OTHER3");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle actual = searchPractitioner(partitionName, "OTHER3");
				assertThat(actual.getEntry()).hasSize(1);
			}
		}

		@Nested
		class PractitionerRole {
			@Test
			void asAPrescribingDoctorICanGet() {
				setUpOrganization(partitionName, "account1");
				setupPractitioner(partitionName, "57761");
				String practitionerRoleId = setupPractitionerRole(partitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyMasterId(true);

				org.hl7.fhir.r4.model.PractitionerRole practitionerRole = getPractitionerRole(partitionName, practitionerRoleId);

				assertThat(practitionerRole).isNotNull();
			}

			@Test
			void asAConsultingDoctorICanGet() {
				setUpOrganization(partitionName, "account1");
				setupPractitioner(partitionName, "57761");
				String practitionerRoleId = setupPractitionerRole(partitionName, "account1", "57761");

				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyMasterId(true);

				org.hl7.fhir.r4.model.PractitionerRole practitionerRole = getPractitionerRole(partitionName, practitionerRoleId);

				assertThat(practitionerRole).isNotNull();
			}

			@Test
			void asAnOrganizationICanGet() {
				setUpOrganization(partitionName, "account1");
				setupPractitioner(partitionName, "57761");
				String practitionerRoleId = setupPractitionerRole(partitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				org.hl7.fhir.r4.model.PractitionerRole practitionerRole = getPractitionerRole(partitionName, practitionerRoleId);

				assertThat(practitionerRole).isNotNull();
			}

			@Test
			void asAPrescribingDoctorICanSearch() {
				setUpOrganization(partitionName, "account1");
				setupPractitioner(partitionName, "57761");
				setupPractitionerRole(partitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyMasterId(true);

				Bundle bundleResult = searchPractitionerRole(partitionName, "account1", "57761");

				Resource resource = bundleResult.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.PractitionerRole.class);
			}

			@Test
			void asAConsultingDoctorICanSearch() {
				setUpOrganization(partitionName, "account1");
				setupPractitioner(partitionName, "57761");
				setupPractitionerRole(partitionName, "account1", "57761");

				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyMasterId(true);

				Bundle bundleResult = searchPractitionerRole(partitionName, "account1", "57761");

				Resource resource = bundleResult.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.PractitionerRole.class);
			}

			@Test
			void asAnOrganizationICanSearch() {
				setUpOrganization(partitionName, "account1");
				setupPractitioner(partitionName, "57761");
				setupPractitionerRole(partitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				Bundle bundleResult = searchPractitionerRole(partitionName, "account1", "57761");

				Resource resource = bundleResult.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.PractitionerRole.class);
			}

			@Test
			void iCannotGetAnother() {
				setUpOrganization(partitionName, "account1");
				setupPractitioner(partitionName, "57761");
				String practitionerRoleId = setupPractitionerRole(partitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyMasterId(true);

				assertThatThrownBy(() -> {
					getPractitionerRole(partitionName, practitionerRoleId);
				}).isInstanceOfAny(ResourceNotFoundException.class);
			}


			@Test
			void iCannotSearchAnother() {
				setUpOrganization(partitionName, "account1");
				setupPractitioner(partitionName, "57761");
				setupPractitionerRole(partitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyMasterId(true);


				Bundle bundleResult = searchPractitionerRole(partitionName, "account1", "57761");
				assertThat(bundleResult.getEntry()).isEmpty();
			}

			@Test
			void iCannotCreate() {
				setUpOrganization(partitionName, "account1");
				setupPractitioner(partitionName, "57761");

				assertThatThrownBy(() -> {
					createPractitionerRole(partitionName, "account1", "57761");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCanSearchAnotherPractitionerRoleWhenIHaveSpecialPermissions() {
				setUpOrganization(partitionName, "account1");
				setupPractitioner(partitionName, "57761");
				setupPractitionerRole(partitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyMasterId(true);
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle bundleResult = searchPractitionerRole(partitionName, "account1", "57761");
				assertThat(bundleResult.getEntry()).hasSize(1);
			}
		}

		@Nested
		class Endpoint {

			@Test
			void asAPrescribingDoctorICanGet() {
				String endpointId = setupEndpoint(partitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);

				org.hl7.fhir.r4.model.Endpoint endpoint = getEndpoint(partitionName, endpointId);

				assertThat(endpoint).isNotNull();
			}

			@Test
			void asAConsultingDoctorICanGet() {
				String endpointId = setupEndpoint(partitionName, "account1", "57761");

				mockContext.setFailVerifyPrescribingDoctors(true);

				org.hl7.fhir.r4.model.Endpoint endpoint = getEndpoint(partitionName, endpointId);

				assertThat(endpoint).isNotNull();
			}

			@Test
			void asAPrescribingDoctorICanSearch() {
				String endpointId = setupEndpoint(partitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);

				Bundle bundle = searchEndpoint(partitionName, endpointId);

				assertThat(bundle.getEntry()).hasSize(1);
			}

			@Test
			void asAConsultingDoctorICanSearch() {
				String endpointId = setupEndpoint(partitionName, "account1", "57761");

				mockContext.setFailVerifyPrescribingDoctors(true);

				Bundle bundle = searchEndpoint(partitionName, endpointId);

				assertThat(bundle.getEntry()).hasSize(1);
			}

			@Test
			void iCannotGetAnother() {
				String endpointId = setupEndpoint(partitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				assertThatThrownBy(() -> {
					getEndpoint(partitionName, endpointId);
				}).isInstanceOfAny(ResourceNotFoundException.class);
			}

			@Test
			void iCannotSearchAnother() {
				String endpointId = setupEndpoint(partitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				assertThatThrownBy(() -> {
					getEndpoint(partitionName, endpointId);
				}).isInstanceOfAny(ResourceNotFoundException.class);
			}

			@Test
			void iCannotCreateAnEndpoint() {
				assertThatThrownBy(() -> {
					createEndpoint(partitionName, "account1", "57761");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}
		}

		@Nested
		class ServiceRequest {
			@Test
			void iCannotCreateSinceResourceShouldNotExistInThisPartition() {
				assertThatThrownBy(() -> {
					createServiceRequest(partitionName);
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCannotGetSinceResourceShouldNotExistInThisPartition() {
				String serviceRequestId = setupServiceRequest(partitionName, "123");
				assertThatThrownBy(() -> {
					getServiceRequest(partitionName, serviceRequestId);
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCannotSearchSinceResourceShouldNotExistInThisPartition() {
				setupServiceRequest(partitionName, "123");
				assertThatThrownBy(() -> {
					searchServiceRequest(partitionName, "123");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}
		}
	}

	@Nested
	class DoctorPartition {
		private String defaultDoctorPartitionName = "D57761";

		@Nested
		class Basic {
			@Test
			void iCanNotGet() {
				String id = setupBasic("Basic1");

				mockContext.setFailVerifyMasterId(true);

				assertThatThrownBy(() -> {
					getBasic(defaultDoctorPartitionName, id);
				}).isInstanceOfAny(ResourceNotFoundException.class, ForbiddenOperationException.class, AuthenticationException.class);
			}

			@Test
			void iCanNotSearch() {
				setupBasic("Basic2");

				assertThatThrownBy(() -> {
					searchBasic(defaultDoctorPartitionName, "Basic2");
				}).isInstanceOfAny(ResourceNotFoundException.class, ForbiddenOperationException.class, AuthenticationException.class);
			}

			@Test
			void iCanNotCreate() {
				setupBasic("Basic4");

				assertThatThrownBy(() -> {
					createBasic(defaultDoctorPartitionName, "Basic4");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCanSearchWhenIHaveSpecialPermissions() {
				setupBasic("Basic3");

				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle actual = searchBasic(defaultDoctorPartitionName, "Basic3");
				assertThat(actual.getEntry()).hasSize(1);
			}

			private String setupBasic(String systemValue) {
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				String idPart = createBasic(defaultDoctorPartitionName, systemValue);

				resetMockContext();

				return idPart;
			}
		}

		@Nested
		class Organization {
			@Test
			void iCannotGetSinceResourceShouldNotExistInThisPartition() {
				String id = setUpOrganization(defaultDoctorPartitionName, "ME1");

				assertThatThrownBy(() -> {
					getOrganization(defaultDoctorPartitionName, id);
				}).isInstanceOf(ForbiddenOperationException.class);
			}

			@Test
			void iCannotSearchSinceResourceShouldNotExistInThisPartition() {
				setUpOrganization(defaultDoctorPartitionName, "ME2");

				assertThatThrownBy(() -> {
					searchOrganization(defaultDoctorPartitionName, "ME2");
				}).isInstanceOf(ForbiddenOperationException.class);
			}

			@Test
			void iCanNotCreateMyOrganizationSinceResourceShouldNotExistInThisPartition() {
				assertThatThrownBy(() -> {
					createOrganization(defaultDoctorPartitionName, "ME3");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCanSearchAnotherOrganizationWhenIHaveSpecialPermissions() {
				setUpOrganization(defaultDoctorPartitionName, "OTHER3");

				mockContext.setFailVerifyMasterId(true);
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle actual = searchOrganization(defaultDoctorPartitionName, "OTHER3");
				assertThat(actual.getEntry()).hasSize(1);
			}

			@Test
			void iCanSearchAnOrganizationOfAnotherPartitionWhenIHaveSpecialPermissions_() {
				setUpOrganization(defaultDoctorPartitionName, "ME3");
				setUpOrganization("D666", "ME3");

				mockContext.setFailVerifyMasterId(true);
				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle actual = searchOrganization("D666", "ME3");
				assertThat(actual.getEntry()).hasSize(1);
			}
		}

		@Nested
		class Practitioner {
			@Test
			void iCanGetMyPrescribingDoctor() {
				String id = setupPractitioner(defaultDoctorPartitionName, "P1");

				mockContext.setFailVerifyConsultingDoctors(true);

				org.hl7.fhir.r4.model.Practitioner practitioner = getPractitioner(defaultDoctorPartitionName, id);

				assertThat(practitioner).isNotNull();
			}

			@Test
			void iCanGetMyConsultingDoctor() {
				String id = setupPractitioner(defaultDoctorPartitionName, "C1");

				mockContext.setFailVerifyPrescribingDoctors(true);

				org.hl7.fhir.r4.model.Practitioner practitioner = getPractitioner(defaultDoctorPartitionName, id);

				assertThat(practitioner).isNotNull();
			}

			@Test
			void iCanSearchMyPrescribingDoctor() {
				setupPractitioner(defaultDoctorPartitionName, "P2");

				mockContext.setFailVerifyConsultingDoctors(true);

				Bundle execute = searchPractitioner(defaultDoctorPartitionName, "P2");

				Resource resource = execute.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.Practitioner.class);
			}

			@Test
			void iCanSearchMyConsultingDoctor() {
				setupPractitioner(defaultDoctorPartitionName, "C2");

				mockContext.setFailVerifyPrescribingDoctors(true);

				Bundle execute = searchPractitioner(defaultDoctorPartitionName, "C2");

				Resource resource = execute.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.Practitioner.class);
			}


			@Test
			void iCanNotGetAnother() {
				String id = setupPractitioner(defaultDoctorPartitionName, "OTHER1");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				assertThatThrownBy(() -> {
					getPractitioner("D666", id);
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCanNotCreate() {
				assertThatThrownBy(() -> {
					createPractitioner(defaultDoctorPartitionName, "C3");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCanSearchAnotherOrganizationWhenIHaveSpecialPermissions() {
				setupPractitioner(defaultDoctorPartitionName, "OTHER3");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle actual = searchPractitioner(defaultDoctorPartitionName, "OTHER3");
				assertThat(actual.getEntry()).hasSize(1);
			}
		}

		@Nested
		class PractitionerRole {
			@Test
			void iCannotGetSinceResourceShouldNotExistInThisPartition() {
				setUpOrganization(defaultDoctorPartitionName, "account1");
				setupPractitioner(defaultDoctorPartitionName, "57761");
				String practitionerRoleId = setupPractitionerRole(defaultDoctorPartitionName, "account1", "57761");

				assertThatThrownBy(() -> {
					getPractitionerRole(defaultDoctorPartitionName, practitionerRoleId);
				}).isInstanceOf(ForbiddenOperationException.class);
			}

			@Test
			void iCannotSearchSinceResourceShouldNotExistInThisPartition() {
				setUpOrganization(defaultDoctorPartitionName, "account1");
				setupPractitioner(defaultDoctorPartitionName, "57761");
				setupPractitionerRole(defaultDoctorPartitionName, "account1", "57761");

				assertThatThrownBy(() -> {
					searchPractitionerRole(defaultDoctorPartitionName, "account1", "57761");
				}).isInstanceOf(ForbiddenOperationException.class);
			}

			@Test
			void iCannotCreate() {
				setUpOrganization(defaultDoctorPartitionName, "account1");
				setupPractitioner(defaultDoctorPartitionName, "57761");

				assertThatThrownBy(() -> {
					createPractitionerRole(defaultDoctorPartitionName, "account1", "57761");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCanSearchAnotherPractitionerRoleWhenIHaveSpecialPermissions_() {
				setUpOrganization(defaultDoctorPartitionName, "account1");
				setupPractitioner(defaultDoctorPartitionName, "57761");
				setupPractitionerRole(defaultDoctorPartitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyMasterId(true);
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle bundleResult = searchPractitionerRole(defaultDoctorPartitionName, "account1", "57761");
				assertThat(bundleResult.getEntry()).hasSize(1);
			}
		}

		@Nested
		class Endpoint {
			@Test
			void iCannotSearchSinceResourceShouldNotExistInThisPartition() {
				String endpointId = setupEndpoint(defaultDoctorPartitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyMasterId(true);

				assertThatThrownBy(() -> {
					searchEndpoint(defaultDoctorPartitionName, endpointId);
				}).isInstanceOf(ForbiddenOperationException.class);
			}

			@Test
			void iCannotGetSinceResourceShouldNotExistInThisPartition() {
				String endpointId = setupEndpoint(defaultDoctorPartitionName, "account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);

				assertThatThrownBy(() -> {
					getEndpoint(defaultDoctorPartitionName, endpointId);
				}).isInstanceOf(ForbiddenOperationException.class);
			}

			@Test
			void iCannotCreateSinceResourceShouldNotExistInThisPartition() {
				assertThatThrownBy(() -> {
					createEndpoint(defaultDoctorPartitionName, "account1", "57761");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}
		}

		@Nested
		class ServiceRequest {
			@Test
			void iCanCreate() {
				String serviceRequestId = createServiceRequest(defaultDoctorPartitionName);
				assertThat(serviceRequestId).isNotNull();
			}

			@Test
			void iCannotUpdate() {
				assertThatThrownBy(() -> {
					updateServiceRequest(defaultDoctorPartitionName, "123");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCanSearch() {
				setupServiceRequest(defaultDoctorPartitionName, "123");
				Bundle bundle = searchServiceRequest(defaultDoctorPartitionName, "123");
				assertThat(bundle.getEntry()).hasSize(1);
			}

			@Test
			void iCanGet() {
				String serviceRequestId = setupServiceRequest(defaultDoctorPartitionName, "123");
				org.hl7.fhir.r4.model.ServiceRequest serviceRequest = getServiceRequest(defaultDoctorPartitionName, serviceRequestId);
				assertThat(serviceRequest).isNotNull();
			}

			@Test
			void iCannotGetAnother() {
				String serviceRequestId = setupServiceRequest(defaultDoctorPartitionName, "123");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				assertThatThrownBy(() -> {
					getServiceRequest(defaultDoctorPartitionName, serviceRequestId);
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void iCannotSearchAnother() {
				setupServiceRequest(defaultDoctorPartitionName, "123");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				assertThatThrownBy(() -> {
					searchServiceRequest(defaultDoctorPartitionName, "123");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

		}
	}


	private boolean hasPartition(int id) {
		Parameters parameters = new Parameters()
			.addParameter("id", new IntegerType(id));
		try {
			getAuthenticatedClient("DEFAULT")
				.operation()
				.onServer()
				.named("$partition-management-read-partition")
				.withParameters(parameters)
				.cacheControl(CacheControlDirective.noCache())
				.execute();
			return true;
		} catch (ResourceNotFoundException e) {
			return false;
		}
	}

	private void removeAllResourcesFromServer() {
		Parameters parameters = new Parameters()
			.addParameter("expungeEverything", true);
		getAuthenticatedClient("DEFAULT")
			.operation()
			.onServer()
			.named("$expunge")
			.withParameters(parameters)
			.execute();
	}

	private void createPartition(int id, String name) {
		Parameters parameters = new Parameters()
			.addParameter("id", new IntegerType(id))
			.addParameter("name", name);

		getAuthenticatedClient("DEFAULT")
			.operation()
			.onServer()
			.named("$partition-management-create-partition")
			.withParameters(parameters)
			.cacheControl(CacheControlDirective.noCache())
			.withAdditionalHeader("Authorization", "Dummy")
			.execute();
	}

	private IGenericClient getUnauthenticatedClient(String partition) {
		String ourServerBase = "http://localhost:" + port + "/fhir/" + partition;

		IGenericClient client = ctx.newRestfulGenericClient(ourServerBase);
		client.registerInterceptor(new LoggingInterceptor(true));

		return client;
	}

	private IGenericClient getAuthenticatedClient(String partition) {
		IGenericClient client = getUnauthenticatedClient(partition);
		client.registerInterceptor(new BasicAuthInterceptor("", ""));

		return client;
	}

	private void resetMockContext() {
		mockContext.setMasterId(null);
		mockContext.setUserShortId(null);
		mockContext.setPatientId(null);
		mockContext.setFailVerifyMasterId(false);
		mockContext.setFailVerifyConsultingDoctors(false);
		mockContext.setFailVerifyPrescribingDoctors(false);

		mockContext.setMyId(null);
		mockContext.setFailVerifyPatientId(false);

		mockContext.setPermissions(new HashSet<>());
	}
}
