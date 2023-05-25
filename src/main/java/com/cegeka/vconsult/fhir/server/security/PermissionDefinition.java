package com.cegeka.vconsult.fhir.server.security;

import be.cegeka.vconsult.security.api.PermissionMigrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PermissionDefinition {
	@Bean
	public PermissionMigrations permissionMigration() {
		return new PermissionMigrations()
			.owner("fhir-sync")
			.upsert(Permission.FHIR_ALL, 0, "view and edit all", Permission.INTERNAL_GROUP).disableAutoAssign().butDoAssignTo();
	}
}
