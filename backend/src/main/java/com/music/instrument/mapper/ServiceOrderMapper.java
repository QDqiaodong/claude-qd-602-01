package com.music.instrument.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.music.instrument.domain.ServiceOrder;
import com.music.instrument.dto.view.ServiceView;

/**
 * 调律维修服务单 Mapper。
 */
@Mapper
public interface ServiceOrderMapper {

    List<ServiceView> list(@Param("instrumentIds") List<Long> instrumentIds,
                           @Param("technicianIds") List<Long> technicianIds,
                           @Param("status") String status);

    ServiceView findById(@Param("id") Long id);

    /** 事务内加行锁读取，配合条件更新做并发唯一结果。 */
    ServiceView lockById(@Param("id") Long id);

    /** 行内展开详情用：一次取多台乐器的服务单（<foreach> 拼 IN (...)，只看未取消的）。 */
    List<ServiceView> listByInstrumentIds(@Param("instrumentIds") List<Long> instrumentIds);

    int countTechOverlapForCreate(@Param("technicianId") Long technicianId,
                                  @Param("planStart") LocalDateTime planStart,
                                  @Param("planEnd") LocalDateTime planEnd);

    int countTechOverlapForUpdate(@Param("technicianId") Long technicianId,
                                  @Param("planStart") LocalDateTime planStart,
                                  @Param("planEnd") LocalDateTime planEnd,
                                  @Param("excludeId") Long excludeId);

    int insert(ServiceOrder order);

    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("resultNote") String resultNote,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /** 开始施工：PLANNED → DOING 并落地乐器原状态快照；非 PLANNED 返回 0。 */
    int markDoing(@Param("id") Long id,
                  @Param("instrumentSnapshot") String instrumentSnapshot,
                  @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 条件更新：仅当服务单当前状态 = expectedStatus 时才落库。
     * 返回 0 即并发冲突（已被别的操作推进），由调用方转成带当前轮次/最新状态的提示。
     */
    int updateStatusIfExpected(@Param("id") Long id,
                               @Param("expectedStatus") String expectedStatus,
                               @Param("newStatus") String newStatus,
                               @Param("currentRound") Integer currentRound,
                               @Param("updatedAt") LocalDateTime updatedAt);

    int countAll();

    int countByNo(@Param("serviceNo") String serviceNo);

    /**
     * 该乐器是否还有其它在途维修/大修单（进行中 / 待验收 / 返修中）。
     * 验收通过恢复乐器时使用：只要还有一张维修单在途，乐器就继续保持维修中。
     */
    int countOpenRepairsByInstrument(@Param("instrumentId") Long instrumentId,
                                     @Param("excludeId") Long excludeId);
}
