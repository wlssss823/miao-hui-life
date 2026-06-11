package com.dianpingplus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.dianpingplus.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
public class DianpingPlusApplication {

    public static void main(String[] args) {
        SpringApplication.run(DianpingPlusApplication.class, args);
    }

}
