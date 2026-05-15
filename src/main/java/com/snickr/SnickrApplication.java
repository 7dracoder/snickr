package com.snickr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SnickrApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnickrApplication.class, args);
        System.out.println("Done！");
    }
}