package com.example.seleniumtool;

import com.example.seleniumtool.config.AutomationProperties;
import com.example.seleniumtool.service.StartupNotificationListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AutomationProperties.class)
public class SeleniumToolApplication {

    /**
     * Spring Boot 启动入口，同时启用定时任务和自定义配置绑定。
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SeleniumToolApplication.class);
        application.addListeners(new StartupNotificationListener());
        application.run(args);
    }
}
