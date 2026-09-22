package com.girhub.deripas.ai.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Slf4j
@SpringBootApplication
public class ExampleApplication {

	@Bean
	public ChatClient chatClient(ChatClient.Builder builder) {
		return builder.build();
	}

	public static void main(String[] args) {
		SpringApplication.run(ExampleApplication.class, args);
	}

}
