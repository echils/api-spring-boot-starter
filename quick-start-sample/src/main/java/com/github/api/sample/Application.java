package com.github.api.sample;

import com.github.api.EnableDocumentDelegate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类
 *
 * @author echils
 */
@SpringBootApplication
@EnableDocumentDelegate
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
