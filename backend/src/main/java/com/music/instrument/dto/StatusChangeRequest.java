package com.music.instrument.dto;

/**
 * 通用状态流转请求（乐器状态机 / 服务单状态机复用）。
 */
public class StatusChangeRequest {

    private String status;
    private String note;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
