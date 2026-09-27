package com.music.instrument.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.music.instrument.dto.view.RentalView;

/**
 * 租赁统一装配规则的纯逻辑测试：租期天数 / 逾期天数（自然日、结束日当天不逾期、
 * 跨月跨年）/ 违约金展示值 / 状态文案，以及旧库三种历史状态的兼容。
 */
class RentalHistoryAssemblerTest {

    /** 演示基准日：与种子数据口径一致（当前日期 = 2026-09-26）。 */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    private final RentalHistoryAssembler assembler = new RentalHistoryAssembler();

    private RentalView order(String rawStatus, String start, String end,
                             String returnDate, String storedFee, String dailyRent) {
        RentalView view = new RentalView();
        view.setId(1L);
        view.setOrderNo("R-T");
        view.setStatus(rawStatus);
        view.setStartDate(LocalDate.parse(start));
        view.setEndDate(LocalDate.parse(end));
        if (returnDate != null) {
            view.setReturnDate(LocalDate.parse(returnDate));
        }
        view.setOverdueFee(storedFee == null ? null : new BigDecimal(storedFee));
        view.setDailyRent(new BigDecimal(dailyRent));
        return view;
    }

    @Test
    void returned_onTime_legacyReturned() {
        // 旧 RETURNED，实际归还日=结束日：结束日当天不算逾期，保留 0 费用
        RentalView v = assembler.assemble(
                order("RETURNED", "2026-05-01", "2026-05-10", "2026-05-10", "0.00", "30"), TODAY);
        assertEquals(10, v.getRentDays());
        assertEquals(0, v.getOverdueDays());
        assertEquals("已归还", v.getStatusName());
        assertEquals("0.00", v.getOverdueFee().toPlainString());
    }

    @Test
    void returned_late_legacyOverdueWithReturnDate_isReturnedHistoryWithStoredFee() {
        // 旧库 OVERDUE + 带实际归还日：按已归还历史解释，逾期按归还日结算，费用保留原值（不重算）
        RentalView v = assembler.assemble(
                order("OVERDUE", "2026-06-01", "2026-06-30", "2026-07-06", "360.00", "40"), TODAY);
        assertEquals(30, v.getRentDays());
        assertEquals(6, v.getOverdueDays());
        assertEquals("逾期归还", v.getStatusName());
        // 即便原费用与公式结果不同，也保留入库时的结算值
        assertEquals("360.00", v.getOverdueFee().toPlainString());
    }

    @Test
    void overdue_withoutReturnDate_showsActiveOverdueWithTrialFee() {
        // 旧库 OVERDUE/RENTING 但没有实际归还日：继续显示逾期中，费用只读试算（日租×天数×1.5）
        RentalView v = assembler.assemble(
                order("RENTING", "2026-09-01", "2026-09-20", null, null, "160"), TODAY);
        assertEquals(20, v.getRentDays());
        assertEquals(6, v.getOverdueDays());
        assertEquals("逾期中", v.getStatusName());
        assertEquals("1440.00", v.getOverdueFee().toPlainString());
    }

    @Test
    void booked_futureStart_neverOverdue() {
        // 未来开始的 BOOKED：即使「今天」晚于结束日也不该发生；正常情况下逾期必须为 0
        RentalView v = assembler.assemble(
                order("BOOKED", "2026-10-01", "2026-10-09", null, null, "45"), TODAY);
        assertEquals(9, v.getRentDays());
        assertEquals(0, v.getOverdueDays());
        assertEquals("已预约", v.getStatusName());
        assertEquals("0.00", v.getOverdueFee().toPlainString());
    }

    @Test
    void renting_endDateIsToday_notOverdue() {
        // 边界：结束日当天归还/查看都不算逾期
        RentalView v = assembler.assemble(
                order("RENTING", "2026-09-20", "2026-09-26", null, null, "60"), TODAY);
        assertEquals(7, v.getRentDays());
        assertEquals(0, v.getOverdueDays());
        assertEquals("租用中", v.getStatusName());
    }

    @Test
    void renting_dayAfterEndDate_isOneOverdueDay() {
        // 边界：结束日次日 = 1 天逾期
        RentalView v = assembler.assemble(
                order("RENTING", "2026-09-20", "2026-09-25", null, null, "60"), TODAY);
        assertEquals(1, v.getOverdueDays());
        assertEquals("逾期中", v.getStatusName());
    }

    @Test
    void crossYear_rentPeriod_andOverdue() {
        // 跨年租期：2025-12-20 ~ 2026-01-19 = 31 天；2026-01-25 归还 = 逾期 6 天
        RentalView v = assembler.assemble(
                order("OVERDUE", "2025-12-20", "2026-01-19", "2026-01-25", "1800.00", "200"), TODAY);
        assertEquals(31, v.getRentDays());
        assertEquals(6, v.getOverdueDays());
        assertEquals("逾期归还", v.getStatusName());
    }

    @Test
    void returned_lateWithNullStoredFee_showsZero_notTrial() {
        // 已归还历史单 overdue_fee 为 NULL：按 0.00 展示，不按当前日期试算
        RentalView v = assembler.assemble(
                order("OVERDUE", "2026-03-01", "2026-03-10", "2026-03-12", null, "100"), TODAY);
        assertEquals(2, v.getOverdueDays());
        assertEquals("0.00", v.getOverdueFee().toPlainString());
    }

    @Test
    void missingRelationFields_stillAssemblesSafely() {
        // 关联学员/乐器缺失（LEFT JOIN 为空）甚至日期脏数据：不抛异常，正常返回
        RentalView v = new RentalView();
        v.setStatus("RENTING");
        v.setDailyRent(null);
        assembler.assemble(v, TODAY);
        assertEquals(0, v.getRentDays());
        assertEquals(0, v.getOverdueDays());
        assertNotNull(v.getOverdueFee());
        assertEquals("租用中", v.getStatusName());
    }

    @Test
    void assembleAll_usesSameSnapshotDay() {
        List<RentalView> views = List.of(
                order("RENTING", "2026-09-20", "2026-09-25", null, null, "60"),
                order("BOOKED", "2026-10-01", "2026-10-09", null, null, "45"));
        assembler.assembleAll(views, TODAY);
        assertEquals(1, views.get(0).getOverdueDays());
        assertEquals(0, views.get(1).getOverdueDays());
    }
}
