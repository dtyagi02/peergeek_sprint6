package com.peergeek.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/test/errors")
class ErrorTestController {

	@GetMapping("/not-found")
	void notFound() {
		throw new NotFoundException("Test resource was not found");
	}

	@GetMapping("/forbidden")
	void forbidden() {
		throw new ForbiddenException("Test access is forbidden");
	}

	@GetMapping("/conflict")
	void conflict() {
		throw new ConflictException("Test conflict");
	}

	@GetMapping("/bad-request")
	void badRequest() {
		throw new BadRequestException("Test request is invalid");
	}

	@PostMapping("/validation")
	void validation(@Valid @RequestBody TestRequest request) {
	}

	@GetMapping("/constraint")
	void constraint(@RequestParam @Min(1) int limit) {
	}

	@GetMapping("/type-mismatch/{value}")
	void typeMismatch(@PathVariable int value) {
	}

	@GetMapping("/unexpected")
	void unexpected() {
		throw new IllegalStateException("internal detail must not be returned");
	}

	@PostMapping("/method-not-allowed")
	void methodNotAllowed() {
	}

	record TestRequest(@NotBlank String name) {
	}
}
