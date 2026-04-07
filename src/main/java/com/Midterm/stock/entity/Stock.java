package com.Midterm.stock.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "STOCK")
@Getter @Setter
@NoArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String stockCode;   // 종목코드 ex) 005930

    private String stockName;   // 종목명 ex) 삼성전자

    private String fullName;    // 종목전체명

    private String market;      // KOSPI / KOSDAQ

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void setUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }

    // Map으로 변환 (기존 searchStock 반환 타입과 호환)
    public java.util.Map<String, String> toMap() {
        return java.util.Map.of(
                "code", stockCode != null ? stockCode : "",
                "name", stockName != null ? stockName : "",
                "fullName", fullName != null ? fullName : "",
                "market", market != null ? market : ""
        );
    }
}