package com.music.instrument.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 乐器入库请求。
 */
public class InstrumentCreateRequest {

    private String instrumentNo;
    private String name;
    private String category;
    private String brand;
    private BigDecimal dailyRent;
    private LocalDate purchaseDate;
    private String note;

    public String getInstrumentNo() {
        return instrumentNo;
    }

    public void setInstrumentNo(String instrumentNo) {
        this.instrumentNo = instrumentNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public BigDecimal getDailyRent() {
        return dailyRent;
    }

    public void setDailyRent(BigDecimal dailyRent) {
        this.dailyRent = dailyRent;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
