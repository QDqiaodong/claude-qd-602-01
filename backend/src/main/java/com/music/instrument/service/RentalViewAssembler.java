package com.music.instrument.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.music.instrument.dto.view.RentalView;

/**
 * 租赁订单的只读装配：租赁订单列表与乐器台账展开历史共用这一套规则，
 * 保证同一张订单在两个入口的租期天数、逾期天数、逾期费用展示值与状态文案逐项一致。
 *
 * 只改视图对象、绝不写库；旧数据（RETURNED、带归还日的 OVERDUE、无归还日的 OVERDUE）
 * 都在装配层按同一口径解释，不靠批量改写数据掩盖查询问题。
 */
@Component
public class RentalViewAssembler {

    /** 按「今天」装配一批订单视图（列表 / 乐器历史 / 单条详情都走这里）。 */
    public void assemble(List<RentalView> views) {
        assemble(views, LocalDate.now());
    }

    /**
     * 装配规则（日期边界一律按自然日，跨月/跨年由 ChronoUnit.DAYS 保证不多算不少算）：
     *   租期天数 = 结束日 - 开始日 + 1（含首尾两天）；
     *   有实际归还日（已归还历史）：逾期天数 = max(0, 归还日 - 结束日)，结束日当天归还不算逾期；
     *       状态为 已归还 / 逾期归还，逾期费用保留库里的原值（仅历史数据缺失时才按公式兜底）；
     *   无实际归还日：逾期天数 = max(0, 今天 - 结束日)，结束日当天不算逾期；
     *       已过期未还 → 逾期中，费用为按今天计算的展示值（日租金 × 逾期天数 × 1.5）；
     *       未到结束日 → 保持 已预约 / 租用中，未来开始的 BOOKED 不会提前出现逾期。
     */
    public void assemble(List<RentalView> views, LocalDate today) {
        if (views == null || views.isEmpty()) {
            return;
        }
        for (RentalView view : views) {
            assembleOne(view, today);
        }
    }

    private void assembleOne(RentalView view, LocalDate today) {
        if (view == null) {
            return;
        }
        LocalDate start = view.getStartDate();
        LocalDate end = view.getEndDate();
        if (start == null || end == null) {
            // 关联数据缺失的兜底：不强行计算，只补状态文案，保证接口正常返回
            view.setStatusName(MusicDict.rentalStatusName(view.getStatus()));
            return;
        }
        view.setRentDays((int) (ChronoUnit.DAYS.between(start, end) + 1));

        LocalDate returnDate = view.getReturnDate();
        if (returnDate != null) {
            // 已归还历史（含旧库里「带归还日的 OVERDUE」）：按归还日结算，保留原费用
            long overdueDays = Math.max(0, ChronoUnit.DAYS.between(end, returnDate));
            view.setOverdueDays((int) overdueDays);
            view.setStatus(overdueDays > 0 ? "OVERDUE" : "RETURNED");
            if (view.getOverdueFee() == null) {
                view.setOverdueFee(overdueFee(view.getDailyRent(), overdueDays));
            }
        } else {
            long overdueDays = Math.max(0, ChronoUnit.DAYS.between(end, today));
            if (overdueDays > 0) {
                // 没有归还日期的记录才继续显示逾期中，费用为截至今天的展示值
                view.setOverdueDays((int) overdueDays);
                view.setStatus(MusicDict.RENTAL_STATUS_OVERDUE_UNRETURNED);
                view.setOverdueFee(overdueFee(view.getDailyRent(), overdueDays));
            } else {
                view.setOverdueDays(0);
                view.setStatus("BOOKED".equals(view.getStatus()) ? "BOOKED" : "RENTING");
                view.setOverdueFee(zeroMoney());
            }
        }
        view.setStatusName(MusicDict.rentalStatusName(view.getStatus()));
    }

    /** 逾期违约金 = 日租金 × 逾期天数 × 1.5；日租金缺失或没逾期时为 0。 */
    private BigDecimal overdueFee(BigDecimal dailyRent, long overdueDays) {
        if (dailyRent == null || overdueDays <= 0) {
            return zeroMoney();
        }
        return dailyRent.multiply(BigDecimal.valueOf(overdueDays))
                .multiply(MusicDict.OVERDUE_MULTIPLE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
