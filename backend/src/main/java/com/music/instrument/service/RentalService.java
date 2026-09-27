package com.music.instrument.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.music.instrument.common.BizException;
import com.music.instrument.domain.Instrument;
import com.music.instrument.domain.RentalOrder;
import com.music.instrument.domain.Student;
import com.music.instrument.dto.RentalCreateRequest;
import com.music.instrument.dto.ReturnRequest;
import com.music.instrument.dto.view.RentalView;
import com.music.instrument.mapper.InstrumentMapper;
import com.music.instrument.mapper.RentalOrderMapper;
import com.music.instrument.mapper.StudentMapper;

/**
 * 租赁订单：租期与押金校验、同乐器时段不重叠、维修中/报废禁租、逾期归还计费。
 */
@Service
public class RentalService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RentalOrderMapper rentalOrderMapper;
    private final InstrumentMapper instrumentMapper;
    private final StudentMapper studentMapper;
    private final RentalHistoryAssembler rentalHistoryAssembler;

    public RentalService(RentalOrderMapper rentalOrderMapper,
                         InstrumentMapper instrumentMapper,
                         StudentMapper studentMapper,
                         RentalHistoryAssembler rentalHistoryAssembler) {
        this.rentalOrderMapper = rentalOrderMapper;
        this.instrumentMapper = instrumentMapper;
        this.studentMapper = studentMapper;
        this.rentalHistoryAssembler = rentalHistoryAssembler;
    }

    public Map<String, Object> options() {
        return Map.of("statuses", MusicDict.rentalStatusOptions());
    }

    public List<RentalView> list(List<Long> instrumentIds, String status) {
        List<RentalView> views = rentalOrderMapper.list(instrumentIds, status);
        // 租期 / 逾期 / 状态文案只走统一装配，与乐器台账展开历史完全同口径
        rentalHistoryAssembler.assembleAll(views, LocalDate.now());
        return views;
    }

    @Transactional
    public RentalView create(RentalCreateRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        // 可空外键先判 null，不让用户看到非人话的报错
        if (request.getInstrumentId() == null) {
            throw new BizException("请先选择要出租的乐器");
        }
        if (request.getStudentId() == null) {
            throw new BizException("请先选择承租学员");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new BizException("请填写租期的开始日期与结束日期");
        }
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        if (endDate.isBefore(startDate)) {
            throw new BizException("租期结束日期不能早于开始日期");
        }

        Instrument instrument = instrumentMapper.findById(request.getInstrumentId());
        if (instrument == null) {
            throw new BizException("乐器不存在，无法开单");
        }
        String label = instrument.getInstrumentNo() + " " + instrument.getName();
        if ("SCRAPPED".equals(instrument.getStatus())) {
            throw new BizException("乐器【" + label + "】已报废，不能再出租");
        }
        if ("REPAIRING".equals(instrument.getStatus())) {
            throw new BizException("乐器【" + label + "】正在维修中，不能出租");
        }

        Student student = studentMapper.findById(request.getStudentId());
        if (student == null) {
            throw new BizException("学员不存在，无法开单");
        }

        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > MusicDict.MAX_RENT_DAYS) {
            throw new BizException("单次租期不得超过 " + MusicDict.MAX_RENT_DAYS + " 天，当前为 " + days + " 天");
        }

        BigDecimal deposit = request.getDeposit();
        if (deposit == null) {
            throw new BizException("请填写押金");
        }
        BigDecimal minDeposit = instrument.getDailyRent()
                .multiply(BigDecimal.valueOf(MusicDict.DEPOSIT_RENT_MULTIPLE));
        if (deposit.compareTo(minDeposit) < 0) {
            throw new BizException("押金不得低于日租金的 " + MusicDict.DEPOSIT_RENT_MULTIPLE
                    + " 倍，本单至少需要 " + minDeposit.setScale(2, RoundingMode.HALF_UP) + " 元");
        }

        int overlap = rentalOrderMapper.countOverlapForCreate(
                request.getInstrumentId(), startDate, endDate);
        if (overlap > 0) {
            throw new BizException("乐器【" + label + "】在 " + startDate + " ~ " + endDate
                    + " 期间已被租出，租期重叠不能开单");
        }

        LocalDateTime now = LocalDateTime.now();
        RentalOrder order = new RentalOrder();
        order.setOrderNo(nextOrderNo());
        order.setInstrumentId(request.getInstrumentId());
        order.setStudentId(request.getStudentId());
        order.setStartDate(startDate);
        order.setEndDate(endDate);
        order.setDeposit(deposit);
        order.setDailyRent(instrument.getDailyRent());
        order.setRentAmount(instrument.getDailyRent().multiply(BigDecimal.valueOf(days)));
        // 默认值只在 service 里落地：还没到起租日就是「已预约」，否则直接「租用中」
        order.setStatus(startDate.isAfter(LocalDate.now()) ? "BOOKED" : "RENTING");
        order.setReturnDate(null);
        order.setOverdueFee(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        rentalOrderMapper.insert(order);
        return rentalHistoryAssembler.assemble(rentalOrderMapper.findById(order.getId()), LocalDate.now());
    }

    /**
     * 办理归还：晚于租期结束日归还的，按「日租金 × 逾期天数 × 1.5」计违约金。
     * 兼容旧库：只要带实际归还日（RETURNED 或旧的 OVERDUE）都视为已办结，不能重复归还。
     */
    @Transactional
    public RentalView returnOrder(Long id, ReturnRequest request) {
        if (id == null) {
            throw new BizException("请指定要办理归还的订单");
        }
        RentalView order = rentalOrderMapper.findById(id);
        if (order == null) {
            throw new BizException("租赁订单不存在，无法办理归还");
        }
        if (order.getReturnDate() != null) {
            throw new BizException("订单【" + order.getOrderNo() + "】已于 " + order.getReturnDate()
                    + " 归还完毕，无需重复办理");
        }
        if (request == null || request.getReturnDate() == null) {
            throw new BizException("请填写实际归还日期");
        }
        LocalDate returnDate = request.getReturnDate();
        if (returnDate.isBefore(order.getStartDate())) {
            throw new BizException("归还日期不能早于租期开始日期 " + order.getStartDate());
        }

        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(order.getEndDate(), returnDate));
        BigDecimal overdueFee = order.getDailyRent()
                .multiply(BigDecimal.valueOf(overdueDays))
                .multiply(MusicDict.OVERDUE_MULTIPLE)
                .setScale(2, RoundingMode.HALF_UP);
        String status = overdueDays > 0 ? "OVERDUE" : "RETURNED";

        rentalOrderMapper.updateReturn(id, returnDate, overdueFee, status, LocalDateTime.now());
        return rentalHistoryAssembler.assemble(rentalOrderMapper.findById(id), LocalDate.now());
    }

    private String nextOrderNo() {
        String datePart = LocalDate.now().format(NO_DATE);
        int seq = rentalOrderMapper.countAll() + 1;
        String candidate;
        do {
            candidate = "R-" + datePart + "-" + String.format("%03d", seq);
            seq++;
        } while (rentalOrderMapper.countByNo(candidate) > 0);
        return candidate;
    }

    /** 供其它 service 复用：把日期时间打成中文文案 */
    public static String timeText(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_TEXT);
    }
}
