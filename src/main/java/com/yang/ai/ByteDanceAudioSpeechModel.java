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

package com.yang.ai;

import com.yang.ai.api.ByteDanceAudioApi;
import com.yang.ai.api.ByteDanceAudioApi.SpeechRequest.AudioResponseFormat;
import com.yang.ai.api.common.ByteDanceApiException;
import com.yang.ai.audio.speech.*;
import com.yang.ai.metadata.audio.ByteDanceAudioSpeechResponseMetadata;
import com.yang.ai.metadata.support.ByteDanceResponseHeaderExtractor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * OpenAI audio speech client implementation for backed by {@link ByteDanceAudioApi}.
 *
 * @author Ahmed Yousri
 * @author Hyunjoon Choi
 * @see ByteDanceAudioApi
 * @since 1.0.0-M1
 */
public class ByteDanceAudioSpeechModel implements SpeechModel, StreamingSpeechModel {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * The default options used for the audio completion requests.
	 */
	private final ByteDanceAudioSpeechOptions defaultOptions;

	/**
	 * The speed of the default voice synthesis.
	 * @see ByteDanceAudioSpeechOptions
	 */
	private static final Float SPEED = 1.0f;

	/**
	 * The retry template used to retry the OpenAI Audio API calls.
	 */
	public final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(ByteDanceApiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.build();

	/**
	 * Low-level access to the OpenAI Audio API.
	 */
	private final ByteDanceAudioApi audioApi;

	/**
	 * Initializes a new instance of the OpenAiAudioSpeechModel class with the provided
	 * ByteDanceAudioApi. It uses the model tts-1, response format mp3, voice alloy, and the
	 * default speed of 1.0.
	 * @param audioApi The ByteDanceAudioApi to use for speech synthesis.
	 */
	public ByteDanceAudioSpeechModel(ByteDanceAudioApi audioApi) {
		this(audioApi,
				ByteDanceAudioSpeechOptions.builder()
					.withModel(ByteDanceAudioApi.TtsModel.TTS_1.getValue())
					.withResponseFormat(AudioResponseFormat.MP3)
					.withVoice(ByteDanceAudioApi.SpeechRequest.Voice.ALLOY)
					.withSpeed(SPEED)
					.build());
	}

	/**
	 * Initializes a new instance of the OpenAiAudioSpeechModel class with the provided
	 * ByteDanceAudioApi and options.
	 * @param audioApi The ByteDanceAudioApi to use for speech synthesis.
	 * @param options The OpenAiAudioSpeechOptions containing the speech synthesis
	 * options.
	 */
	public ByteDanceAudioSpeechModel(ByteDanceAudioApi audioApi, ByteDanceAudioSpeechOptions options) {
		Assert.notNull(audioApi, "ByteDanceAudioApi must not be null");
		Assert.notNull(options, "OpenAiSpeechOptions must not be null");
		this.audioApi = audioApi;
		this.defaultOptions = options;
	}

	@Override
	public byte[] call(String text) {
		SpeechPrompt speechRequest = new SpeechPrompt(text);
		return call(speechRequest).getResult().getOutput();
	}

	@Override
	public SpeechResponse call(SpeechPrompt speechPrompt) {

		return this.retryTemplate.execute(ctx -> {

			ByteDanceAudioApi.SpeechRequest speechRequest = createRequestBody(speechPrompt);

			ResponseEntity<byte[]> speechEntity = this.audioApi.createSpeech(speechRequest);
			var speech = speechEntity.getBody();

			if (speech == null) {
				logger.warn("No speech response returned for speechRequest: {}", speechRequest);
				return new SpeechResponse(new Speech(new byte[0]));
			}

			RateLimit rateLimits = ByteDanceResponseHeaderExtractor.extractAiResponseHeaders(speechEntity);

			return new SpeechResponse(new Speech(speech), new ByteDanceAudioSpeechResponseMetadata(rateLimits));

		});
	}

	/**
	 * Streams the audio response for the given speech prompt.
	 * @param prompt The speech prompt containing the text and options for speech
	 * synthesis.
	 * @return A Flux of SpeechResponse objects containing the streamed audio and
	 * metadata.
	 */
	@Override
	public Flux<SpeechResponse> stream(SpeechPrompt prompt) {
		return this.audioApi.stream(this.createRequestBody(prompt))
			.map(entity -> new SpeechResponse(new Speech(entity.getBody()), new ByteDanceAudioSpeechResponseMetadata(
					ByteDanceResponseHeaderExtractor.extractAiResponseHeaders(entity))));
	}

	private ByteDanceAudioApi.SpeechRequest createRequestBody(SpeechPrompt request) {
		ByteDanceAudioSpeechOptions options = this.defaultOptions;

		if (request.getOptions() != null) {
			if (request.getOptions() instanceof ByteDanceAudioSpeechOptions runtimeOptions) {
				options = this.merge(runtimeOptions, options);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type SpeechOptions: "
						+ request.getOptions().getClass().getSimpleName());
			}
		}

		String input = StringUtils.isNotBlank(options.getInput()) ? options.getInput()
				: request.getInstructions().getText();

		ByteDanceAudioApi.SpeechRequest.Builder requestBuilder = ByteDanceAudioApi.SpeechRequest.builder()
			.withModel(options.getModel())
			.withInput(input)
			.withVoice(options.getVoice())
			.withResponseFormat(options.getResponseFormat())
			.withSpeed(options.getSpeed());

		return requestBuilder.build();
	}

	private ByteDanceAudioSpeechOptions merge(ByteDanceAudioSpeechOptions source, ByteDanceAudioSpeechOptions target) {
		ByteDanceAudioSpeechOptions.Builder mergedBuilder = ByteDanceAudioSpeechOptions.builder();

		mergedBuilder.withModel(source.getModel() != null ? source.getModel() : target.getModel());
		mergedBuilder.withInput(source.getInput() != null ? source.getInput() : target.getInput());
		mergedBuilder.withVoice(source.getVoice() != null ? source.getVoice() : target.getVoice());
		mergedBuilder.withResponseFormat(
				source.getResponseFormat() != null ? source.getResponseFormat() : target.getResponseFormat());
		mergedBuilder.withSpeed(source.getSpeed() != null ? source.getSpeed() : target.getSpeed());

		return mergedBuilder.build();
	}

}
