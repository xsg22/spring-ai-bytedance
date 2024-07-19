package com.yang.ai.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author: double
 * @date: 2024/5/30 15:23
 */
@EnabledIfEnvironmentVariable(named = "BYTE_DANCE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "MODEL_END_POINT", matches = ".+")
class ByteDanceChatApiTest {

    ByteDanceChatApi byteDanceChatApi = new ByteDanceChatApi(System.getenv("BYTE_DANCE_API_KEY"));

    String modelEndPoint = System.getenv("MODEL_END_POINT");

    @Test
    void chatCompletionEntity() {
        ByteDanceChatApi.ChatCompletionMessage chatCompletionMessage = new ByteDanceChatApi.ChatCompletionMessage("Hello world", ByteDanceChatApi.ChatCompletionMessage.Role.USER);
        ResponseEntity<ByteDanceChatApi.ChatCompletion> response = byteDanceChatApi.chatCompletionEntity(
                new ByteDanceChatApi.ChatCompletionRequest(List.of(chatCompletionMessage), modelEndPoint, 0.8f, false));

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void chatCompletionStream() {
        ByteDanceChatApi.ChatCompletionMessage chatCompletionMessage = new ByteDanceChatApi.ChatCompletionMessage("Hello world", ByteDanceChatApi.ChatCompletionMessage.Role.USER);
        Flux<ByteDanceChatApi.ChatCompletionChunk> response = byteDanceChatApi.chatCompletionStream(
                new ByteDanceChatApi.ChatCompletionRequest(List.of(chatCompletionMessage), modelEndPoint, 0.8f, true));

        assertThat(response).isNotNull();
        assertThat(response.collectList().block()).isNotNull();
    }
}