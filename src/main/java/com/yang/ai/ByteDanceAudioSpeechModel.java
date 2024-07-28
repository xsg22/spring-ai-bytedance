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
import com.yang.ai.api.common.ByteDanceApiException;
import com.yang.ai.audio.speech.*;
import com.yang.ai.metadata.audio.ByteDanceAudioSpeechResponseMetadata;
import com.yang.ai.metadata.support.ByteDanceResponseHeaderExtractor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * ByteDance audio speech client implementation for backed by {@link ByteDanceAudioApi}.
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
     *
     * @see ByteDanceAudioSpeechOptions
     */
    private static final Float SPEED = 1.0f;

    /**
     * The retry template used to retry the ByteDance Audio API calls.
     */
    public final RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(10)
            .retryOn(ByteDanceApiException.class)
            .exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
            .build();

    /**
     * Low-level access to the ByteDance Audio API.
     */
    private final ByteDanceAudioApi audioApi;

    /**
     * Initializes a new instance of the ByteDanceAudioSpeechModel class with the provided
     * ByteDanceAudioApi and options.
     *
     * @param audioApi The ByteDanceAudioApi to use for speech synthesis.
     *                 options.
     */
    public ByteDanceAudioSpeechModel(ByteDanceAudioApi audioApi, String appId, String voiceType) {
        this(audioApi, ByteDanceAudioSpeechOptions.builder()
                .withApp(new ByteDanceAudioApi.SpeechRequest.App(appId))
                .withUser(new ByteDanceAudioApi.SpeechRequest.User("uid"))
                .withAudio(new ByteDanceAudioApi.SpeechRequest.Audio(voiceType))
                .withRequest(new ByteDanceAudioApi.SpeechRequest.Request(null, null, "query"))
                .build());
    }

    /**
     * Initializes a new instance of the ByteDanceAudioSpeechModel class with the provided
     * ByteDanceAudioApi and options.
     *
     * @param audioApi The ByteDanceAudioApi to use for speech synthesis.
     * @param options  The ByteDanceAudioSpeechOptions containing the speech synthesis
     *                 options.
     */
    public ByteDanceAudioSpeechModel(ByteDanceAudioApi audioApi, ByteDanceAudioSpeechOptions options) {
        Assert.notNull(audioApi, "ByteDanceAudioApi must not be null");
        Assert.notNull(options, "ByteDanceSpeechOptions must not be null");
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

            ResponseEntity<ByteDanceAudioApi.SpeechApiResponse> speechEntity = this.audioApi.createSpeech(speechRequest);
            ByteDanceAudioApi.SpeechApiResponse speechEntityBody = speechEntity.getBody();

            if (speechEntityBody == null) {
                logger.warn("No speechEntityBody response returned for speechRequest: {}", speechRequest);
                return new SpeechResponse(new Speech(new byte[0]));
            }

            byte[] speech = Base64.getDecoder().decode(speechEntityBody.data());
            return new SpeechResponse(new Speech(speech), new ByteDanceAudioSpeechResponseMetadata(null));
        });
    }

    /**
     * Streams the audio response for the given speech prompt.
     *
     * @param prompt The speech prompt containing the text and options for speech
     *               synthesis.
     * @return A Flux of SpeechResponse objects containing the streamed audio and
     * metadata.
     */
    @Override
    public Flux<SpeechResponse> stream(SpeechPrompt prompt) {
        throw new UnsupportedOperationException();
//        return this.audioApi.stream(this.createRequestBody(prompt))
//                .map(entity -> new SpeechResponse(new Speech(entity.getBody()), new ByteDanceAudioSpeechResponseMetadata(
//                        ByteDanceResponseHeaderExtractor.extractAiResponseHeaders(entity))));
    }

    private ByteDanceAudioApi.SpeechRequest createRequestBody(SpeechPrompt request) {
        ByteDanceAudioSpeechOptions options = this.defaultOptions;

        if (request.getOptions() != null) {
            if (request.getOptions() instanceof ByteDanceAudioSpeechOptions runtimeOptions) {
                options = this.merge(runtimeOptions, options);
            } else {
                throw new IllegalArgumentException("Prompt options are not of type SpeechOptions: "
                        + request.getOptions().getClass().getSimpleName());
            }
        }

        ByteDanceAudioApi.SpeechRequest.Request requestParam = options.getRequest();
        if (StringUtils.isBlank(options.getRequest().text()) && StringUtils.isBlank(requestParam.reqid())) {
            String input = request.getInstructions().getText();
            String reqid = UUID.randomUUID().toString().replaceAll("-", "");
            requestParam = new ByteDanceAudioApi.SpeechRequest.Request(reqid, input, requestParam.operation());
        } else if (StringUtils.isBlank(options.getRequest().text())) {
            String input = request.getInstructions().getText();
            requestParam = new ByteDanceAudioApi.SpeechRequest.Request(requestParam.reqid(), input, requestParam.operation());
        } else if (StringUtils.isBlank(requestParam.reqid())) {
            String reqid = UUID.randomUUID().toString().replaceAll("-", "");
            requestParam = new ByteDanceAudioApi.SpeechRequest.Request(reqid, requestParam.text(), requestParam.operation());
        }

        ByteDanceAudioApi.SpeechRequest.Builder requestBuilder = ByteDanceAudioApi.SpeechRequest.builder()
                .withApp(options.getApp())
                .withUser(options.getUser())
                .withAudio(options.getAudio())
                .withRequest(requestParam);

        return requestBuilder.build();
    }

    private ByteDanceAudioSpeechOptions merge(ByteDanceAudioSpeechOptions source, ByteDanceAudioSpeechOptions target) {
        ByteDanceAudioSpeechOptions.Builder mergedBuilder = ByteDanceAudioSpeechOptions.builder();

        mergedBuilder.withApp(source.getApp() != null ? source.getApp() : target.getApp());
        mergedBuilder.withUser(source.getUser() != null ? source.getUser() : target.getUser());
        mergedBuilder.withAudio(source.getAudio() != null ? source.getAudio() : target.getAudio());
        mergedBuilder.withRequest(source.getRequest() != null ? source.getRequest() : target.getRequest());

        return mergedBuilder.build();
    }
}
