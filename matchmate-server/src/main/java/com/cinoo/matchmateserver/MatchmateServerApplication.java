package com.cinoo.matchmateserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.cinoo.matchmateserver.mapper")
public class MatchmateServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchmateServerApplication.class, args);
    }

}
