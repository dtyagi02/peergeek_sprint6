package com.peergeek.doubtsession;

import java.time.Instant;
import com.peergeek.common.BaseEntity;
import com.peergeek.course.CourseEntity;
import com.peergeek.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_doubt_session_settings")
public class CourseDoubtSessionSettingsEntity extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false, unique = true)
	private CourseEntity course;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "instructor_id", nullable = false)
	private UserEntity instructor;

	@Column(nullable = false)
	private boolean enabled = false;

	@Column(nullable = false)
	private boolean available = false;

	@Column(name = "hourly_rate_pgc", nullable = false)
	private int hourlyRatePgc = 600;

	protected CourseDoubtSessionSettingsEntity() {
	}

	public CourseDoubtSessionSettingsEntity(CourseEntity course, UserEntity instructor) {
		this.course = course;
		this.instructor = instructor;
		this.enabled = false;
		this.available = false;
		this.hourlyRatePgc = 600;
	}

	public CourseDoubtSessionSettingsEntity(
			CourseEntity course,
			UserEntity instructor,
			boolean enabled,
			boolean available,
			int hourlyRatePgc) {
		this.course = course;
		this.instructor = instructor;
		this.enabled = enabled;
		this.available = available;
		this.hourlyRatePgc = Math.max(1, hourlyRatePgc);
	}

	public CourseEntity getCourse() {
		return course;
	}

	public void setCourse(CourseEntity course) {
		this.course = course;
	}

	public UserEntity getInstructor() {
		return instructor;
	}

	public void setInstructor(UserEntity instructor) {
		this.instructor = instructor;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public int getHourlyRatePgc() {
		return hourlyRatePgc;
	}

	public void setHourlyRatePgc(int hourlyRatePgc) {
		this.hourlyRatePgc = Math.max(1, hourlyRatePgc);
	}
}
