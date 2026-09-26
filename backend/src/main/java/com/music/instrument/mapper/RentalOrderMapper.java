package com.music.instrument.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.music.instrument.domain.RentalOrder;
import com.music.instrument.dto.view.RentalView;

/**
 * 租赁订单 Mapper。
 */
@Mapper
public interface RentalOrderMapper {

    List<RentalView> list(@Param("instrumentIds") List<Long> instrumentIds,
                          @Param("status") String status);

    RentalView findById(@Param("id") Long id);

    /** 行内展开详情用：一次取多台乐器的租赁记录（<foreach> 拼 IN (...)）。 */
    List<RentalView> listByInstrumentIds(@Param("instrumentIds") List<Long> instrumentIds);

    int countOverlapForCreate(@Param("instrumentId") Long instrumentId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

    int countOverlapForUpdate(@Param("instrumentId") Long instrumentId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate,
                              @Param("excludeId") Long excludeId);

    int insert(RentalOrder order);

    int updateReturn(@Param("id") Long id,
                     @Param("returnDate") LocalDate returnDate,
                     @Param("overdueFee") BigDecimal overdueFee,
                     @Param("status") String status,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int countAll();

    int countByNo(@Param("orderNo") String orderNo);
}
