package com.Midterm.stock.repository;

import com.Midterm.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Stock findByStockCode(String stockCode);
}