package com.peergeek.courselearning.course;

import jakarta.persistence.*;

@Entity
@Table(name = "course_topics")
public class CourseTopicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "position", nullable = false)
    private int position = 0;

    @Column(name = "name", nullable = false)
    private String name;

    protected CourseTopicEntity() {}

    public CourseTopicEntity(Long courseId, int position, String name) {
        this.courseId = courseId;
        this.position = position;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

