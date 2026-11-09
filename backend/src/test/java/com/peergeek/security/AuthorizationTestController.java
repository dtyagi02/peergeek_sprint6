package com.peergeek.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test/security")
class AuthorizationTestController {
	@GetMapping("/protected")
	String protectedEndpoint() {
		return "authenticated";
	}

	@GetMapping("/admin")
	@PreAuthorize("hasRole('ADMIN')")
	String adminEndpoint() {
		return "admin";
	}
}
