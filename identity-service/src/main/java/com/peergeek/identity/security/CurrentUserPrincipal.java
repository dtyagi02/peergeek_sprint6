package com.peergeek.identity.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import com.peergeek.identity.entity.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CurrentUserPrincipal implements Principal, UserDetails, Serializable {
	private final Long id;
	private final UserRole role;
	private final List<GrantedAuthority> authorities;

	public CurrentUserPrincipal(Long id, UserRole role) {
		this.id = id;
		this.role = role != null ? role : UserRole.USER;
		this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
	}

	public Long getId() {
		return id;
	}

	public UserRole getRole() {
		return role;
	}

	@Override
	public String getName() {
		return String.valueOf(id);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return null;
	}

	@Override
	public String getUsername() {
		return String.valueOf(id);
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}

