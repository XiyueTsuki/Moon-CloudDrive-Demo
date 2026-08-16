package com.xiyuetsuki.moonclouddrivedemo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xiyuetsuki.moonclouddrivedemo.mapper")
public class MoonCloudDriveDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoonCloudDriveDemoApplication.class, args);
	}

}
