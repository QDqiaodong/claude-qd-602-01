package com.music.instrument.dto.view;

import java.util.List;

import com.music.instrument.domain.LessonCourse;
import com.music.instrument.domain.Student;

/**
 * 课程行内展开详情：课程本身 + 已报名学员名单。
 */
public class CourseDetailView {

    private LessonCourse course;
    private List<Student> students;
    private Integer remainSeats;

    public LessonCourse getCourse() {
        return course;
    }

    public void setCourse(LessonCourse course) {
        this.course = course;
    }

    public List<Student> getStudents() {
        return students;
    }

    public void setStudents(List<Student> students) {
        this.students = students;
    }

    public Integer getRemainSeats() {
        return remainSeats;
    }

    public void setRemainSeats(Integer remainSeats) {
        this.remainSeats = remainSeats;
    }
}
