package com.smartmeeting;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能会议系统 Spring Boot 启动类。
 * <p>
 * 提供会议创建、AI 主持、实时转写、纪要生成、待办跟踪及飞书机器人集成等功能。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.smartmeeting.repository")
public class SmartMeetingApplication {

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SmartMeetingApplication.class, args);
    }
}
