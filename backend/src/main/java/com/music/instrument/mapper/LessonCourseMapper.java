package com.music.instrument.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.music.instrument.domain.LessonCourse;

/**
 * 课程 Mapper。
 */
@Mapper
public interface LessonCourseMapper {

    List<LessonCourse> findAll();

    LessonCourse findById(@Param("id") Long id);

    /** 批量按 id 取课程（<foreach> 拼 IN (...)）。 */
    List<LessonCourse> findByIds(@Param("ids") List<Long> ids);

    int insert(LessonCourse course);

    /** delta 为 1 表示报名占位。 */
    int updateEnrolledCount(@Param("id") Long id,
                            @Param("delta") Integer delta,
                            @Param("updatedAt") LocalDateTime updatedAt);

    int countByNo(@Param("courseNo") String courseNo);
}
