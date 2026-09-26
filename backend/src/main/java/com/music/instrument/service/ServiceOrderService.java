package com.music.instrument.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.music.instrument.common.BizException;
import com.music.instrument.domain.Instrument;
import com.music.instrument.domain.ServiceCompletion;
import com.music.instrument.domain.ServiceOrder;
import com.music.instrument.domain.Technician;
import com.music.instrument.dto.AcceptanceRequest;
import com.music.instrument.dto.CompletionSubmitRequest;
import com.music.instrument.dto.ServiceCreateRequest;
import com.music.instrument.dto.StatusChangeRequest;
import com.music.instrument.dto.view.CompletionRoundView;
import com.music.instrument.dto.view.ServiceView;
import com.music.instrument.mapper.InstrumentMapper;
import com.music.instrument.mapper.ServiceCompletionMapper;
import com.music.instrument.mapper.ServiceOrderMapper;
import com.music.instrument.mapper.TechnicianMapper;

/**
 * 调律与维修：派单校验（报废禁派、休假禁派、同调律师时段不重叠）与
 * 「完工提交 → 待验收 →（验收通过 / 退回返修 → 再次提交）」闭环。
 *
 * 并发口径：提交与验收都在事务内先对服务单行加 FOR UPDATE 行锁，再用
 * 条件 UPDATE（WHERE status = 期望状态）落库；轮次记录唯一键兜底。
 * 因此连点、重试、两人同时作出相反结论都只会有一个有效结果。
 */
@Service
public class ServiceOrderService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter SLOT_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static final String PHASE_COMPLETION = "COMPLETION";
    static final String PHASE_ACCEPTED = "ACCEPTED";
    static final String PHASE_REJECTED = "REJECTED";

    private final ServiceOrderMapper serviceOrderMapper;
    private final ServiceCompletionMapper completionMapper;
    private final InstrumentMapper instrumentMapper;
    private final TechnicianMapper technicianMapper;

    public ServiceOrderService(ServiceOrderMapper serviceOrderMapper,
                               ServiceCompletionMapper completionMapper,
                               InstrumentMapper instrumentMapper,
                               TechnicianMapper technicianMapper) {
        this.serviceOrderMapper = serviceOrderMapper;
        this.completionMapper = completionMapper;
        this.instrumentMapper = instrumentMapper;
        this.technicianMapper = technicianMapper;
    }

    public Map<String, Object> options() {
        return Map.of(
                "serviceTypes", MusicDict.serviceTypeOptionsData(),
                "statuses", MusicDict.serviceStatusOptions());
    }

    public List<Technician> listTechnicians() {
        return technicianMapper.findAll();
    }

    public List<ServiceView> list(List<Long> instrumentIds, List<Long> technicianIds, String status) {
        List<ServiceView> views = serviceOrderMapper.list(instrumentIds, technicianIds, status);
        attachRounds(views);
        for (ServiceView view : views) {
            decorate(view);
        }
        return views;
    }

    /** 乐器展开历史复用：为服务单视图批量补齐轮次记录。 */
    public void attachRounds(List<ServiceView> views) {
        if (views == null || views.isEmpty()) {
            return;
        }
        List<Long> ids = views.stream().map(ServiceView::getId).toList();
        List<CompletionRoundView> all = completionMapper.listByOrderIds(ids);
        Map<Long, List<CompletionRoundView>> grouped = new LinkedHashMap<>();
        for (CompletionRoundView round : all) {
            round.setPhaseName(MusicDict.completionPhaseName(round.getPhase()));
            grouped.computeIfAbsent(round.getServiceOrderId(), k -> new ArrayList<>()).add(round);
        }
        for (ServiceView view : views) {
            List<CompletionRoundView> rounds =
                    grouped.getOrDefault(view.getId(), List.of());
            view.setRounds(rounds);
            view.setLatestDecisionName(latestDecisionName(rounds));
        }
    }

    @Transactional
    public ServiceView create(ServiceCreateRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.getInstrumentId() == null) {
            throw new BizException("请先选择要服务的乐器");
        }
        if (request.getTechnicianId() == null) {
            throw new BizException("请先选择调律师");
        }
        if (request.getPlanStart() == null || request.getPlanEnd() == null) {
            throw new BizException("请填写计划的开始时间与结束时间");
        }
        if (!request.getPlanEnd().isAfter(request.getPlanStart())) {
            throw new BizException("计划结束时间必须晚于开始时间");
        }
        if (!MusicDict.isValidServiceType(request.getServiceType())) {
            throw new BizException("服务类型不合法，只能是 " + MusicDict.serviceTypeOptions());
        }
        if (request.getFee() == null || request.getFee().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("请填写服务费用，且不能为负数");
        }

        Instrument instrument = instrumentMapper.findById(request.getInstrumentId());
        if (instrument == null) {
            throw new BizException("乐器不存在，无法派单");
        }
        if ("SCRAPPED".equals(instrument.getStatus())) {
            throw new BizException("乐器【" + instrument.getInstrumentNo() + " " + instrument.getName()
                    + "】已报废，不能派单");
        }

        Technician technician = technicianMapper.findById(request.getTechnicianId());
        if (technician == null) {
            throw new BizException("调律师不存在，无法派单");
        }
        if (!"ACTIVE".equals(technician.getStatus())) {
            throw new BizException("调律师【" + technician.getName() + "】当前为"
                    + MusicDict.techStatusName(technician.getStatus()) + "状态，不能派单");
        }

        int overlap = serviceOrderMapper.countTechOverlapForCreate(
                request.getTechnicianId(), request.getPlanStart(), request.getPlanEnd());
        if (overlap > 0) {
            throw new BizException("调律师【" + technician.getName() + "】在 "
                    + request.getPlanStart().format(SLOT_TEXT) + " ~ " + request.getPlanEnd().format(SLOT_TEXT)
                    + " 已有排单，时段重叠不能派单");
        }

        LocalDateTime now = LocalDateTime.now();
        ServiceOrder order = new ServiceOrder();
        order.setServiceNo(nextServiceNo());
        order.setInstrumentId(request.getInstrumentId());
        order.setTechnicianId(request.getTechnicianId());
        order.setServiceType(request.getServiceType());
        order.setPlanStart(request.getPlanStart());
        order.setPlanEnd(request.getPlanEnd());
        order.setFee(request.getFee());
        // 默认值只在 service 里落地
        order.setStatus("PLANNED");
        order.setCurrentRound(0);
        order.setResultNote(request.getResultNote());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        serviceOrderMapper.insert(order);
        ServiceView view = serviceOrderMapper.findById(order.getId());
        attachRounds(List.of(view));
        return decorate(view);
    }

    /**
     * 通用状态推进只保留：开始施工（PLANNED→DOING，维修类联动乐器到维修中）、取消派单。
     * 完工提交、验收通过、退回返修必须走专属接口，不能靠改状态文字跳步。
     */
    @Transactional
    public ServiceView changeStatus(Long id, StatusChangeRequest request) {
        if (id == null) {
            throw new BizException("请指定要推进的服务单");
        }
        if (request == null || request.getStatus() == null || request.getStatus().isEmpty()) {
            throw new BizException("请指定目标状态");
        }
        String target = request.getStatus();

        ServiceView order = serviceOrderMapper.lockById(id);
        if (order == null) {
            throw new BizException("服务单不存在，无法推进状态");
        }
        String reject = MusicDict.serviceTransferReject(order.getStatus(), target);
        if (reject != null) {
            throw new BizException("服务单【" + order.getServiceNo() + "】" + reject);
        }
        LocalDateTime now = LocalDateTime.now();

        if ("DOING".equals(target)) {
            startDoing(order, now);
        } else {
            // 取消派单：维修未交付，不恢复乐器状态，避免把正在维修/租出的乐器误改
            serviceOrderMapper.updateStatus(id, target, order.getResultNote(), now);
        }
        ServiceView view = serviceOrderMapper.findById(id);
        attachRounds(List.of(view));
        return decorate(view);
    }

    /**
     * 调律师提交完工：DOING / REWORKING 才能提交，写入新一轮完工记录并进入待验收。
     * 维修/大修类乐器继续保持维修中（不动），调律类始终不动乐器状态。
     */
    @Transactional
    public ServiceView submitCompletion(Long id, CompletionSubmitRequest request) {
        if (id == null) {
            throw new BizException("请指定要提交完工的服务单");
        }
        String processNote = required(request == null ? null : request.getProcessNote(), "本轮处理内容");
        String checkResult = required(request == null ? null : request.getCheckResult(), "检测结果");
        String deliveryNote = required(request == null ? null : request.getDeliveryNote(), "交付说明");

        ServiceView order = serviceOrderMapper.lockById(id);
        if (order == null) {
            throw new BizException("服务单不存在，无法提交完工");
        }
        String status = order.getStatus();
        if (!"DOING".equals(status) && !"REWORKING".equals(status)) {
            throw new BizException("服务单【" + order.getServiceNo() + "】" + busyHint(order)
                    + "，当前不能提交完工，请刷新后按最新状态操作");
        }

        int nextRound = nz(order.getCurrentRound()) + 1;
        // 行锁已把同单并发串行化，这里二次确认；唯一键 uk_order_round_phase 再兜底
        if (completionMapper.countByOrderRoundPhase(id, nextRound, PHASE_COMPLETION) > 0) {
            throw new BizException("服务单【" + order.getServiceNo() + "】第 " + nextRound
                    + " 轮完工记录已存在，请勿重复提交");
        }

        Technician technician = technicianMapper.findById(order.getTechnicianId());
        String operator = technician == null ? "调律师" : technician.getName();

        ServiceCompletion completion = new ServiceCompletion();
        completion.setServiceOrderId(id);
        completion.setRoundNo(nextRound);
        completion.setPhase(PHASE_COMPLETION);
        completion.setProcessNote(processNote);
        completion.setCheckResult(checkResult);
        completion.setDeliveryNote(deliveryNote);
        completion.setOperatorName(operator);
        completion.setCreatedAt(LocalDateTime.now());
        try {
            completionMapper.insert(completion);
        } catch (DuplicateKeyException e) {
            throw new BizException("服务单【" + order.getServiceNo() + "】第 " + nextRound
                    + " 轮完工已被提交，重复操作未生效");
        }

        int changed = serviceOrderMapper.updateStatusIfExpected(
                id, status, "PENDING_ACCEPT", nextRound, LocalDateTime.now());
        if (changed == 0) {
            // 条件更新未命中说明并发下状态已被推进，本轮写入随事务回滚，绝不留半条链路
            throw new BizException("服务单【" + order.getServiceNo() + "】状态刚被变更，"
                    + busyHint(serviceOrderMapper.lockById(id)) + "，本次提交未生效，请刷新重试");
        }

        ServiceView view = serviceOrderMapper.findById(id);
        attachRounds(List.of(view));
        return decorate(view);
    }

    /**
     * 店员验收：只有待验收可操作。
     * ACCEPTED 验收通过 → 服务单 DONE（终态），维修/大修类乐器恢复在库（处理期间报废则不复活）；
     * REJECTED 退回返修 → 必须写仍存在的故障与返修要求，服务单 REWORKING，乐器继续维修中。
     */
    @Transactional
    public ServiceView accept(Long id, AcceptanceRequest request) {
        if (id == null) {
            throw new BizException("请指定要验收的服务单");
        }
        if (request == null
                || (!PHASE_ACCEPTED.equals(request.getDecision()) && !PHASE_REJECTED.equals(request.getDecision()))) {
            throw new BizException("验收决定不合法，只能是 ACCEPTED（通过）或 REJECTED（退回返修）");
        }
        boolean rejected = PHASE_REJECTED.equals(request.getDecision());
        String remainingFault = null;
        String reworkRequest = null;
        if (rejected) {
            remainingFault = required(request.getRemainingFault(), "仍存在的故障");
            reworkRequest = required(request.getReworkRequest(), "返修要求");
        }
        String operatorName = required(
                request.getOperatorName() == null ? null : request.getOperatorName().trim(),
                "验收店员");

        ServiceView order = serviceOrderMapper.lockById(id);
        if (order == null) {
            throw new BizException("服务单不存在，无法验收");
        }
        if (!"PENDING_ACCEPT".equals(order.getStatus())) {
            throw new BizException("服务单【" + order.getServiceNo() + "】" + busyHint(order)
                    + "，不是待验收状态，本次验收未生效，请刷新后查看最新结论");
        }
        int round = nz(order.getCurrentRound());
        if (round <= 0
                || completionMapper.countByOrderRoundPhase(id, round, PHASE_COMPLETION) == 0) {
            throw new BizException("服务单【" + order.getServiceNo() + "】第 " + round
                    + " 轮缺少完工记录，无法验收");
        }

        ServiceCompletion decision = new ServiceCompletion();
        decision.setServiceOrderId(id);
        decision.setRoundNo(round);
        decision.setPhase(request.getDecision());
        decision.setOperatorName(operatorName);
        decision.setCreatedAt(LocalDateTime.now());
        if (rejected) {
            decision.setRemainingFault(remainingFault);
            decision.setReworkRequest(reworkRequest);
        }
        try {
            completionMapper.insert(decision);
        } catch (DuplicateKeyException e) {
            CompletionRoundView exists = completionMapper.findDecision(id, round);
            String decisionName = exists == null ? "已有结论"
                    : MusicDict.completionPhaseName(exists.getPhase());
            throw new BizException("服务单【" + order.getServiceNo() + "】第 " + round
                    + " 轮已被验收（" + decisionName + "），你的操作未生效");
        }

        String newStatus = rejected ? "REWORKING" : "DONE";
        int changed = serviceOrderMapper.updateStatusIfExpected(
                id, "PENDING_ACCEPT", newStatus, round, LocalDateTime.now());
        if (changed == 0) {
            throw new BizException("服务单【" + order.getServiceNo() + "】状态刚被变更，验收未生效，请刷新重试");
        }

        // 只有「最新一轮验收通过」才联动乐器；调律类不动乐器状态
        if (!rejected && MusicDict.isRepairKind(order.getServiceType())) {
            restoreInstrumentAfterAccept(order);
        }

        ServiceView view = serviceOrderMapper.findById(id);
        attachRounds(List.of(view));
        return decorate(view);
    }

    /**
     * 开始施工的乐器联动：
     * 维修/大修类 → 记录乐器原状态快照，在库的置为维修中；已维修中保持；已报废禁止开工。
     * 调律类 → 不动乐器状态，快照保持 NULL。
     */
    private void startDoing(ServiceView order, LocalDateTime now) {
        Instrument instrument = instrumentMapper.lockById(order.getInstrumentId());
        if (instrument == null) {
            throw new BizException("关联乐器不存在，无法开始施工");
        }
        if ("SCRAPPED".equals(instrument.getStatus())) {
            throw new BizException("乐器【" + instrument.getInstrumentNo() + " " + instrument.getName()
                    + "】已报废，不能开始施工");
        }

        String snapshot = null;
        if (MusicDict.isRepairKind(order.getServiceType())) {
            snapshot = instrument.getStatus();
            if ("IN_STOCK".equals(instrument.getStatus())) {
                int moved = instrumentMapper.updateStatusIfCurrent(
                        instrument.getId(), "IN_STOCK", "REPAIRING", now);
                if (moved == 0) {
                    throw new BizException("乐器【" + instrument.getInstrumentNo()
                            + "】状态刚被变更，开始施工未生效，请刷新重试");
                }
            }
        }
        int changed = serviceOrderMapper.markDoing(order.getId(), snapshot, now);
        if (changed == 0) {
            throw new BizException("服务单【" + order.getServiceNo()
                    + "】已不是已排期状态，开始施工未生效，请刷新重试");
        }
    }

    /**
     * 验收通过后恢复乐器：按开工前快照恢复（通常回在库，租出中送修的回租出）；
     * 处理期间已报废则保留服务记录但不复活。条件更新天然防重复恢复。
     */
    private void restoreInstrumentAfterAccept(ServiceView order) {
        Instrument instrument = instrumentMapper.lockById(order.getInstrumentId());
        if (instrument == null) {
            throw new BizException("关联乐器不存在，验收无法完成");
        }
        if ("SCRAPPED".equals(instrument.getStatus())) {
            // 报废是终态：服务单照常 DONE 留痕，乐器不复活
            return;
        }
        // 同一乐器还有其它在途维修单时，乐器继续维修中，等最后一单验收通过再恢复
        int stillOpen = serviceOrderMapper.countOpenRepairsByInstrument(
                instrument.getId(), order.getId());
        if (stillOpen > 0) {
            return;
        }
        String target = order.getInstrumentSnapshot();
        // 历史维修单没有快照时兜底回在库
        if (target == null || "REPAIRING".equals(target) || "SCRAPPED".equals(target)) {
            target = "IN_STOCK";
        }
        int restored = instrumentMapper.updateStatusIfCurrent(
                instrument.getId(), "REPAIRING", target, LocalDateTime.now());
        if (restored == 0) {
            // 乐器已不在维修中（例如处理期间被人工改态 / 已报废）：不再重复恢复，只保留服务结论
            return;
        }
    }

    /**
     * 仅保留 SERVICE_FLOW 里「已完成/已取消」视为终态。
     */
    public boolean isClosed(String status) {
        return "DONE".equals(status) || "CANCELED".equals(status);
    }

    private ServiceView decorate(ServiceView view) {
        view.setServiceTypeName(MusicDict.serviceTypeName(view.getServiceType()));
        view.setStatusName(MusicDict.serviceStatusName(view.getStatus()));
        if (view.getPlanStart() != null && view.getPlanEnd() != null) {
            long minutes = Duration.between(view.getPlanStart(), view.getPlanEnd()).toMinutes();
            view.setPlanHours((int) Math.max(0, Math.round(minutes / 60.0)));
        }
        return view;
    }

    /** 最新一轮的验收结论名；没有结论返回 null。 */
    private String latestDecisionName(List<CompletionRoundView> rounds) {
        CompletionRoundView latest = null;
        for (CompletionRoundView round : rounds) {
            if (PHASE_ACCEPTED.equals(round.getPhase()) || PHASE_REJECTED.equals(round.getPhase())) {
                if (latest == null || round.getRoundNo() >= latest.getRoundNo()) {
                    latest = round;
                }
            }
        }
        return latest == null ? null : MusicDict.completionPhaseName(latest.getPhase());
    }

    /** 并发/乱序操作提示：说清当前轮次和最新状态。 */
    private String busyHint(ServiceView order) {
        String statusName = MusicDict.serviceStatusName(order.getStatus());
        int round = nz(order.getCurrentRound());
        String roundText = round > 0 ? "，当前第 " + round + " 轮" : "";
        return "当前状态为【" + statusName + "】" + roundText;
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException("请填写「" + field + "」");
        }
        return value.trim();
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private String nextServiceNo() {
        String datePart = LocalDate.now().format(NO_DATE);
        int seq = serviceOrderMapper.countAll() + 1;
        String candidate;
        do {
            candidate = "SV-" + datePart + "-" + String.format("%03d", seq);
            seq++;
        } while (serviceOrderMapper.countByNo(candidate) > 0);
        return candidate;
    }
}
