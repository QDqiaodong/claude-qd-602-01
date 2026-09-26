package com.music.instrument.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.music.instrument.domain.ServiceCompletion;
import com.music.instrument.dto.view.CompletionRoundView;

/**
 * 完工 / 验收轮次记录 Mapper：只有插入与查询，记录只追加、不提供修改。
 */
@Mapper
public interface ServiceCompletionMapper {

    int insert(ServiceCompletion completion);

    /** 一张服务单的完整轮次记录，按轮次升序、同轮按时间升序（先完工、后验收）。 */
    List<CompletionRoundView> listByOrderId(@Param("serviceOrderId") Long serviceOrderId);

    /** 多张服务单批量取，供列表/乐器展开一次装配。 */
    List<CompletionRoundView> listByOrderIds(@Param("serviceOrderIds") List<Long> serviceOrderIds);

    /**
     * 某服务单某一轮是否已存在某阶段记录。
     * 与唯一键 uk_order_round_phase 一起构成并发防线：先查给友好提示，约束兜底保证唯一。
     */
    int countByOrderRoundPhase(@Param("serviceOrderId") Long serviceOrderId,
                               @Param("roundNo") Integer roundNo,
                               @Param("phase") String phase);

    /** 取某一轮的验收结论（ACCEPTED / REJECTED），没有返回 null。 */
    CompletionRoundView findDecision(@Param("serviceOrderId") Long serviceOrderId,
                                     @Param("roundNo") Integer roundNo);
}
