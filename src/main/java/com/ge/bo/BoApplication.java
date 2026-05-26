package com.ge.bo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;

import jakarta.annotation.PostConstruct;

import java.util.Arrays;

@SpringBootApplication
public class BoApplication {

    @Autowired
    private ConfigurableEnvironment env;

    public static void main(String[] args) {
        SpringApplication.run(BoApplication.class, args);
    }

    @PostConstruct
    public void debugCors() {
        System.out.println("####################### CORS 진단 시작 #######################");
        System.out.println("########## active profiles = " + Arrays.toString(env.getActiveProfiles()));
        System.out.println("########## 최종 cors.allowed-origins = [" + env.getProperty("cors.allowed-origins") + "]");

        MutablePropertySources sources = env.getPropertySources();
        sources.forEach(ps -> {
            if (ps.containsProperty("cors.allowed-origins")) {
                System.out.println(
                        "########## 출처[" + ps.getName() + "] => [" + ps.getProperty("cors.allowed-origins") + "]");
            }
        });
        System.out.println("####################### CORS 진단 끝 #######################");
    }
}