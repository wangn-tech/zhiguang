package com.wangning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ZhiGuangApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhiGuangApplication.class, args);
    }
}
