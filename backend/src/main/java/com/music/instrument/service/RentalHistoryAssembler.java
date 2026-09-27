package com.music.instrument.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.music.instrument.dto.view.RentalView;

/**
 * 租赁视图的唯一只读装配口径：租赁列表与乐器台账展开的租赁历史都必须经过这里，
 * 任何一个入口都不许再各写一套租期 / 逾期 / 状态计算。
 *
 * 口径（全部按自然日，结束日当天不算逾期；跨月、跨年只看两个日期差，不多算不少算）：
 *   1. 租期天数 = endDate - startDate + 1（首尾两天都计入）。
 *   2. 已归还（return_date 非空，含旧库 RETURNED 与带实际归还日的 OVERDUE）：
 *      一律按「已归还历史」解释——逾期天数按实际归还日结算，违约金展示库里保留的原费用，
 *      不重新计算、不改写旧数据；逾期天数为 0 显示已归还，大于 0 显示逾期归还。
 *   3. 未归还（return_date 为空）：以当前日期为参照。
 *      起租日在未来的 BOOKED 不能提前逾期；已过结束日才算逾期中，
 *      逾期天数 = today - endDate，违约金为只读试算值（日租 × 逾期天数 × 倍率，不落库）。
 */
@Component
public class RentalHistoryAssembler {

    /** 列表 / 历史批量装配：同一批共用一个「今天」，保证同一响应内口径一致。 */
    public void assembleAll(List<RentalView> views, LocalDate today) {
        if (views == null) {
            return;
        }
        LocalDate referenceDay = today != null ? today : LocalDate.now();
        for (RentalView view : views) {
            assemble(view, referenceDay);
        }
    }

    /** 单条装配（开单、归还后的回执同样走这里，保证三个接口字段一致）。 */
    public RentalView assemble(RentalView view, LocalDate today) {
        if (view == null) {
            return null;
        }
        LocalDate referenceDay = today != null ? today : LocalDate.now();

        view.setRentDays(rentDays(view));
        long overdueDays = overdueDays(view, referenceDay);
        view.setOverdueDays((int) overdueDays);

        String displayStatus = displayStatus(view, referenceDay, overdueDays);
        view.setStatusName(MusicDict.rentalStatusName(displayStatus));
        view.setOverdueFee(overdueFeeView(view, overdueDays));
        return view;
    }

    /**
     * 展示状态：只在只读视图上推导，绝不回写数据库。
     * 有实际归还日 → RETURNED（按期）/ OVERDUE（逾期归还，保留旧状态码 OVERDUE 的含义）；
     * 无实际归还日 → BOOKED（未来起租）/ OVERDUE_ACTIVE（已逾期未还）/ RENTING（租用中）。
     */
    private String displayStatus(RentalView view, LocalDate today, long overdueDays) {
        if (view.getReturnDate() != null) {
            return overdueDays > 0 ? "OVERDUE" : "RETURNED";
        }
        if (view.getStartDate() != null && view.getStartDate().isAfter(today)) {
            return "BOOKED";
        }
        if (overdueDays > 0) {
            return "OVERDUE_ACTIVE";
        }
        return "RENTING";
    }

    /** 租期天数：首尾两天都算，自然日差值 + 1；日期缺失的脏数据兜底为 0。 */
    private Integer rentDays(RentalView view) {
        if (view.getStartDate() == null || view.getEndDate() == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(view.getStartDate(), view.getEndDate()) + 1;
        return (int) Math.max(0, days);
    }

    /**
     * 逾期天数：结束日当天不算逾期（between 到结束日为 0），次日起算。
     * 已归还按实际归还日结算；未归还按当前日期；未来起租 / 尚未到结束日都为 0。
     */
    private long overdueDays(RentalView view, LocalDate today) {
        if (view.getEndDate() == null) {
            return 0;
        }
        LocalDate reference = view.getReturnDate() != null ? view.getReturnDate() : today;
        return Math.max(0, ChronoUnit.DAYS.between(view.getEndDate(), reference));
    }

    /**
     * 违约金展示值：已归还历史单保留入库时的原费用（旧库可能为 NULL，按 0.00 展示）；
     * 未归还且逾期中的，按当前逾期天数只读试算（与归还结算公式一致），不落库。
     */
    private BigDecimal overdueFeeView(RentalView view, long overdueDays) {
        if (view.getReturnDate() != null) {
            BigDecimal stored = view.getOverdueFee();
            return stored != null ? stored : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (overdueDays <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal dailyRent = view.getDailyRent() != null ? view.getDailyRent() : BigDecimal.ZERO;
        return dailyRent.multiply(BigDecimal.valueOf(overdueDays))
                .multiply(MusicDict.OVERDUE_MULTIPLE)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
