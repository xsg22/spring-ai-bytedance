/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yang.ai.chat;

import com.yang.ai.ByteDanceChatModel;
import com.yang.ai.api.ByteDanceChatApi;
import com.yang.ai.metadata.support.ByteDanceApiResponseHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @author yang
 */
@RestClientTest(ByteDanceChatModelWithChatResponseMetadataTests.Config.class)
public class ByteDanceChatModelWithChatResponseMetadataTests {

	private static String TEST_API_KEY = "sk-1234567890";

	@Autowired
	private ByteDanceChatModel byteDanceChatModel;

	@Autowired
	private MockRestServiceServer server;

	@AfterEach
	void resetMockServer() {
		server.reset();
	}

	@Test
	void aiResponseContainsAiMetadata() {

		prepareMock();

		Prompt prompt = new Prompt("Reach for the sky.");

		ChatResponse response = this.byteDanceChatModel.call(prompt);

		assertThat(response).isNotNull();

		ChatResponseMetadata chatResponseMetadata = response.getMetadata();

		assertThat(chatResponseMetadata).isNotNull();

		Usage usage = chatResponseMetadata.getUsage();

		assertThat(usage).isNotNull();
		assertThat(usage.getPromptTokens()).isEqualTo(9L);
		assertThat(usage.getGenerationTokens()).isEqualTo(12L);
		assertThat(usage.getTotalTokens()).isEqualTo(21L);

		PromptMetadata promptMetadata = response.getMetadata().getPromptMetadata();

		assertThat(promptMetadata).isNotNull();
		assertThat(promptMetadata).isEmpty();

		response.getResults().forEach(generation -> {
			ChatGenerationMetadata chatGenerationMetadata = generation.getMetadata();
			assertThat(chatGenerationMetadata).isNotNull();
			assertThat(chatGenerationMetadata.getFinishReason()).isEqualTo("STOP");
			assertThat(chatGenerationMetadata.<Object>getContentFilterMetadata()).isNull();
		});
	}

	private void prepareMock() {
		server.expect(requestTo("/api/v3/chat/completions"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_KEY))
			.andRespond(withSuccess(getJson(), MediaType.APPLICATION_JSON));
	}

	private String getJson() {
		return """
					{
					  "id": "chatcmpl-123",
					  "object": "chat.completion",
					  "created": 1677652288,
					  "model": "gpt-3.5-turbo-0613",
					  "choices": [{
						"index": 0,
						"message": {
						  "role": "assistant",
						  "content": "I surrender!"
						},
						"finish_reason": "stop"
					  }],
					  "usage": {
						"prompt_tokens": 9,
						"completion_tokens": 12,
						"total_tokens": 21
					  }
					}
				""";
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public ByteDanceChatApi chatCompletionApi(RestClient.Builder builder, WebClient.Builder webClientBuilder) {
			return new ByteDanceChatApi("", TEST_API_KEY, builder, webClientBuilder);
		}

		@Bean
		public ByteDanceChatModel byteDanceClient(ByteDanceChatApi byteDanceChatApi) {
			return new ByteDanceChatModel(byteDanceChatApi);
		}
	}

}
