package com.smartmeeting;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.smartmeeting.repository")
public class SmartMeetingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartMeetingApplication.class, args);
    }
}
