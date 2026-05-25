package com.smartmeeting.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.smartmeeting.admin.repository")
public class MeetingAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingAdminApplication.class, args);
    }
}
