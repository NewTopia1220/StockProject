package com.Midterm.stock.repository;

import com.Midterm.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    @Query("SELECT s FROM Stock s WHERE " +
            "s.stockName LIKE %:keyword% OR " +
            "s.fullName LIKE %:keyword% OR " +
            "s.stockCode LIKE %:keyword%")
    List<Stock> findByKeyword(@Param("keyword") String keyword, Pageable pageable);
}