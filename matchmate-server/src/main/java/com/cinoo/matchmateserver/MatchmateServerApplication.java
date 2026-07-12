/*
 * Copyright 2026 CintaOvO
 * Licensed under the Apache License, Version 2.0.
 * Original project: https://github.com/Cinnamo-roll/Cinta-MatchMate
 */
package com.cinoo.matchmateserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan({
        "com.cinoo.matchmateserver.user.mapper",
        "com.cinoo.matchmateserver.tag.mapper",
        "com.cinoo.matchmateserver.chat.mapper",
        "com.cinoo.matchmateserver.card.mapper"
})
@EnableCaching
@EnableScheduling
@EnableAsync
public class MatchmateServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchmateServerApplication.class, args);
    }

}
