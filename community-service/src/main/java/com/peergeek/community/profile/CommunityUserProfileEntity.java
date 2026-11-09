package com.peergeek.community.profile;

import com.peergeek.community.common.UserMode;
import com.peergeek.community.common.UserRole;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "community_user_profiles")
public class CommunityUserProfileEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private UserRole role = UserRole.LEARNER;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 50)
    private UserMode mode = UserMode.LEARNER;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CommunityUserProfileEntity() {}

    public CommunityUserProfileEntity(
            Long userId,
            String fullName,
            String username,
            String email,
            UserRole role,
            UserMode mode,
            String avatarUrl,
            boolean enabled) {
        this.userId = userId;
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.role = role != null ? role : UserRole.LEARNER;
        this.mode = mode != null ? mode : UserMode.LEARNER;
        this.avatarUrl = avatarUrl;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDisplayName() {
        return fullName != null && !fullName.isBlank() ? fullName : username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserMode getMode() {
        return mode;
    }

    public void setMode(UserMode mode) {
        this.mode = mode;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

