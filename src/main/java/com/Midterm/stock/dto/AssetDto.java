package com.Midterm.stock.dto;

import java.time.LocalDate;

public class AssetDto {

    private int month;            // 월
    private LocalDate date;       // 일자
    private int amount;           // 금액
    private String vendor;        // 구매처
    private String category;      // 최종카테고리



    public AssetDto() {}

    public AssetDto(int month, LocalDate date, int amount, String vendor, String category) {
        this.month = month;
        this.date = date;
        this.amount = amount;
        this.vendor = vendor;
        this.category = category;
    }



    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}