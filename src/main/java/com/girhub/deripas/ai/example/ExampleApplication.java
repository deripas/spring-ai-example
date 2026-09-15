package com.girhub.deripas.ai.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class ExampleApplication {

	@Bean
	public ChatClient chatClient(ChatClient.Builder builder) {
		return builder.build();
	}

	public static void main(String[] args) {
		final ConfigurableApplicationContext context = SpringApplication.run(ExampleApplication.class, args);
		final ChatClient client = context.getBean(ChatClient.class);
		final String response = client.prompt("Привет! Ты кто?")
				.call()
				.content();
		log.info(response);
	}

}
