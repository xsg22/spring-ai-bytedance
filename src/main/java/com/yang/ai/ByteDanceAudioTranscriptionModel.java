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
/*
* Copyright 2024-2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.yang.ai;

import com.yang.ai.api.ByteDanceAudioApi;
import com.yang.ai.api.ByteDanceAudioApi.StructuredResponse;
import com.yang.ai.audio.transcription.AudioTranscription;
import com.yang.ai.audio.transcription.AudioTranscriptionPrompt;
import com.yang.ai.audio.transcription.AudioTranscriptionResponse;
import com.yang.ai.metadata.audio.ByteDanceAudioTranscriptionResponseMetadata;
import com.yang.ai.metadata.support.ByteDanceResponseHeaderExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.model.Model;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * ByteDance audio transcription client implementation for backed by {@link ByteDanceAudioApi}.
 * You provide as input the audio file you want to transcribe and the desired output file
 * format of the transcription of the audio.
 *
 * @author xsg
 * @see ByteDanceAudioApi
 */
public class ByteDanceAudioTranscriptionModel implements Model<AudioTranscriptionPrompt, AudioTranscriptionResponse> {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ByteDanceAudioTranscriptionOptions defaultOptions;

	public final RetryTemplate retryTemplate;

	private final ByteDanceAudioApi audioApi;

	/**
	 * ByteDanceAudioTranscriptionModel is a client class used to interact with the ByteDance
	 * Audio Transcription API.
	 * @param audioApi The ByteDanceAudioApi instance to be used for making API calls.
	 */
	public ByteDanceAudioTranscriptionModel(ByteDanceAudioApi audioApi) {
		this(audioApi,
				ByteDanceAudioTranscriptionOptions.builder()
					.withModel(ByteDanceAudioApi.WhisperModel.WHISPER_1.getValue())
					.withResponseFormat(ByteDanceAudioApi.TranscriptResponseFormat.JSON)
					.withTemperature(0.7f)
					.build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * ByteDanceAudioTranscriptionModel is a client class used to interact with the ByteDance
	 * Audio Transcription API.
	 * @param audioApi The ByteDanceAudioApi instance to be used for making API calls.
	 * @param options The ByteDanceAudioTranscriptionOptions instance for configuring the
	 * audio transcription.
	 */
	public ByteDanceAudioTranscriptionModel(ByteDanceAudioApi audioApi, ByteDanceAudioTranscriptionOptions options) {
		this(audioApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * ByteDanceAudioTranscriptionModel is a client class used to interact with the ByteDance
	 * Audio Transcription API.
	 * @param audioApi The ByteDanceAudioApi instance to be used for making API calls.
	 * @param options The ByteDanceAudioTranscriptionOptions instance for configuring the
	 * audio transcription.
	 * @param retryTemplate The RetryTemplate instance for retrying failed API calls.
	 */
	public ByteDanceAudioTranscriptionModel(ByteDanceAudioApi audioApi, ByteDanceAudioTranscriptionOptions options,
                                            RetryTemplate retryTemplate) {
		Assert.notNull(audioApi, "ByteDanceAudioApi must not be null");
		Assert.notNull(options, "ByteDanceTranscriptionOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		this.audioApi = audioApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	public String call(Resource audioResource) {
		AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioResource);
		return call(transcriptionRequest).getResult().getOutput();
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt request) {

		return this.retryTemplate.execute(ctx -> {

			Resource audioResource = request.getInstructions();

			ByteDanceAudioApi.TranscriptionRequest requestBody = createRequestBody(request);

			if (requestBody.responseFormat().isJsonType()) {

				ResponseEntity<StructuredResponse> transcriptionEntity = this.audioApi.createTranscription(requestBody,
						StructuredResponse.class);

				var transcription = transcriptionEntity.getBody();

				if (transcription == null) {
					logger.warn("No transcription returned for request: {}", audioResource);
					return new AudioTranscriptionResponse(null);
				}

				AudioTranscription transcript = new AudioTranscription(transcription.text());

				RateLimit rateLimits = ByteDanceResponseHeaderExtractor.extractAiResponseHeaders(transcriptionEntity);

				return new AudioTranscriptionResponse(transcript,
						ByteDanceAudioTranscriptionResponseMetadata.from(transcriptionEntity.getBody())
							.withRateLimit(rateLimits));

			}
			else {

				ResponseEntity<String> transcriptionEntity = this.audioApi.createTranscription(requestBody,
						String.class);

				var transcription = transcriptionEntity.getBody();

				if (transcription == null) {
					logger.warn("No transcription returned for request: {}", audioResource);
					return new AudioTranscriptionResponse(null);
				}

				AudioTranscription transcript = new AudioTranscription(transcription);

				RateLimit rateLimits = ByteDanceResponseHeaderExtractor.extractAiResponseHeaders(transcriptionEntity);

				return new AudioTranscriptionResponse(transcript,
						ByteDanceAudioTranscriptionResponseMetadata.from(transcriptionEntity.getBody())
							.withRateLimit(rateLimits));
			}
		});
	}

	ByteDanceAudioApi.TranscriptionRequest createRequestBody(AudioTranscriptionPrompt request) {

		ByteDanceAudioTranscriptionOptions options = this.defaultOptions;

		if (request.getOptions() != null) {
			if (request.getOptions() instanceof ByteDanceAudioTranscriptionOptions runtimeOptions) {
				options = this.merge(runtimeOptions, options);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type TranscriptionOptions: "
						+ request.getOptions().getClass().getSimpleName());
			}
		}

		ByteDanceAudioApi.TranscriptionRequest audioTranscriptionRequest = ByteDanceAudioApi.TranscriptionRequest.builder()
			.withFile(toBytes(request.getInstructions()))
			.withResponseFormat(options.getResponseFormat())
			.withPrompt(options.getPrompt())
			.withTemperature(options.getTemperature())
			.withLanguage(options.getLanguage())
			.withModel(options.getModel())
			.withGranularityType(options.getGranularityType())
			.build();

		return audioTranscriptionRequest;
	}

	private byte[] toBytes(Resource resource) {
		try {
			return resource.getInputStream().readAllBytes();
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to read resource: " + resource, e);
		}
	}

	private ByteDanceAudioTranscriptionOptions merge(ByteDanceAudioTranscriptionOptions source,
			ByteDanceAudioTranscriptionOptions target) {

		if (source == null) {
			source = new ByteDanceAudioTranscriptionOptions();
		}

		ByteDanceAudioTranscriptionOptions merged = new ByteDanceAudioTranscriptionOptions();
		merged.setLanguage(source.getLanguage() != null ? source.getLanguage() : target.getLanguage());
		merged.setModel(source.getModel() != null ? source.getModel() : target.getModel());
		merged.setPrompt(source.getPrompt() != null ? source.getPrompt() : target.getPrompt());
		merged.setResponseFormat(
				source.getResponseFormat() != null ? source.getResponseFormat() : target.getResponseFormat());
		merged.setTemperature(source.getTemperature() != null ? source.getTemperature() : target.getTemperature());
		merged.setGranularityType(
				source.getGranularityType() != null ? source.getGranularityType() : target.getGranularityType());
		return merged;
	}

}
