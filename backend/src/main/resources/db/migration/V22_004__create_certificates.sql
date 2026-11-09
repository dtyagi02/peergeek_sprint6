CREATE TABLE certificates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    certificate_number VARCHAR(64) NOT NULL,
    verification_code VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    student_name VARCHAR(255) NOT NULL,
    course_title VARCHAR(500) NOT NULL,
    instructor_name VARCHAR(255) NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    file_key VARCHAR(500),
    status VARCHAR(16) NOT NULL,
    revoked_at TIMESTAMP(6),
    revocation_reason VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_certificates_number UNIQUE (certificate_number),
    CONSTRAINT uq_certificates_verification UNIQUE (verification_code),
    CONSTRAINT uq_certificates_enrollment UNIQUE (enrollment_id),
    CONSTRAINT fk_certificates_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_certificates_course FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_certificates_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id)
);

CREATE INDEX idx_certificates_user_status ON certificates(user_id, status);
CREATE INDEX idx_certificates_course ON certificates(course_id);
