///*
// * Copyright 2024-2024 the original author or authors.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      https://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.yang.ai.chat;
//
//import com.yang.ai.ByteDanceChatModel;
//import com.yang.ai.ByteDanceChatOptions;
//import com.yang.ai.api.ByteDanceChatApi;
//import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.ValueSource;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.ai.chat.client.AdvisedRequest;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.chat.client.RequestResponseAdvisor;
//import org.springframework.ai.chat.model.ChatResponse;
//import org.springframework.ai.converter.BeanOutputConverter;
//import org.springframework.ai.model.function.FunctionCallbackContext;
//import org.springframework.ai.retry.RetryUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.SpringBootConfiguration;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Description;
//import org.springframework.core.ParameterizedTypeReference;
//import reactor.core.publisher.Flux;
//
//import java.util.List;
//import java.util.Map;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
///**
// * @author yang
// */
//@SpringBootTest
//@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
//@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
//@EnabledIfEnvironmentVariable(named = "BYTE_DANCE_API_KEY", matches = ".+")
//@EnabledIfEnvironmentVariable(named = "MODEL_END_POINT", matches = ".+")
//public class ByteDancePaymentTransactionIT {
//
//	private final static Logger logger = LoggerFactory.getLogger(ByteDancePaymentTransactionIT.class);
//
//	@Autowired
//	ChatClient chatClient;
//
//    static String modelEndPoint = System.getenv("MODEL_END_POINT");
//
//	record TransactionStatusResponse(String id, String status) {
//	}
//
//	private static class LoggingAdvisor implements RequestResponseAdvisor {
//
//		private final Logger logger = LoggerFactory.getLogger(LoggingAdvisor.class);
//
//		@Override
//		public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
//			logger.info("System text: \n" + request.systemText());
//			logger.info("System params: " + request.systemParams());
//			logger.info("User text: \n" + request.userText());
//			logger.info("User params:" + request.userParams());
//			logger.info("Function names: " + request.functionNames());
//
//			logger.info("Options: " + request.chatOptions().toString());
//
//			return request;
//		}
//
//		@Override
//		public ChatResponse adviseResponse(ChatResponse response, Map<String, Object> context) {
//			logger.info("Response: " + response);
//			return response;
//		}
//
//	}
//
//	@ParameterizedTest(name = "{0} : {displayName} ")
//	@ValueSource(strings = { "paymentStatus", "paymentStatuses" })
//	public void transactionPaymentStatuses(String functionName) {
//		List<TransactionStatusResponse> content = this.chatClient.prompt()
//			.advisors(new LoggingAdvisor())
//			.functions(functionName)
//			.user("""
//					What is the status of my payment transactions 001, 002 and 003?
//					""")
//			.call()
//			.entity(new ParameterizedTypeReference<List<TransactionStatusResponse>>() {
//			});
//
//		logger.info("" + content);
//
//		assertThat(content.get(0).id()).isEqualTo("001");
//		assertThat(content.get(0).status()).isEqualTo("pending");
//
//		assertThat(content.get(1).id()).isEqualTo("002");
//		assertThat(content.get(1).status()).isEqualTo("approved");
//
//		assertThat(content.get(2).id()).isEqualTo("003");
//		assertThat(content.get(2).status()).isEqualTo("rejected");
//	}
//
//	@ParameterizedTest(name = "{0} : {displayName} ")
//	@ValueSource(strings = { "paymentStatus", "paymentStatuses" })
//	public void streamingPaymentStatuses(String functionName) {
//
//		var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<TransactionStatusResponse>>() {
//		});
//
//		Flux<String> flux = this.chatClient.prompt()
//			.advisors(new LoggingAdvisor())
//			.functions(functionName)
//			.user(u -> u.text("""
//					What is the status of my payment transactions 001, 002 and 003?
//
//					{format}
//					""").param("format", converter.getFormat()))
//			.stream()
//			.content();
//
//		String content = flux.collectList().block().stream().collect(Collectors.joining());
//
//		List<TransactionStatusResponse> structure = converter.convert(content);
//		logger.info("" + content);
//
//		assertThat(structure.get(0).id()).isEqualTo("001");
//		assertThat(structure.get(0).status()).isEqualTo("pending");
//
//		assertThat(structure.get(1).id()).isEqualTo("002");
//		assertThat(structure.get(1).status()).isEqualTo("approved");
//
//		assertThat(structure.get(2).id()).isEqualTo("003");
//		assertThat(structure.get(2).status()).isEqualTo("rejected");
//	}
//
//	record Transaction(String id) {
//	}
//
//	record Status(String name) {
//	}
//
//	record Transactions(List<Transaction> transactions) {
//	}
//
//	record Statuses(List<Status> statuses) {
//	}
//
//	private static final Map<Transaction, Status> DATASET = Map.of(new Transaction("001"), new Status("pending"),
//			new Transaction("002"), new Status("approved"), new Transaction("003"), new Status("rejected"));
//
//	@SpringBootConfiguration
//	public static class TestConfiguration {
//
//		@Bean
//		@Description("Get the status of a single payment transaction")
//		public Function<Transaction, Status> paymentStatus() {
//			return transaction -> {
//				logger.info("Single transaction: " + transaction);
//				return DATASET.get(transaction);
//			};
//		}
//
//		@Bean
//		@Description("Get the list statuses of a list of payment transactions")
//		public Function<Transactions, Statuses> paymentStatuses() {
//			return transactions -> {
//				logger.info("List of transactions: " + transactions);
//				return new Statuses(transactions.transactions().stream().map(t -> DATASET.get(t)).toList());
//			};
//		}
//
//		@Bean
//		public ChatClient chatClient(ByteDanceChatModel chatModel) {
//			return ChatClient.builder(chatModel).build();
//		}
//
//		@Bean
//		public ByteDanceChatApi chatCompletionApi() {
//			return new ByteDanceChatApi(System.getenv("BYTE_DANCE_API_KEY"));
//		}
//
//		@Bean
//		public ByteDanceChatModel byteDanceClient(ByteDanceChatApi byteDanceChatApi, FunctionCallbackContext functionCallbackContext) {
//			return new ByteDanceChatModel(byteDanceChatApi,
//					ByteDanceChatOptions.builder()
//						.withModel(modelEndPoint)
//						.withTemperature(0.1f)
//						.build(),
//					functionCallbackContext, RetryUtils.DEFAULT_RETRY_TEMPLATE);
//		}
//
//		/**
//		 * Because of the OPEN_API_SCHEMA type, the FunctionCallbackContext instance must
//		 * different from the other JSON schema types.
//		 */
//		@Bean
//		public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
//			FunctionCallbackContext manager = new FunctionCallbackContext();
//			manager.setApplicationContext(context);
//			return manager;
//		}
//
//	}
//
//}
