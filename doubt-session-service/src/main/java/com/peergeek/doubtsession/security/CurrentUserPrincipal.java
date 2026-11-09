package com.peergeek.doubtsession.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CurrentUserPrincipal implements Principal, UserDetails, Serializable {
	private final Long id;
	private final String role;
	private final List<GrantedAuthority> authorities;

	public CurrentUserPrincipal(Long id, String role) {
		this.id = id;
		this.role = role != null ? role : "USER";
		this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + this.role));
	}

	public Long getId() {
		return id;
	}

	public String getRole() {
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

