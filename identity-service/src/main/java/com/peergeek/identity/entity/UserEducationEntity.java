package com.peergeek.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_educations")
public class UserEducationEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 200)
	private String institution;

	@Column(length = 100)
	private String degree;

	@Column(name = "field_of_study", length = 100)
	private String fieldOfStudy;

	@Column(name = "start_year")
	private Integer startYear;

	@Column(name = "end_year")
	private Integer endYear;

	public UserEducationEntity() {
	}

	public UserEducationEntity(Long userId, String institution, String degree, String fieldOfStudy, Integer startYear, Integer endYear) {
		this.userId = userId;
		this.institution = institution;
		this.degree = degree;
		this.fieldOfStudy = fieldOfStudy;
		this.startYear = startYear;
		this.endYear = endYear;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getInstitution() {
		return institution;
	}

	public void setInstitution(String institution) {
		this.institution = institution;
	}

	public String getDegree() {
		return degree;
	}

	public void setDegree(String degree) {
		this.degree = degree;
	}

	public String getFieldOfStudy() {
		return fieldOfStudy;
	}

	public void setFieldOfStudy(String fieldOfStudy) {
		this.fieldOfStudy = fieldOfStudy;
	}

	public Integer getStartYear() {
		return startYear;
	}

	public void setStartYear(Integer startYear) {
		this.startYear = startYear;
	}

	public Integer getEndYear() {
		return endYear;
	}

	public void setEndYear(Integer endYear) {
		this.endYear = endYear;
	}
}

