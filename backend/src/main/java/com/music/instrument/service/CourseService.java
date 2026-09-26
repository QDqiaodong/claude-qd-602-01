package com.music.instrument.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.music.instrument.common.BizException;
import com.music.instrument.domain.Enrollment;
import com.music.instrument.domain.LessonCourse;
import com.music.instrument.domain.Student;
import com.music.instrument.dto.BatchEnrollRequest;
import com.music.instrument.dto.EnrollmentCreateRequest;
import com.music.instrument.dto.view.CourseDetailView;
import com.music.instrument.dto.view.EnrollmentView;
import com.music.instrument.mapper.EnrollmentMapper;
import com.music.instrument.mapper.LessonCourseMapper;
import com.music.instrument.mapper.StudentMapper;

/**
 * 学员与课程：课时余额门槛、满员拦截、未购课禁约、同课不重复约。
 */
@Service
public class CourseService {

    private final StudentMapper studentMapper;
    private final LessonCourseMapper lessonCourseMapper;
    private final EnrollmentMapper enrollmentMapper;

    public CourseService(StudentMapper studentMapper,
                         LessonCourseMapper lessonCourseMapper,
                         EnrollmentMapper enrollmentMapper) {
        this.studentMapper = studentMapper;
        this.lessonCourseMapper = lessonCourseMapper;
        this.enrollmentMapper = enrollmentMapper;
    }

    public Map<String, Object> options() {
        return Map.of(
                "courseStatuses", MusicDict.courseStatusOptions(),
                "studentLevels", MusicDict.studentLevelOptions());
    }

    public List<Student> listStudents() {
        return studentMapper.findAll();
    }

    public List<LessonCourse> listCourses() {
        return lessonCourseMapper.findAll();
    }

    public List<EnrollmentView> listEnrollments(Long studentId, Long courseId) {
        List<EnrollmentView> views = enrollmentMapper.list(studentId, courseId);
        for (EnrollmentView view : views) {
            decorate(view);
        }
        return views;
    }

    /**
     * 课程行内展开详情：课程本身 + 已报名学员名单（用 <foreach> 批量取学员）。
     */
    public CourseDetailView courseDetail(Long courseId) {
        if (courseId == null) {
            throw new BizException("请指定要展开的课程");
        }
        LessonCourse course = lessonCourseMapper.findById(courseId);
        if (course == null) {
            throw new BizException("课程不存在，无法展开");
        }
        List<Enrollment> enrollments = enrollmentMapper.listByCourseId(courseId);
        List<Long> studentIds = enrollments.stream().map(Enrollment::getStudentId).toList();

        CourseDetailView view = new CourseDetailView();
        view.setCourse(course);
        view.setStudents(studentIds.isEmpty() ? List.of() : studentMapper.findByIds(studentIds));
        view.setRemainSeats(Math.max(0, course.getCapacity() - course.getEnrolledCount()));
        return view;
    }

    @Transactional
    public EnrollmentView enroll(EnrollmentCreateRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.getStudentId() == null) {
            throw new BizException("请先选择学员");
        }
        if (request.getCourseId() == null) {
            throw new BizException("请先选择课程");
        }
        Integer lessons = request.getLessons();
        if (lessons == null || lessons <= 0) {
            throw new BizException("消耗课时必须大于 0");
        }

        Student student = studentMapper.findById(request.getStudentId());
        if (student == null) {
            throw new BizException("学员不存在，无法约课");
        }
        LessonCourse course = lessonCourseMapper.findById(request.getCourseId());
        if (course == null) {
            throw new BizException("课程不存在，无法约课");
        }

        checkCourseOpen(course);
        checkCourseNotFull(course);
        checkBalance(student, lessons);
        checkNotDuplicated(student, course);

        LocalDate enrollDate = request.getEnrollDate() == null ? LocalDate.now() : request.getEnrollDate();
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(student.getId());
        enrollment.setCourseId(course.getId());
        enrollment.setEnrollDate(enrollDate);
        enrollment.setLessons(lessons);
        // 默认值只在 service 里落地
        enrollment.setStatus("NORMAL");
        enrollment.setCreatedAt(LocalDateTime.now());
        enrollmentMapper.insert(enrollment);

        studentMapper.updateLessonBalance(student.getId(), -lessons, LocalDateTime.now());
        lessonCourseMapper.updateEnrolledCount(course.getId(), 1, LocalDateTime.now());

        return enrollmentMapper.list(enrollment.getStudentId(), null).stream()
                .filter(view -> view.getId().equals(enrollment.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 一键多科报名：一次校验、一次 <foreach> 批量插入、一次批量扣课时。
     */
    @Transactional
    public List<EnrollmentView> batchEnroll(BatchEnrollRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.getStudentId() == null) {
            throw new BizException("请先选择学员");
        }
        if (request.getCourseIds() == null || request.getCourseIds().isEmpty()) {
            throw new BizException("请至少选择一门课程");
        }
        Set<Long> distinctIds = new LinkedHashSet<>(request.getCourseIds());
        if (distinctIds.contains(null)) {
            throw new BizException("课程 id 不能为空");
        }
        if (distinctIds.size() > MusicDict.MAX_BATCH_COURSES) {
            throw new BizException("一次最多选择 " + MusicDict.MAX_BATCH_COURSES + " 门课程，当前选了 "
                    + distinctIds.size() + " 门");
        }
        int lessons = request.getLessons() == null ? MusicDict.DEFAULT_BATCH_LESSONS : request.getLessons();
        if (lessons <= 0) {
            throw new BizException("消耗课时必须大于 0");
        }

        Student student = studentMapper.findById(request.getStudentId());
        if (student == null) {
            throw new BizException("学员不存在，无法约课");
        }

        List<Long> courseIds = new ArrayList<>(distinctIds);
        List<LessonCourse> courses = lessonCourseMapper.findByIds(courseIds);
        if (courses.size() != courseIds.size()) {
            throw new BizException("所选课程中有 " + (courseIds.size() - courses.size()) + " 门不存在，请刷新后重试");
        }
        for (LessonCourse course : courses) {
            checkCourseOpen(course);
            checkCourseNotFull(course);
            checkNotDuplicated(student, course);
        }

        int totalLessons = lessons * courses.size();
        checkBalance(student, totalLessons);

        LocalDate enrollDate = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<Enrollment> batch = new ArrayList<>();
        for (LessonCourse course : courses) {
            Enrollment enrollment = new Enrollment();
            enrollment.setStudentId(student.getId());
            enrollment.setCourseId(course.getId());
            enrollment.setEnrollDate(enrollDate);
            enrollment.setLessons(lessons);
            enrollment.setStatus("NORMAL");
            enrollment.setCreatedAt(now);
            batch.add(enrollment);
        }
        enrollmentMapper.insertBatch(batch);
        studentMapper.updateLessonBalance(student.getId(), -totalLessons, now);
        for (LessonCourse course : courses) {
            lessonCourseMapper.updateEnrolledCount(course.getId(), 1, now);
        }

        return listEnrollments(student.getId(), null);
    }

    private void checkCourseOpen(LessonCourse course) {
        if (!"OPEN".equals(course.getStatus())) {
            throw new BizException("课程【" + course.getCourseNo() + " " + course.getTitle() + "】"
                    + MusicDict.courseStatusName(course.getStatus()) + "，不能约课");
        }
    }

    private void checkCourseNotFull(LessonCourse course) {
        if (course.getEnrolledCount() >= course.getCapacity()) {
            throw new BizException("课程【" + course.getCourseNo() + " " + course.getTitle() + "】已满员（"
                    + course.getEnrolledCount() + "/" + course.getCapacity() + "），不能约课");
        }
    }

    private void checkNotDuplicated(Student student, LessonCourse course) {
        int exists = enrollmentMapper.countByStudentAndCourse(student.getId(), course.getId());
        if (exists > 0) {
            throw new BizException("学员【" + student.getName() + "】已经约过课程【" + course.getTitle()
                    + "】，不能重复约课");
        }
    }

    private void checkBalance(Student student, int needLessons) {
        int balance = student.getLessonBalance() == null ? 0 : student.getLessonBalance();
        if (balance <= 0) {
            throw new BizException("学员【" + student.getName() + "】尚未购课（课时余额为 0），不能约课");
        }
        if (balance < needLessons) {
            throw new BizException("学员【" + student.getName() + "】课时余额不足（剩余 " + balance
                    + " 节，本次需要 " + needLessons + " 节）");
        }
    }

    private void decorate(EnrollmentView view) {
        view.setStatusName(MusicDict.enrollmentStatusName(view.getStatus()));
        view.setStudentLevel(MusicDict.studentLevelName(view.getStudentLevel()));
        view.setCourseCategory(MusicDict.categoryName(view.getCourseCategory()));
    }
}
