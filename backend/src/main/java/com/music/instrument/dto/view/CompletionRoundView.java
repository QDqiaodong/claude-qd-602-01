package com.music.instrument.dto.view;

import java.time.LocalDateTime;

/**
 * 一条完工 / 验收记录的展示视图。同一轮次内：
 * phase=COMPLETION 为调律师完工提交，phase=ACCEPTED / REJECTED 为店员验收结论。
 */
public class CompletionRoundView {

    private Long id;
    private Long serviceOrderId;
    private Integer roundNo;
    private String phase;
    private String phaseName;
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

    public String getPhaseName() {
        return phaseName;
    }

    public void setPhaseName(String phaseName) {
        this.phaseName = phaseName;
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
