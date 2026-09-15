package net.boomerangplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;

import io.mongock.runner.springboot.EnableMongock;

@SpringBootApplication
@EnableAutoConfiguration(exclude={MongoAutoConfiguration.class})
@EnableMongock
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
