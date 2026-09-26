package com.music.instrument.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.music.instrument.domain.Technician;

/**
 * 调律师 Mapper。
 */
@Mapper
public interface TechnicianMapper {

    List<Technician> findAll();

    Technician findById(@Param("id") Long id);

    /** 批量按 id 取调律师（<foreach> 拼 IN (...)）。 */
    List<Technician> findByIds(@Param("ids") List<Long> ids);

    int countByNo(@Param("techNo") String techNo);
}
