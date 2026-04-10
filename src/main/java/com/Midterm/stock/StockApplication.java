package com.Midterm.stock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling  // ★ 이 어노테이션이 메인 클래스 위에 있어야 함!
@SpringBootApplication

public class StockApplication {

	public static void main(String[] args) {
		System.out.println("★ 서버 시작 시도 중 ★");
		SpringApplication.run(StockApplication.class, args);
	}

}
