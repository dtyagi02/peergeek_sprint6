package com.peergeek.courselearning.profile;

import com.peergeek.courselearning.common.UserMode;
import com.peergeek.courselearning.common.UserRole;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "learning_user_profiles")
public class LearningUserProfileEntity {

    @Id
    private Long id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 50)
    private UserMode mode = UserMode.STUDENT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected LearningUserProfileEntity() {}

    public LearningUserProfileEntity(Long id, String email, String username, String displayName, String avatarUrl, UserRole role, UserMode mode) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.role = role != null ? role : UserRole.USER;
        this.mode = mode != null ? mode : UserMode.STUDENT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName : (username != null ? username : "user_" + id);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

