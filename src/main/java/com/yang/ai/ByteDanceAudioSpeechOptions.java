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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yang.ai.api.ByteDanceAudioApi;
import com.yang.ai.api.ByteDanceAudioApi.SpeechRequest.*;
import org.springframework.ai.model.ModelOptions;

import java.util.Objects;


/**
 * Options for ByteDance text to audio - speech synthesis.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ByteDanceAudioSpeechOptions implements ModelOptions {

	/**
	 * ID of the model to use for generating the audio. One of the available TTS models:
	 * tts-1 or tts-1-hd.
	 */
	@JsonProperty("app")
	private App app;

	/**
	 * The input text to synthesize. Must be at most 4096 tokens long.
	 */
	@JsonProperty("user")
	private User user;

	/**
	 * The voice to use for synthesis. One of the available voices for the chosen model:
	 * 'alloy', 'echo', 'fable', 'onyx', 'nova', and 'shimmer'.
	 */
	@JsonProperty("audio")
	private Audio audio;

	/**
	 * The format of the audio output. Supported formats are mp3, opus, aac, and flac.
	 * Defaults to mp3.
	 */
	@JsonProperty("response_format")
	private Request request;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final ByteDanceAudioSpeechOptions options = new ByteDanceAudioSpeechOptions();

		public Builder withApp(App app) {
			options.app = app;
			return this;
		}

		public Builder withUser(User user) {
			options.user = user;
			return this;
		}

		public Builder withAudio(Audio audio) {
			options.audio = audio;
			return this;
		}

		public Builder withRequest(Request request) {
			options.request = request;
			return this;
		}

		public ByteDanceAudioSpeechOptions build() {
			return options;
		}
	}

	public App getApp() {
		return app;
	}

	public void setApp(App app) {
		this.app = app;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public Audio getAudio() {
		return audio;
	}

	public void setAudio(Audio audio) {
		this.audio = audio;
	}

	public Request getRequest() {
		return request;
	}

	public void setRequest(Request request) {
		this.request = request;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ByteDanceAudioSpeechOptions that = (ByteDanceAudioSpeechOptions) o;
		return Objects.equals(app, that.app) && Objects.equals(user, that.user) && Objects.equals(audio, that.audio) && Objects.equals(request, that.request);
	}

	@Override
	public int hashCode() {
		return Objects.hash(app, user, audio, request);
	}

	@Override
	public String toString() {
		return "ByteDanceAudioSpeechOptions{" +
				"app=" + app +
				", user=" + user +
				", audio=" + audio +
				", request=" + request +
				'}';
	}
}
