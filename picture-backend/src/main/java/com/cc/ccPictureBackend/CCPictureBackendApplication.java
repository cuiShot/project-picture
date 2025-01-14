package com.cc.ccPictureBackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@MapperScan("com.cc.ccPictureBackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class CCPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CCPictureBackendApplication.class, args);
    }

}
