package com.music.instrument.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.music.instrument.domain.Enrollment;
import com.music.instrument.dto.view.EnrollmentView;

/**
 * 约课记录 Mapper。
 */
@Mapper
public interface EnrollmentMapper {

    List<EnrollmentView> list(@Param("studentId") Long studentId,
                              @Param("courseId") Long courseId);

    List<Enrollment> listByCourseId(@Param("courseId") Long courseId);

    int countByStudentAndCourse(@Param("studentId") Long studentId,
                                @Param("courseId") Long courseId);

    int insert(Enrollment enrollment);

    /** 批量插入：一键多科报名一次落库（<foreach> 拼 VALUES）。 */
    int insertBatch(@Param("list") List<Enrollment> enrollments);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
