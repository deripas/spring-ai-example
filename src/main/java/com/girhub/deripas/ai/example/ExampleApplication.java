package com.girhub.deripas.ai.example;

import com.girhub.deripas.ai.example.repo.ChatEntryRepository;
import com.girhub.deripas.ai.example.services.ChatService;
import com.girhub.deripas.ai.example.services.PostgresChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class ExampleApplication {

	@Bean
	public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
		final MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		return builder.defaultAdvisors(advisor).build();
	}

	@Bean
	public ChatMemory chatMemory(ChatEntryRepository chatEntryRepository) {
		return PostgresChatMemory.builder()
				.maxMessages(2)
				.chatEntryRepository(chatEntryRepository)
				.build();
	}

	public static void main(String[] args) {
		SpringApplication.run(ExampleApplication.class, args);
	}

}
