package com.peergeek.doubtsession.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_doubt_session_settings")
public class CourseDoubtSessionSettingsEntity extends BaseEntity {

	@Column(name = "course_id", nullable = false, unique = true)
	private Long courseId;

	@Column(name = "instructor_id", nullable = false)
	private Long instructorId;

	@Column(nullable = false)
	private boolean enabled = false;

	@Column(nullable = false)
	private boolean available = false;

	@Column(name = "hourly_rate_pgc", nullable = false)
	private int hourlyRatePgc = 600;

	public CourseDoubtSessionSettingsEntity() {
	}

	public CourseDoubtSessionSettingsEntity(Long courseId, Long instructorId) {
		this.courseId = courseId;
		this.instructorId = instructorId;
		this.enabled = false;
		this.available = false;
		this.hourlyRatePgc = 600;
	}

	public CourseDoubtSessionSettingsEntity(
			Long courseId,
			Long instructorId,
			boolean enabled,
			boolean available,
			int hourlyRatePgc) {
		this.courseId = courseId;
		this.instructorId = instructorId;
		this.enabled = enabled;
		this.available = available;
		this.hourlyRatePgc = Math.max(1, hourlyRatePgc);
	}

	public Long getCourseId() {
		return courseId;
	}

	public void setCourseId(Long courseId) {
		this.courseId = courseId;
	}

	public Long getInstructorId() {
		return instructorId;
	}

	public void setInstructorId(Long instructorId) {
		this.instructorId = instructorId;
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

