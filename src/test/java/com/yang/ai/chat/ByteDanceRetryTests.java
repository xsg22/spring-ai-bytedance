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

import com.yang.ai.ByteDanceAudioTranscriptionModel;
import com.yang.ai.ByteDanceAudioTranscriptionOptions;
import com.yang.ai.ByteDanceChatModel;
import com.yang.ai.ByteDanceChatOptions;
import com.yang.ai.api.ByteDanceAudioApi;
import com.yang.ai.api.ByteDanceChatApi;
import com.yang.ai.audio.transcription.AudioTranscriptionPrompt;
import com.yang.ai.audio.transcription.AudioTranscriptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static com.yang.ai.api.ByteDanceAudioApi.*;
import static com.yang.ai.api.ByteDanceChatApi.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

/**
 * @author yang
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class ByteDanceRetryTests {

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
			onSuccessRetryCount = context.getRetryCount();
		}

		@Override
		public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
				Throwable throwable) {
			onErrorRetryCount = context.getRetryCount();
		}

	}

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private @Mock ByteDanceChatApi byteDanceChatApi;

	private @Mock ByteDanceAudioApi byteDanceAudioApi;

	private ByteDanceChatModel chatModel;

//	private ByteDanceEmbeddingModel embeddingModel;

	private ByteDanceAudioTranscriptionModel audioTranscriptionModel;

	@BeforeEach
	public void beforeEach() {
		retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		retryListener = new TestRetryListener();
		retryTemplate.registerListener(retryListener);

		chatModel = new ByteDanceChatModel(byteDanceChatApi, ByteDanceChatOptions.builder().build(), null, retryTemplate);
//		embeddingModel = new ByteDanceEmbeddingModel(byteDanceChatApi, MetadataMode.EMBED,
//				ByteDanceEmbeddingOptions.builder().build(), retryTemplate);
		audioTranscriptionModel = new ByteDanceAudioTranscriptionModel(byteDanceAudioApi,
				ByteDanceAudioTranscriptionOptions.builder()
					.withModel("model")
					.withResponseFormat(TranscriptResponseFormat.JSON)
					.build(),
				retryTemplate);
	}

	@Test
	public void byteDanceChatTransientError() {

		var choice = new ChatCompletion.Choice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", ChatCompletionMessage.Role.ASSISTANT), null);
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", List.of(choice), 666l, "model", null,
				new Usage(10, 10, 10));

		when(byteDanceChatApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = chatModel.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void byteDanceChatNonTransientError() {
		when(byteDanceChatApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatModel.call(new Prompt("text")));
	}

	@Test
	public void byteDanceChatStreamTransientError() {

		var choice = new ChatCompletionChunk.ChunkChoice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", ChatCompletionMessage.Role.ASSISTANT), null);
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", List.of(choice), 666l, "model", null,
				null);

		when(byteDanceChatApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(Flux.just(expectedChatCompletion));

		var result = chatModel.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.collectList().block().get(0).getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void byteDanceChatStreamNonTransientError() {
		when(byteDanceChatApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatModel.stream(new Prompt("text")));
	}

	@Test
	public void byteDanceAudioTranscriptionTransientError() {

		var expectedResponse = new StructuredResponse("nl", 6.7f, "Transcription Text", List.of(), List.of());

		when(byteDanceAudioApi.createTranscription(isA(TranscriptionRequest.class), isA(Class.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedResponse)));

		AudioTranscriptionResponse result = audioTranscriptionModel
			.call(new AudioTranscriptionPrompt(new ClassPathResource("speech/jfk.flac")));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(expectedResponse.text());
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void byteDanceAudioTranscriptionNonTransientError() {
		when(byteDanceAudioApi.createTranscription(isA(TranscriptionRequest.class), isA(Class.class)))
				.thenThrow(new RuntimeException("Transient Error 1"));
		assertThrows(RuntimeException.class, () -> audioTranscriptionModel
				.call(new AudioTranscriptionPrompt(new ClassPathResource("speech/jfk.flac"))));
	}
}
