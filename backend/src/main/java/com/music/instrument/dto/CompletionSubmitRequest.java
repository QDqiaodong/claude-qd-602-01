package com.music.instrument.dto;

/**
 * 调律师完工提交请求：本轮处理内容、检测结果、交付说明三件套都必须写清。
 */
public class CompletionSubmitRequest {

    private String processNote;
    private String checkResult;
    private String deliveryNote;

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
}
