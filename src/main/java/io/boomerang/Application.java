package io.boomerang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.mongock.runner.springboot.EnableMongock;

@SpringBootApplication
@EnableMongock
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

}

