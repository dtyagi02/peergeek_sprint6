package com.peergeek.identity.controller;

import java.util.List;
import com.peergeek.identity.dto.AdminUserDto;
import com.peergeek.identity.dto.PageResponse;
import com.peergeek.identity.dto.UpdateUserStatusRequest;
import com.peergeek.identity.entity.UserEntity;
import com.peergeek.identity.entity.UserMode;
import com.peergeek.identity.entity.UserRole;
import com.peergeek.identity.exception.BadRequestException;
import com.peergeek.identity.exception.NotFoundException;
import com.peergeek.identity.repository.RefreshTokenRepository;
import com.peergeek.identity.repository.UserRepository;
import com.peergeek.identity.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
	private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final CurrentUserProvider currentUserProvider;

	public AdminUserController(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			CurrentUserProvider currentUserProvider) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/users")
	@Transactional(readOnly = true)
	public ResponseEntity<PageResponse<AdminUserDto>> getUsers(
			@RequestParam(value = "role", required = false) UserRole role,
			@RequestParam(value = "mode", required = false) UserMode mode,
			@RequestParam(value = "enabled", required = false) Boolean enabled,
			@RequestParam(value = "query", required = false) String query,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));
		Pageable pageable = PageRequest.of(safePage, safeSize);
		String safeQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;

		Page<UserEntity> userPage = userRepository.findAdminUsers(role, mode, enabled, safeQuery, pageable);

		List<AdminUserDto> dtos = userPage.getContent().stream().map(user -> new AdminUserDto(
				user.getId(),
				user.getEmail(),
				user.getDisplayName(),
				user.getRole(),
				user.getLastMode(),
				user.isEnabled(),
				user.isEmailVerified(),
				user.getCreatedAt())).toList();

		return ResponseEntity.ok(new PageResponse<>(
				dtos,
				userPage.getNumber(),
				userPage.getSize(),
				userPage.getTotalElements(),
				userPage.getTotalPages()));
	}

	@PatchMapping("/users/{userId}/status")
	@Transactional
	public ResponseEntity<AdminUserDto> updateUserStatus(
			@PathVariable("userId") Long userId,
			@Valid @RequestBody UpdateUserStatusRequest request) {
		long adminUserId = currentUserProvider.requireCurrentUser().id();
		if (userId.equals(adminUserId)) {
			throw new BadRequestException("Administrators cannot block their own account");
		}

		UserEntity user = userRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("User not found"));

		if (user.getRole() == UserRole.ADMIN) {
			throw new BadRequestException("Cannot block administrator accounts");
		}

		boolean previousEnabled = user.isEnabled();
		user.setEnabled(request.enabled());
		user = userRepository.save(user);

		if (!request.enabled()) {
			refreshTokenRepository.revokeAllByUserId(userId);
			log.info("Admin userId={} blocked user account targetUserId={} reason={}",
					adminUserId, userId, request.reason());
		} else if (!previousEnabled && request.enabled()) {
			log.info("Admin userId={} unblocked user account targetUserId={} reason={}",
					adminUserId, userId, request.reason());
		}

		return ResponseEntity.ok(new AdminUserDto(
				user.getId(),
				user.getEmail(),
				user.getDisplayName(),
				user.getRole(),
				user.getLastMode(),
				user.isEnabled(),
				user.isEmailVerified(),
				user.getCreatedAt()));
	}
}

