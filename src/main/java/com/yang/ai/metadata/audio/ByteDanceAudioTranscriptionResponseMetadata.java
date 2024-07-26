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
package com.yang.ai.metadata.audio;

import com.yang.ai.api.ByteDanceAudioApi;
import com.yang.ai.metadata.ByteDanceRateLimit;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.model.ResponseMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.HashMap;

/**
 * Audio transcription metadata implementation for {@literal ByteDance}.
 *
 * @author Michael Lavelle
 * @since 0.8.1
 * @see RateLimit
 */
public class ByteDanceAudioTranscriptionResponseMetadata extends HashMap<String, Object> implements ResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, rateLimit: %4$s }";

	public static final ByteDanceAudioTranscriptionResponseMetadata NULL = new ByteDanceAudioTranscriptionResponseMetadata() {
	};

	public static ByteDanceAudioTranscriptionResponseMetadata from(ByteDanceAudioApi.StructuredResponse result) {
		Assert.notNull(result, "ByteDance Transcription must not be null");
		ByteDanceAudioTranscriptionResponseMetadata transcriptionResponseMetadata = new ByteDanceAudioTranscriptionResponseMetadata();
		return transcriptionResponseMetadata;
	}

	public static ByteDanceAudioTranscriptionResponseMetadata from(String result) {
		Assert.notNull(result, "ByteDance Transcription must not be null");
		ByteDanceAudioTranscriptionResponseMetadata transcriptionResponseMetadata = new ByteDanceAudioTranscriptionResponseMetadata();
		return transcriptionResponseMetadata;
	}

	@Nullable
	private RateLimit rateLimit;

	protected ByteDanceAudioTranscriptionResponseMetadata() {
		this(null);
	}

	protected ByteDanceAudioTranscriptionResponseMetadata(@Nullable ByteDanceRateLimit rateLimit) {
		this.rateLimit = rateLimit;
	}

	@Nullable
	public RateLimit getRateLimit() {
		RateLimit rateLimit = this.rateLimit;
		return rateLimit != null ? rateLimit : new EmptyRateLimit();
	}

	public ByteDanceAudioTranscriptionResponseMetadata withRateLimit(RateLimit rateLimit) {
		this.rateLimit = rateLimit;
		return this;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getRateLimit());
	}

}
