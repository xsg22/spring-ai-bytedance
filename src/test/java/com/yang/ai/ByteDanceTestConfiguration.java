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

import com.yang.ai.api.ByteDanceChatApi;
import com.yang.ai.api.ByteDanceAudioApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class ByteDanceTestConfiguration {

	@Bean
	public ByteDanceChatApi byteDanceChatApi() {
		return new ByteDanceChatApi(getApiKey());
	}

	@Bean
	public ByteDanceAudioApi byteDanceAudioApi() {
		return new ByteDanceAudioApi(getApiKey());
	}

	private String getApiKey() {
		String apiKey = System.getenv("BYTE_DANCE_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name BYTE_DANCE_API_KEY");
		}
		return apiKey;
	}

	@Bean
	public ByteDanceChatModel byteDanceChatModel(ByteDanceChatApi api) {
        return new ByteDanceChatModel(api);
	}

	@Bean
	public ByteDanceAudioTranscriptionModel byteDanceTranscriptionModel(ByteDanceAudioApi api) {
        return new ByteDanceAudioTranscriptionModel(api);
	}

	@Bean
	public ByteDanceAudioSpeechModel byteDanceAudioSpeechModel(ByteDanceAudioApi api) {
        return new ByteDanceAudioSpeechModel(api);
	}
}
