package com.music.instrument.dto;

import java.util.List;

/**
 * 一键多科报名请求：同一学员一次选多门课程。
 */
public class BatchEnrollRequest {

    private Long studentId;
    private List<Long> courseIds;
    private Integer lessons;

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public List<Long> getCourseIds() {
        return courseIds;
    }

    public void setCourseIds(List<Long> courseIds) {
        this.courseIds = courseIds;
    }

    public Integer getLessons() {
        return lessons;
    }

    public void setLessons(Integer lessons) {
        this.lessons = lessons;
    }
}
