package com.music.instrument.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.music.instrument.domain.Instrument;
import com.music.instrument.dto.view.InstrumentHistoryView;
import com.music.instrument.dto.view.RentalView;
import com.music.instrument.mapper.InstrumentMapper;
import com.music.instrument.mapper.RentalOrderMapper;
import com.music.instrument.mapper.ServiceOrderMapper;

/**
 * 乐器历史批量装配：空历史、关联乐器缺失、批量分组与「只查询一次」验证。
 * 查询次数不随历史条数线性增长的端到端断言由 demo/e2e_rental_consistency.py
 * 通过 X-Query-Count 响应头在真实 MySQL 上再验一遍。
 */
@ExtendWith(MockitoExtension.class)
class InstrumentHistoryBatchTest {

    @Mock
    private InstrumentMapper instrumentMapper;
    @Mock
    private RentalOrderMapper rentalOrderMapper;
    @Mock
    private ServiceOrderMapper serviceOrderMapper;
    @Mock
    private ServiceOrderService serviceOrderService;
    @Mock
    private RentalHistoryAssembler rentalHistoryAssembler;

    @InjectMocks
    private InstrumentService instrumentService;

    private static RentalView rental(long id, long instrumentId, String start, String end,
                                     String returnDate, String dailyRent) {
        RentalView v = new RentalView();
        v.setId(id);
        v.setInstrumentId(instrumentId);
        v.setStartDate(LocalDate.parse(start));
        v.setEndDate(LocalDate.parse(end));
        if (returnDate != null) {
            v.setReturnDate(LocalDate.parse(returnDate));
        }
        v.setDailyRent(new BigDecimal(dailyRent));
        v.setOverdueFee(BigDecimal.ZERO);
        return v;
    }

    private static Instrument instrument(long id, String no) {
        Instrument i = new Instrument();
        i.setId(id);
        i.setInstrumentNo(no);
        return i;
    }

    @Test
    void emptyHistory_returnsEmptyLists_andStillBatched() {
        when(instrumentMapper.findByIds(anyList())).thenReturn(List.of(instrument(11, "U-6001")));
        when(rentalOrderMapper.listByInstrumentIds(anyList())).thenReturn(new ArrayList<>());
        when(serviceOrderMapper.listByInstrumentIds(anyList())).thenReturn(new ArrayList<>());

        List<InstrumentHistoryView> result = instrumentService.batchHistories(List.of(11L));

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getRentals().size());
        assertEquals(0, result.get(0).getServices().size());
        // 三个查询各只执行一次，无论该乐器有多少条历史
        verify(rentalOrderMapper, times(1)).listByInstrumentIds(anyList());
        verify(serviceOrderMapper, times(1)).listByInstrumentIds(anyList());
    }

    @Test
    void multipleInstruments_groupedInOneQuery() {
        when(instrumentMapper.findByIds(anyList()))
                .thenReturn(List.of(instrument(1, "G-1001"), instrument(4, "P-2002")));
        when(rentalOrderMapper.listByInstrumentIds(anyList())).thenReturn(List.of(
                rental(1, 1, "2026-09-01", "2026-09-30", null, "30"),
                rental(2, 4, "2026-08-01", "2026-08-20", "2026-08-26", "500")));
        when(serviceOrderMapper.listByInstrumentIds(anyList())).thenReturn(List.of());

        List<InstrumentHistoryView> result = instrumentService.batchHistories(List.of(1L, 4L));

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getRentals().size());
        assertEquals(1, result.get(1).getRentals().size());
        // 两台乐器、多条历史：租赁仍只批量查一次，绝不按乐器/订单追加查询
        verify(rentalOrderMapper, times(1)).listByInstrumentIds(anyList());
        verify(rentalHistoryAssembler, times(1)).assembleAll(anyList(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rentalWhoseInstrumentIsMissing_isDroppedFromResultButDoesNotBreakOthers() {
        // 关联乐器被删除/缺失：只返回实际存在的乐器，其余历史记录不影响正常乐器装配
        when(instrumentMapper.findByIds(anyList())).thenReturn(List.of(instrument(1, "G-1001")));
        when(rentalOrderMapper.listByInstrumentIds(anyList())).thenReturn(List.of(
                rental(1, 1, "2026-09-01", "2026-09-30", null, "30")));
        when(serviceOrderMapper.listByInstrumentIds(anyList())).thenReturn(List.of());

        List<InstrumentHistoryView> result = instrumentService.batchHistories(List.of(1L, 999L));

        assertEquals(1, result.size());
        assertEquals("G-1001", result.get(0).getInstrument().getInstrumentNo());
        assertEquals(1, result.get(0).getRentals().size());
    }

    @Test
    void allInstrumentsMissing_throwsBizError() {
        when(instrumentMapper.findByIds(anyList())).thenReturn(List.of());
        assertThrows(RuntimeException.class,
                () -> instrumentService.batchHistories(List.of(404L)));
        verify(rentalOrderMapper, never()).listByInstrumentIds(anyList());
    }

    @Test
    void duplicateIds_deduplicatedBeforeQuery() {
        when(instrumentMapper.findByIds(anyList()))
                .thenReturn(List.of(instrument(1, "G-1001")));
        when(rentalOrderMapper.listByInstrumentIds(anyList())).thenReturn(List.of());
        when(serviceOrderMapper.listByInstrumentIds(anyList())).thenReturn(List.of());

        List<InstrumentHistoryView> result =
                instrumentService.batchHistories(List.of(1L, 1L));

        assertEquals(1, result.size());
        verify(instrumentMapper, times(1)).findByIds(List.of(1L));
    }
}
