package com.Midterm.stock.repository;

import com.Midterm.stock.entity.NewsImpact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsImpactRepository extends JpaRepository<NewsImpact, Long> {
}
