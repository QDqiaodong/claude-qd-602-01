package com.music.instrument.dto;

import java.time.LocalDate;

/**
 * 办理归还请求。
 */
public class ReturnRequest {

    private LocalDate returnDate;
    private String note;

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
