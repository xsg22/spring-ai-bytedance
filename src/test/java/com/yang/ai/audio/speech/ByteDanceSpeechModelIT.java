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

package com.yang.ai.audio.speech;

import com.yang.ai.ByteDanceTestConfiguration;
import com.yang.ai.testutils.AbstractIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ByteDanceTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "BYTE_DANCE_SPEECH_APP_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BYTE_DANCE_API_KEY", matches = ".+")
class ByteDanceSpeechModelIT extends AbstractIT {

//	@Test
//	void shouldSuccessfullyStreamAudioBytesForEmptyMessage() {
//		Flux<byte[]> response = speechModel.stream("Today is a wonderful day to build something people love!");
//		assertThat(response).isNotNull();
//		assertThat(response.collectList().block()).isNotNull();
//		System.out.println(response.collectList().block());
//	}

	@Test
	void shouldProduceAudioBytesDirectlyFromMessage() {
		byte[] audioBytes = speechModel.call("Today is a wonderful day to build something people love!");
		assertThat(audioBytes).hasSizeGreaterThan(0);

	}

	@Test
	void shouldGenerateNonEmptyMp3AudioFromSpeechPrompt() {
		SpeechPrompt speechPrompt = new SpeechPrompt("Today is a wonderful day to build something people love!");
		SpeechResponse response = speechModel.call(speechPrompt);
		byte[] audioBytes = response.getResult().getOutput();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(audioBytes).hasSizeGreaterThan(0);

	}

//	@Test
//	void shouldStreamNonEmptyResponsesForValidSpeechPrompts() {
//
//		SpeechPrompt speechPrompt = new SpeechPrompt("Today is a wonderful day to build something people love!");
//		Flux<SpeechResponse> responseFlux = speechModel.stream(speechPrompt);
//		assertThat(responseFlux).isNotNull();
//		List<SpeechResponse> responses = responseFlux.collectList().block();
//		assertThat(responses).isNotNull();
//		responses.forEach(response -> {
//			System.out.println("Audio data chunk size: " + response.getResult().getOutput().length);
//			assertThat(response.getResult().getOutput()).isNotEmpty();
//		});
//	}

}