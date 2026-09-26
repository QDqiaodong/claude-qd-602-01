package com.music.instrument.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.music.instrument.domain.Instrument;

/**
 * 乐器台账 Mapper（SQL 全部写在 resources/mapper/InstrumentMapper.xml）。
 */
@Mapper
public interface InstrumentMapper {

    List<Instrument> findAll();

    List<Instrument> findByCategoryAndStatus(@Param("category") String category,
                                             @Param("status") String status);

    Instrument findById(@Param("id") Long id);

    /** 事务内对乐器行加锁，服务单状态联动乐器时使用。 */
    Instrument lockById(@Param("id") Long id);

    /** 批量按 id 取乐器（<foreach> 拼 IN (...)）。 */
    List<Instrument> findByIds(@Param("ids") List<Long> ids);

    int insert(Instrument instrument);

    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /** 条件更新：仅当当前状态匹配时生效，返回影响行数（0 即并发冲突）。 */
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("currentStatus") String currentStatus,
                              @Param("newStatus") String newStatus,
                              @Param("updatedAt") LocalDateTime updatedAt);

    int countByNo(@Param("instrumentNo") String instrumentNo);
}
