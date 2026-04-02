package com.Midterm.stock.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stockCode;   // 종목코드 ex) 005930
    private String stockName;   // 종목명 ex) 삼성전자
}