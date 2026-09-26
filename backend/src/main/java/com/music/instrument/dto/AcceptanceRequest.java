package com.music.instrument.dto;

/**
 * 店员验收决定请求。
 * decision=ACCEPTED 验收通过；decision=REJECTED 退回返修（此时 remainingFault / reworkRequest 必填）。
 */
public class AcceptanceRequest {

    private String decision;
    private String remainingFault;
    private String reworkRequest;
    private String operatorName;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
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
}
