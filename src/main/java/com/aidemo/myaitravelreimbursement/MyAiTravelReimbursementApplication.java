package com.aidemo.myaitravelreimbursement;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aidemo.myaitravelreimbursement.mapper")
public class MyAiTravelReimbursementApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyAiTravelReimbursementApplication.class, args);
    }
}
