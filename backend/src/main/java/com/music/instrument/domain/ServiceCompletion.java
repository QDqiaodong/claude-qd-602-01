package com.music.instrument.domain;

import java.time.LocalDateTime;

/**
 * 完工 / 验收轮次记录实体。
 * 一张服务单可产生多轮，每轮最多三条记录（完工提交 + 验收通过/退回），
 * 永远只追加、不改写历史，因此不提供任何 update 语义。
 */
public class ServiceCompletion {

    private Long id;
    private Long serviceOrderId;
    private Integer roundNo;
    /** COMPLETION 完工提交 / ACCEPTED 验收通过 / REJECTED 退回返修 */
    private String phase;
    private String processNote;
    private String checkResult;
    private String deliveryNote;
    private String remainingFault;
    private String reworkRequest;
    private String operatorName;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getServiceOrderId() {
        return serviceOrderId;
    }

    public void setServiceOrderId(Long serviceOrderId) {
        this.serviceOrderId = serviceOrderId;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getProcessNote() {
        return processNote;
    }

    public void setProcessNote(String processNote) {
        this.processNote = processNote;
    }

    public String getCheckResult() {
        return checkResult;
    }

    public void setCheckResult(String checkResult) {
        this.checkResult = checkResult;
    }

    public String getDeliveryNote() {
        return deliveryNote;
    }

    public void setDeliveryNote(String deliveryNote) {
        this.deliveryNote = deliveryNote;
    }

    public String getRemainingFault() {
        return remainingFault;
    }

    public void setRemainingFault(String remainingFault) {
        this.remainingFault = remainingFault;
    }

    public String getReworkRequest() {
        return reworkRequest;
    }

    public void setReworkRequest(String reworkRequest) {
        this.reworkRequest = reworkRequest;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
