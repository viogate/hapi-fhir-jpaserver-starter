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
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SecurityIT {

	//TODO service request doctor mag alleen post doen geen put
	@Autowired
	private MockContext mockContext;

	@LocalServerPort
	private int port;

	private final static FhirContext ctx = FhirContext.forR4();

	@BeforeEach
	void beforeEach() {
		ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);

		mockContext.setPermissions(Set.of("FHIR_ALL"));
		if (!hasPartition(-1)) {
			createPartition(-1, "root");
		}
		if (!hasPartition(57761)) {
			createPartition(57761, "D57761");
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

		private String getFullOrganizationId(String organizationIdPart) {
			return String.format("Organization/%s-%s", "root", organizationIdPart);
		}

		private String getFullPractitionerId(String practitionerIdPart) {
			return String.format("Practitioner/%s-%s", "root", practitionerIdPart);
		}

		private String setUpOrganization(String masterId) {
			mockContext.setPermissions(Set.of("FHIR_ALL"));

			String id = createOrganization(masterId);

			resetMockContext();

			return id;
		}

		private String createOrganization(String masterId) {
			org.hl7.fhir.r4.model.Organization organization = new org.hl7.fhir.r4.model.Organization();
			Identifier id = new Identifier();
			id.setSystem("http://viollier.ch/fhir/system/master-id");
			id.setValue(masterId);
			organization.addIdentifier(id);
			String organizationId = getFullOrganizationId(masterId);
			organization.setId(organizationId);

			return getAuthenticatedClient("root")
				.update()
				.resource(organization)
				.execute()
				.getId()
				.getIdPart();
		}

		private String setupPractitioner(String archiveNumber) {
			mockContext.setPermissions(Set.of("FHIR_ALL"));

			String idPart = createPractitioner(archiveNumber);

			resetMockContext();

			return idPart;
		}

		private String createPractitioner(String archiveNumber) {
			org.hl7.fhir.r4.model.Practitioner practitioner = new org.hl7.fhir.r4.model.Practitioner();
			Identifier id = new Identifier();
			id.setSystem("http://viollier.ch/fhir/system/archive-number");
			id.setValue(archiveNumber);
			practitioner.addIdentifier(id);

			String fullPractitionerId = getFullPractitionerId(archiveNumber);
			practitioner.setId(fullPractitionerId);

			String idPart = getAuthenticatedClient("root")
				.update()
				.resource(practitioner)
				.execute()
				.getId()
				.getIdPart();
			return idPart;
		}

		@Nested
		class Basic {
			@Test
			void normalAccountsCanNotGetBasic() {
				String id = setupBasic("Basic1");

				mockContext.setFailVerifyMasterId(true);

				assertThatThrownBy(() -> {
					getBasic(id);
				}).isInstanceOfAny(ResourceNotFoundException.class, ForbiddenOperationException.class, AuthenticationException.class);
			}

			@Test
			void normalAccountsCanNotSearchBasic() {
				setupBasic("Basic2");

				assertThatThrownBy(() -> {
					searchBasic("Basic2");
				}).isInstanceOfAny(ResourceNotFoundException.class, ForbiddenOperationException.class, AuthenticationException.class);
			}

			@Test
			void normalAccountsCanNotCreateBasic() {
				setupBasic("Basic4");

				assertThatThrownBy(() -> {
					createBasic("Basic4");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void whenIHaveSpecialPermissions_thenICanSearchBasics() {
				setupBasic("Basic3");

				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle actual = searchBasic("Basic3");
				assertThat(actual.getEntry()).hasSize(1);
			}

//			@Test
//			void normalAccountsCannotSeeOtherAccounts() {
//				setupBasic("Basic4");
//
//				assertThatThrownBy(() -> {
//					searchOrganization("Organization1");
//				}).isInstanceOfAny(ForbiddenOperationException.class);
//			}

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

			private org.hl7.fhir.r4.model.Basic getBasic(String id) {
				return getAuthenticatedClient("root")
					.read()
					.resource(org.hl7.fhir.r4.model.Basic.class)
					.withId(id)
					.cacheControl(CacheControlDirective.noCache())
					.execute();
			}

			private Bundle searchBasic(String id) {
				return getAuthenticatedClient("root")
					.<Bundle>search()
					.forResource(org.hl7.fhir.r4.model.Basic.class)
					.where(org.hl7.fhir.r4.model.Basic.IDENTIFIER.exactly().systemAndIdentifier("dummy", id))
					.cacheControl(CacheControlDirective.noCache())
					.execute();
			}
		}

		@Nested
		class Organization {
			@Test
			void iCanGetMyOrganization() {
				String id = setUpOrganization("ME1");

				org.hl7.fhir.r4.model.Organization organization = getOrganization(id);

				assertThat(organization).isNotNull();
			}

			@Test
			void iCanSearchMyOrganization() {
				setUpOrganization("ME2");

				Bundle execute = searchOrganization("ME2");

				Resource resource = execute.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.Organization.class);
			}

			@Test
			void iCanNotGetAnotherOrganization() {
				String id = setUpOrganization("OTHER1");

				mockContext.setFailVerifyMasterId(true);

				assertThatThrownBy(() -> {
					getOrganization(id);
				}).isInstanceOfAny(ResourceNotFoundException.class);
			}

			@Test
			void iCanNotSearchAnotherOrganization() {
				setUpOrganization("OTHER2");

				mockContext.setFailVerifyMasterId(true);

				Bundle actual = searchOrganization("OTHER2");
				assertThat(actual.getEntry()).isEmpty();
			}

			@Test
			void iCanNotCreateMyOrganization() {
				assertThatThrownBy(() -> {
					createOrganization("ME3");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void whenIHaveSpecialPermissions_thenICanSearchAnotherOrganization() {
				setUpOrganization("OTHER3");

				mockContext.setFailVerifyMasterId(true);
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle actual = searchOrganization("OTHER3");
				assertThat(actual.getEntry()).hasSize(1);
			}

			private org.hl7.fhir.r4.model.Organization getOrganization(String id) {
				return getAuthenticatedClient("root")
					.read()
					.resource(org.hl7.fhir.r4.model.Organization.class)
					.withId(id)
					.cacheControl(CacheControlDirective.noCache())
					.execute();
			}

			private Bundle searchOrganization(String masterId) {
				return getAuthenticatedClient("root")
					.<Bundle>search()
					.forResource(org.hl7.fhir.r4.model.Organization.class)
					.where(org.hl7.fhir.r4.model.Organization.IDENTIFIER.exactly().systemAndIdentifier("http://viollier.ch/fhir/system/master-id", masterId))
					.cacheControl(CacheControlDirective.noCache())
					.execute();
			}
		}

		@Nested
		class Practitioner {
			@Test
			void iCanGetMyPrescribingDoctor() {
				String id = setupPractitioner("P1");

				mockContext.setFailVerifyConsultingDoctors(true);

				org.hl7.fhir.r4.model.Practitioner practitioner = getPractitioner(id);

				assertThat(practitioner).isNotNull();
			}

			@Test
			void iCanGetMyConsultingDoctor() {
				String id = setupPractitioner("C1");

				mockContext.setFailVerifyPrescribingDoctors(true);

				org.hl7.fhir.r4.model.Practitioner practitioner = getPractitioner(id);

				assertThat(practitioner).isNotNull();
			}

			@Test
			void iCanSearchMyPrescribingDoctor() {
				setupPractitioner("P2");

				mockContext.setFailVerifyConsultingDoctors(true);

				Bundle execute = searchPractitioner("P2");

				Resource resource = execute.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.Practitioner.class);
			}

			@Test
			void iCanSearchMyConsultingDoctor() {
				setupPractitioner("C2");

				mockContext.setFailVerifyPrescribingDoctors(true);

				Bundle execute = searchPractitioner("C2");

				Resource resource = execute.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.Practitioner.class);
			}

			@Test
			void iCanNotGetAnotherPractitioner() {
				String id = setupPractitioner("OTHER1");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				assertThatThrownBy(() -> {
					getPractitioner(id);
				}).isInstanceOfAny(ResourceNotFoundException.class);
			}

			@Test
			void iCanNotSearchAnotherPractitioner() {
				setupPractitioner("OTHER2");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				Bundle actual = searchPractitioner("OTHER2");
				assertThat(actual.getEntry()).isEmpty();
			}

			@Test
			void iCanNotCreateMyDoctor() {
				assertThatThrownBy(() -> {
					createPractitioner("C3");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void whenIHaveSpecialPermissions_thenICanSearchAnotherOrganization() {
				setupPractitioner("OTHER3");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle actual = searchPractitioner("OTHER3");
				assertThat(actual.getEntry()).hasSize(1);
			}

			private org.hl7.fhir.r4.model.Practitioner getPractitioner(String id) {
				return getAuthenticatedClient("root")
					.read()
					.resource(org.hl7.fhir.r4.model.Practitioner.class)
					.withId(id)
					.cacheControl(CacheControlDirective.noCache())
					.execute();
			}

			private Bundle searchPractitioner(String masterId) {
				return getAuthenticatedClient("root")
					.<Bundle>search()
					.forResource(org.hl7.fhir.r4.model.Practitioner.class)
					.where(org.hl7.fhir.r4.model.Practitioner.IDENTIFIER.exactly().systemAndIdentifier("http://viollier.ch/fhir/system/archive-number", masterId))
					.cacheControl(CacheControlDirective.noCache())
					.execute();
			}
		}

		@Nested
		class PractitionerRole {
			@Test
			void asAPrescribingDoctorICanGetMyLinkToMyOrganization() {
				setUpOrganization("account1");
				setupPractitioner("57761");
				String practitionerRoleId = setupPractitionerRole("account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyMasterId(true);

				org.hl7.fhir.r4.model.PractitionerRole practitionerRole = getPractitionerRole(practitionerRoleId);

				assertThat(practitionerRole).isNotNull();
			}

			@Test
			void asAConsultingDoctorICanGetMyLinkToMyOrganization() {
				setUpOrganization("account1");
				setupPractitioner("57761");
				String practitionerRoleId = setupPractitionerRole("account1", "57761");

				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyMasterId(true);

				org.hl7.fhir.r4.model.PractitionerRole practitionerRole = getPractitionerRole(practitionerRoleId);

				assertThat(practitionerRole).isNotNull();
			}

			@Test
			void asAnOrganizationICanGetMyLinkToMyDoctors() {
				setUpOrganization("account1");
				setupPractitioner("57761");
				String practitionerRoleId = setupPractitionerRole("account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				org.hl7.fhir.r4.model.PractitionerRole practitionerRole = getPractitionerRole(practitionerRoleId);

				assertThat(practitionerRole).isNotNull();
			}

			@Test
			void asAPrescribingDoctorICanSearchMyLinkToMyOrganization() {
				setUpOrganization("account1");
				setupPractitioner("57761");
				setupPractitionerRole("account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyMasterId(true);

				Bundle bundleResult = searchPractitionerRole("account1", "57761");

				Resource resource = bundleResult.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.PractitionerRole.class);
			}

			@Test
			void asAConsultingDoctorICanSearchMyLinkToMyOrganization() {
				setUpOrganization("account1");
				setupPractitioner("57761");
				setupPractitionerRole("account1", "57761");

				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyMasterId(true);

				Bundle bundleResult = searchPractitionerRole("account1", "57761");

				Resource resource = bundleResult.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.PractitionerRole.class);
			}

			@Test
			void asAnOrganizationICanSearchMyLinkToMyDoctors() {
				setUpOrganization("account1");
				setupPractitioner("57761");
				setupPractitionerRole("account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				Bundle bundleResult =  searchPractitionerRole("account1", "57761");

				Resource resource = bundleResult.getEntryFirstRep().getResource();
				assertThat(resource).isInstanceOf(org.hl7.fhir.r4.model.PractitionerRole.class);
			}

			@Test
			void iCannotGetADoctorOrganisationLinkOfAnotherDoctorOrAnotherOrganisation() {
				setUpOrganization("account1");
				setupPractitioner("57761");
				String practitionerRoleId = setupPractitionerRole("account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyMasterId(true);

				assertThatThrownBy(() -> {
					getPractitionerRole(practitionerRoleId);
				}).isInstanceOfAny(ResourceNotFoundException.class);
			}


			@Test
			void iCannotSearchADoctorOrganisationLinkOfAnotherDoctorOrAnotherOrganisation() {
				setUpOrganization("account1");
				setupPractitioner("57761");
				setupPractitionerRole("account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyMasterId(true);


				Bundle bundleResult = searchPractitionerRole("account1", "57761");
				assertThat(bundleResult.getEntry()).isEmpty();
			}

			@Test
			void iCannotCreateADoctorOrganizationLink() {
				setUpOrganization("account1");
				setupPractitioner("57761");

				assertThatThrownBy(() -> {
					createPractitionerRole("account1", "57761");
				}).isInstanceOfAny(ForbiddenOperationException.class);
			}

			@Test
			void whenIHaveSpecialPermissions_thenICanSearchAnotherPractitionerRole() {
				setUpOrganization("account1");
				setupPractitioner("57761");
				setupPractitionerRole("account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);
				mockContext.setFailVerifyMasterId(true);
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				Bundle bundleResult = searchPractitionerRole("account1", "57761");
				assertThat(bundleResult.getEntry()).hasSize(1);
			}

			private String setupPractitionerRole(String accountId, String archiveNumber) {
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				String idPart = createPractitionerRole(accountId, archiveNumber);

				resetMockContext();

				return idPart;
			}

			private String createPractitionerRole(String organizationIdPart, String practitionerIdPart) {
				org.hl7.fhir.r4.model.PractitionerRole practitionerRole = new org.hl7.fhir.r4.model.PractitionerRole();
				String organizationId = getFullOrganizationId(organizationIdPart);
				String practitionerId = getFullPractitionerId(practitionerIdPart);
				practitionerRole.setOrganization(new Reference(organizationId));
				practitionerRole.setPractitioner(new Reference(practitionerId));

				String idPart = getAuthenticatedClient("root")
					.create()
					.resource(practitionerRole)
					.execute()
					.getId()
					.getIdPart();
				return idPart;
			}

			private org.hl7.fhir.r4.model.PractitionerRole getPractitionerRole(String id) {
				return getAuthenticatedClient("root")
					.read()
					.resource(org.hl7.fhir.r4.model.PractitionerRole.class)
					.withId(id)
					.cacheControl(CacheControlDirective.noCache())
					.execute();
			}

			private Bundle searchPractitionerRole(String masterId, String archiveNumber) {
				return getAuthenticatedClient("root")
					.<Bundle>search()
					.forResource(org.hl7.fhir.r4.model.PractitionerRole.class)
					.where(org.hl7.fhir.r4.model.PractitionerRole.ORGANIZATION.hasChainedProperty(org.hl7.fhir.r4.model.Organization.IDENTIFIER.exactly().systemAndIdentifier("http://viollier.ch/fhir/system/master-id", masterId)))
					.where(org.hl7.fhir.r4.model.PractitionerRole.PRACTITIONER.hasChainedProperty(org.hl7.fhir.r4.model.Practitioner.IDENTIFIER.exactly().systemAndIdentifier("http://viollier.ch/fhir/system/archive-number", archiveNumber)))
					.cacheControl(CacheControlDirective.noCache())
					.execute();
			}
		}

		@Nested
		class Endpoint {
			private String setupEndpoint(String masterId, String archiveNumber) {
				mockContext.setPermissions(Set.of("FHIR_ALL"));

				String idPart = createEndpoint(masterId, archiveNumber);

				resetMockContext();

				return idPart;
			}

			private String createEndpoint(String masterId, String archiveNumber) {
				String endpointId = String.format("Endpoint/%s-%s", masterId, archiveNumber);
				org.hl7.fhir.r4.model.Endpoint endpoint = new org.hl7.fhir.r4.model.Endpoint();
				endpoint.setId(endpointId);
				String address = String.format("https://api.dev.viollier.ch/fhir/r4/D%s", archiveNumber);
				endpoint.setAddress(address);

				String idPart = getAuthenticatedClient("root")
					.create()
					.resource(endpoint)
					.execute()
					.getId()
					.getIdPart();
				return idPart;
			}

			private org.hl7.fhir.r4.model.Endpoint getEndpoint(String id) {
				return getAuthenticatedClient("root")
					.read()
					.resource(org.hl7.fhir.r4.model.Endpoint.class)
					.withId(id)
					.cacheControl(CacheControlDirective.noCache())
					.execute();
			}

			@Test
			void asAPrescribingDoctorICanGetMyEndpoint() {
				String endpointId = setupEndpoint("account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);

				org.hl7.fhir.r4.model.Endpoint endpoint = getEndpoint(endpointId);

				assertThat(endpoint).isNotNull();
			}

			@Test
			void asAConsultingDoctorICanGetMyEndpoint() {
				String endpointId = setupEndpoint("account1", "57761");

				mockContext.setFailVerifyPrescribingDoctors(true);

				org.hl7.fhir.r4.model.Endpoint endpoint = getEndpoint(endpointId);

				assertThat(endpoint).isNotNull();
			}

			@Test
			void iCannotGetAnEndpointBelongingToAnotherDoctor() {
				String endpointId = setupEndpoint("account1", "57761");

				mockContext.setFailVerifyConsultingDoctors(true);
				mockContext.setFailVerifyPrescribingDoctors(true);

				assertThatThrownBy(() -> {
					getEndpoint(endpointId);
				}).isInstanceOfAny(ResourceNotFoundException.class);
			}

			@Test
			void iCannotCreateAnEndpoint() {
				assertThatThrownBy(() -> {
					createEndpoint("account1", "57761");
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
