package com.music.instrument.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.music.instrument.domain.Student;

/**
 * 学员 Mapper。
 */
@Mapper
public interface StudentMapper {

    List<Student> findAll();

    Student findById(@Param("id") Long id);

    /** 批量按 id 取学员（<foreach> 拼 IN (...)，课程详情里的学员名单用它）。 */
    List<Student> findByIds(@Param("ids") List<Long> ids);

    int insert(Student student);

    /** delta 为负表示扣减课时。 */
    int updateLessonBalance(@Param("id") Long id,
                            @Param("delta") Integer delta,
                            @Param("updatedAt") LocalDateTime updatedAt);

    int countByNo(@Param("studentNo") String studentNo);

    int countByPhone(@Param("phone") String phone);
}
