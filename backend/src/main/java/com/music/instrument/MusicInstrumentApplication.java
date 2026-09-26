package com.music.instrument;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.music.instrument.mapper")
public class MusicInstrumentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicInstrumentApplication.class, args);
    }
}
