package com.girhub.deripas.ai.example;

import com.girhub.deripas.ai.example.repo.ChatEntryRepository;
import com.girhub.deripas.ai.example.services.PostgresChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.util.List;

@Slf4j
@SpringBootApplication
public class ExampleApplication {

	private static final PromptTemplate PROMPT_TEMPLATE = new PromptTemplate("""
       {query}

       Контекстная информация приведена ниже и окружена ---------------------

       ---------------------
       {question_answer_context}
       ---------------------

       Опираясь на контекст и предоставленную историю общения, а не на имеющиеся ранее знания,
       ответьте на комментарий пользователя. Если ответа нет в контексте, сообщите
       пользователю, что вы не можете ответить на вопрос.
       """);

	@Bean
	@Order(1)
	public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
		return MessageChatMemoryAdvisor.builder(chatMemory)
				.build();
	}

	@Bean
	@Order(2)
	public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
		return QuestionAnswerAdvisor.builder(vectorStore)
				.promptTemplate(PROMPT_TEMPLATE)
				.searchRequest(SearchRequest.builder().topK(4).build())
				.build();
	}

	@Bean
	@Order(3)
	public SimpleLoggerAdvisor simpleLoggerAdvisor() {
		return SimpleLoggerAdvisor.builder()
				.build();
	}

	@Bean
	public ChatClient chatClient(ChatClient.Builder builder, List<Advisor> advisors) {
		return builder.defaultAdvisors(advisors).build();
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
