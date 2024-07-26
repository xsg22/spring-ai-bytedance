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
package com.yang.ai.audio.api;

import com.yang.ai.api.ByteDanceAudioApi;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.util.FileCopyUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "BYTE_DANCE_SPEECH_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BYTE_DANCE_SPEECH_APP_ID", matches = ".+")
public class ByteDanceAudioApiIT {

    ByteDanceAudioApi audioApi = new ByteDanceAudioApi(System.getenv("BYTE_DANCE_SPEECH_API_KEY"));

    @SuppressWarnings("null")
    @Test
    void speech() throws IOException {

        ByteDanceAudioApi.SpeechApiResponse speechApiResponse = audioApi
                .createSpeech(ByteDanceAudioApi.SpeechRequest.builder()
                        .withApp(new ByteDanceAudioApi.SpeechRequest.App(System.getenv("BYTE_DANCE_SPEECH_APP_ID")))
                        .withUser(new ByteDanceAudioApi.SpeechRequest.User("uid"))
                        .withAudio(new ByteDanceAudioApi.SpeechRequest.Audio("BV700_streaming"))
                        .withRequest(new ByteDanceAudioApi.SpeechRequest.Request(UUID.randomUUID().toString(), "hi，我叫小明。你呢，叫什么名字？", "query"))
                        .build())
                .getBody();
        byte[] speech = Base64.getDecoder().decode(speechApiResponse.data());

        assertThat(speech).isNotEmpty();

        // 使用 ByteArrayInputStream 读取二进制数据
        ByteArrayInputStream audioStream = new ByteArrayInputStream(speech);

        try {
            // 创建 Player
            Player player = new Player(audioStream);

            // 播放音频
            player.play();
        } catch (JavaLayerException e) {
            throw new RuntimeException(e);
        }
    }

}
