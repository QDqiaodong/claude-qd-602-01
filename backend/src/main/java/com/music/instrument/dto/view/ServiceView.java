package com.music.instrument.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务单行视图（带乐器、调律师的展示字段）。
 */
public class ServiceView {

    private Long id;
    private String serviceNo;
    private Long instrumentId;
    private String instrumentNo;
    private String instrumentName;
    private String instrumentCategory;
    private String instrumentStatus;
    private Long technicianId;
    private String technicianNo;
    private String technicianName;
    private String technicianLevel;
    private String serviceType;
    private String serviceTypeName;
    private LocalDateTime planStart;
    private LocalDateTime planEnd;
    private Integer planHours;
    private BigDecimal fee;
    private String status;
    private String statusName;
    /** 当前轮次：每提交一次完工 +1 */
    private Integer currentRound;
    /** 维修类开始施工时的乐器原状态，验收通过后据此恢复 */
    private String instrumentSnapshot;
    /** 最新一轮验收结论名称：验收通过 / 退回返修；未验收为 null */
    private String latestDecisionName;
    /** 按轮次、时间排列的完整完工/验收记录；仅详情/历史接口装配，列表可为 null */
    private List<CompletionRoundView> rounds;
    private String resultNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getServiceNo() {
        return serviceNo;
    }

    public void setServiceNo(String serviceNo) {
        this.serviceNo = serviceNo;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getInstrumentNo() {
        return instrumentNo;
    }

    public void setInstrumentNo(String instrumentNo) {
        this.instrumentNo = instrumentNo;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getInstrumentCategory() {
        return instrumentCategory;
    }

    public void setInstrumentCategory(String instrumentCategory) {
        this.instrumentCategory = instrumentCategory;
    }

    public String getInstrumentStatus() {
        return instrumentStatus;
    }

    public void setInstrumentStatus(String instrumentStatus) {
        this.instrumentStatus = instrumentStatus;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(Long technicianId) {
        this.technicianId = technicianId;
    }

    public String getTechnicianNo() {
        return technicianNo;
    }

    public void setTechnicianNo(String technicianNo) {
        this.technicianNo = technicianNo;
    }

    public String getTechnicianName() {
        return technicianName;
    }

    public void setTechnicianName(String technicianName) {
        this.technicianName = technicianName;
    }

    public String getTechnicianLevel() {
        return technicianLevel;
    }

    public void setTechnicianLevel(String technicianLevel) {
        this.technicianLevel = technicianLevel;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getServiceTypeName() {
        return serviceTypeName;
    }

    public void setServiceTypeName(String serviceTypeName) {
        this.serviceTypeName = serviceTypeName;
    }

    public LocalDateTime getPlanStart() {
        return planStart;
    }

    public void setPlanStart(LocalDateTime planStart) {
        this.planStart = planStart;
    }

    public LocalDateTime getPlanEnd() {
        return planEnd;
    }

    public void setPlanEnd(LocalDateTime planEnd) {
        this.planEnd = planEnd;
    }

    public Integer getPlanHours() {
        return planHours;
    }

    public void setPlanHours(Integer planHours) {
        this.planHours = planHours;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public Integer getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(Integer currentRound) {
        this.currentRound = currentRound;
    }

    public String getInstrumentSnapshot() {
        return instrumentSnapshot;
    }

    public void setInstrumentSnapshot(String instrumentSnapshot) {
        this.instrumentSnapshot = instrumentSnapshot;
    }

    public String getLatestDecisionName() {
        return latestDecisionName;
    }

    public void setLatestDecisionName(String latestDecisionName) {
        this.latestDecisionName = latestDecisionName;
    }

    public List<CompletionRoundView> getRounds() {
        return rounds;
    }

    public void setRounds(List<CompletionRoundView> rounds) {
        this.rounds = rounds;
    }

    public String getResultNote() {
        return resultNote;
    }

    public void setResultNote(String resultNote) {
        this.resultNote = resultNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
