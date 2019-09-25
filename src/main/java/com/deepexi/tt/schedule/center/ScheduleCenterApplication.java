package com.deepexi.tt.schedule.center;

import com.deepexi.tt.schedule.center.service.ITaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author 白猛
 */
@SpringBootApplication
@EnableScheduling
@EnableRetry
@EnableAsync
public class ScheduleCenterApplication implements ApplicationRunner {

    private static Logger logger = LoggerFactory.getLogger(ScheduleCenterApplication.class);

    @Autowired
    ITaskService taskService;

    public static void main(String[] args) {
        SpringApplication.run(ScheduleCenterApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        new Thread(() -> {
            logger.info("监听");
            while (true) {
                taskService.consumeTaskQueue();
            }
        }).start();
    }
}
