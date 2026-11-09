package com.peergeek.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import com.peergeek.audit.AuditAction;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.config.BootstrapAdminSettings;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
	private final BootstrapAdminSettings settings;
	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final AuditService auditService;
	private final com.peergeek.wallet.WalletService walletService;

	public BootstrapAdminInitializer(
			BootstrapAdminSettings settings, UserRepository users,
			PasswordEncoder passwordEncoder, AuditService auditService,
			com.peergeek.wallet.WalletService walletService) {
		this.settings = settings;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.auditService = auditService;
		this.walletService = walletService;
	}

	@Override
	public void run(ApplicationArguments args) {
		String email = settings.getEmail();
		String password = settings.getPassword();
		boolean emailConfigured = email != null && !email.isBlank();
		boolean passwordConfigured = password != null && !password.isBlank();
		if (!emailConfigured && !passwordConfigured) return;
		if (!emailConfigured || !passwordConfigured) {
			throw new IllegalStateException("Both bootstrap admin credentials must be configured together");
		}
		if (users.existsByRole(UserRole.ADMIN)) return;
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		if (password.length() < 8 || password.length() > 72
				|| password.getBytes(StandardCharsets.UTF_8).length < 8
				|| password.getBytes(StandardCharsets.UTF_8).length > 72
				|| password.equalsIgnoreCase(normalizedEmail)) {
			throw new IllegalStateException("Bootstrap admin password does not satisfy the password policy");
		}
		UserEntity user = users.findByEmail(normalizedEmail).orElse(null);
		if (user == null) {
			user = new UserEntity(normalizedEmail, passwordEncoder.encode(password), "PeerGeek Admin", "peergeek_admin");
		} else {
			user.updatePasswordHash(passwordEncoder.encode(password));
			if (user.getUsername() == null) {
				user.setUsername("peergeek_admin");
			}
		}
		user.setRole(UserRole.ADMIN);
		UserEntity saved = users.saveAndFlush(user);
		walletService.getOrCreateWallet(saved);
		auditService.record(new AuditEvent(saved.getId(), AuditAction.AUTH_BOOTSTRAP_ADMIN_CREATED,
				"USER", Long.toString(saved.getId()), Map.of()));
	}
}
