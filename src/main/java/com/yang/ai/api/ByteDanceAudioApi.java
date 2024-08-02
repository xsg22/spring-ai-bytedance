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
package com.yang.ai.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yang.ai.api.common.ApiUtils;
import com.yang.ai.api.common.ByteDanceApiConstants;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Turn audio into text or text into audio. Based on
 * <a href="https://www.volcengine.com/docs/6561/79820">ByteDance Audio</a>
 *
 */
public class ByteDanceAudioApi {

    private final RestClient restClient;

    private final WebClient webClient;

    /**
     * Create a new audio api.
     *
     * @param speechApiToken ByteDance apiKey.
     */
    public ByteDanceAudioApi(String speechApiToken) {
        this(ByteDanceApiConstants.DEFAULT_SPEECH_BASE_URL, speechApiToken, RestClient.builder(), WebClient.builder(),
                RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
    }

    /**
     * Create an new chat completion api.
     *
     * @param baseUrl              api base URL.
     * @param speechApiToken       ByteDance apiKey.
     * @param restClientBuilder    RestClient builder.
     * @param responseErrorHandler Response error handler.
     */
    public ByteDanceAudioApi(String baseUrl, String speechApiToken, RestClient.Builder restClientBuilder,
                             ResponseErrorHandler responseErrorHandler) {

        this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(headers -> {
            headers.setBearerAuth(speechApiToken);
        }).defaultStatusHandler(responseErrorHandler).build();

        this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(headers -> {
            headers.setBearerAuth(speechApiToken);
        }).defaultHeaders(ApiUtils.getJsonContentHeaders(speechApiToken)).build();
    }

    /**
     * Create an new chat completion api.
     *
     * @param baseUrl              api base URL.
     * @param speechApiToken          ByteDance apiKey.
     * @param restClientBuilder    RestClient builder.
     * @param webClientBuilder     WebClient builder.
     * @param responseErrorHandler Response error handler.
     */
    public ByteDanceAudioApi(String baseUrl, String speechApiToken, RestClient.Builder restClientBuilder,
                             WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

        this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(headers -> {
            headers.setBearerAuth(speechApiToken);
        }).defaultStatusHandler(responseErrorHandler).build();

        this.webClient = webClientBuilder.baseUrl(baseUrl).defaultHeaders(headers -> {
            headers.setBearerAuth(speechApiToken);
        }).defaultHeaders(ApiUtils.getJsonContentHeaders(speechApiToken)).build();
    }

    /**
     * Request to generates audio from the input text. Reference:
     * <a href="https://www.volcengine.com/docs/6561/79823">Create
     * Speech</a>
     *
     * @param app     应用相关配置
     * @param user    用户相关配置
     * @param audio   音频相关配置
     * @param request 请求相关配置
     */
    @JsonInclude(Include.NON_NULL)
    public record SpeechRequest(
            // @formatter:off
		@JsonProperty("app") App app,
		@JsonProperty("user") User user,
		@JsonProperty("audio") Audio audio,
		@JsonProperty("request") Request request
    ) {
		// @formatter:on

        public record App(
                String appid,
                /**
                 * 目前未生效，填写默认值：access_token
                 */
                String token,
                String cluster
        ) {
            public App(String appid) {
                this(appid, "access_token", "volcano_tts");
            }
        }

        public record User(String uid) {
        }

        public record Audio(
                /**
                 * 音色类型
                 * <a href="https://www.volcengine.com/docs/6561/97465">音色列表</a>
                 */
                @NonNull
                String voice_type,
                /**
                 * 音频编码格式
                 */
                String encoding,
                /**
                 * opus格式时编码压缩比
                 */
                Integer compression_rate,
                /**
                 * 语速
                 */
                Float speed_ratio,
                /**
                 * 音量
                 */
                Float volume_ratio,
                /**
                 * 音高
                 */
                Float pitch_ratio,
                /**
                 * 情感/风格
                 */
                String emotion,
                /**
                 * 语言类型
                 * <a href="https://www.volcengine.com/docs/6561/97465">音色列表</a>
                 */
                String language
        ) {
            public Audio(String voice_type) {
                // emotion传null接口会报错，改成空字符串
                this(voice_type, AudioResponseFormat.MP3.value, 1, 1.0f, 1.0f, 1.0f, "", null);
            }
        }

        public record Request(
                /**
                 * 请求标识
                 */
                String reqid,
                /**
                 * 文本
                 */
                String text,
                /**
                 * 文本类型
                 * plain / ssml, 默认为plain
                 */
                String text_type,
                /**
                 * 句尾静音时长
                 * 单位为ms，默认为125
                 */
                Integer silence_duration,
                /**
                 * 操作
                 * query（非流式，http只能query） / submit（流式）
                 */
                String operation,
                /**
                 * 当with_frontend为1且frontend_type为unitTson的时候，返回音素级时间戳
                 */
                String with_timestamp,
                /**
                 * 复刻音色语速优化
                 */
                String split_sentence,

                /**
                 * 英文前端优化,
                 */
                String pure_english_opt
        ) {
            public Request(String reqid, String text, String operation) {
                this(reqid, text, "plain", 125, operation, "", "", "");
            }
        }

        /**
         * wav / pcm / ogg_opus / mp3，默认为 pcm
         * 注意：wav 不支持流式
         */
        public enum AudioResponseFormat {

            /**
             * not support stream
             */
            @JsonProperty("wav") WAV("wav"),
            /**
             * default
             */
            @JsonProperty("pcm") PCM("pcm"),
            @JsonProperty("ogg_opus") OGG_OPUS("ogg_opus"),
            @JsonProperty("mp3") MP3("mp3");

            public final String value;

            AudioResponseFormat(String value) {
                this.value = value;
            }

            public String getValue() {
                return this.value;
            }

        }

        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for the SpeechRequest.
         */
        public static class Builder {
            private App app;
            private User user;
            private Audio audio;
            private Request request;

            public Builder withApp(App app) {
                this.app = app;
                return this;
            }

            public Builder withUser(User user) {
                this.user = user;
                return this;
            }

            public Builder withAudio(Audio audio) {
                this.audio = audio;
                return this;
            }

            public Builder withRequest(Request request) {
                this.request = request;
                return this;
            }

            public SpeechRequest build() {
                Assert.notNull(app, "model must not be null");
                Assert.notNull(user, "user must not be null");
                Assert.notNull(audio, "audio must not be null");
                Assert.notNull(request, "request must not be null");

                return new SpeechRequest(this.app, this.user, this.audio, this.request);
            }

        }
    }

    public record SpeechApiResponse(
            /**
             * 请求 ID,与传入的参数中 reqid 一致
             */
            @JsonProperty("reqid") String reqId,
            /**
             * 错误码，参考下方说明
             */
            Integer code,
            /**
             * 错误信息
             */
            String message,
            /**
             * 负数表示合成完毕
             */
            Integer sequence,
            /**
             * 返回的音频数据，base64 编码
             */
            String data,
            /**
             * 额外信息父节点
             */
            Addition addition

    ) {
        public record Addition(
                /**
                 * 返回音频的长度，单位ms
                 */
                String duration,
                /**
                 * 包含字级别和音素级别的时间戳信息
                 */
                String frontend
        ) {
        }
    }

    /**
     * <a href="https://platform.openai.com/docs/models/whisper">Whisper</a> is a
     * general-purpose speech recognition model. It is trained on a large dataset of
     * diverse audio and is also a multi-task model that can perform multilingual speech
     * recognition as well as speech translation and language identification. The Whisper
     * v2-large model is currently available through our API with the whisper-1 model
     * name.
     */
    public enum WhisperModel {

        // @formatter:off
		@JsonProperty("whisper-1") WHISPER_1("whisper-1");
		// @formatter:on

        public final String value;

        WhisperModel(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

    }

    /**
     * Request to transcribe an audio file to text. Reference: <a href=
     * "https://platform.openai.com/docs/api-reference/audio/createTranscription">Create
     * Transcription</a>
     *
     * @param file            The audio file to transcribe. Must be a valid audio file type.
     * @param model           ID of the model to use. Only whisper-1 is currently available.
     * @param language        The language of the input audio. Supplying the input language in
     *                        ISO-639-1 format will improve accuracy and latency.
     * @param prompt          An optional text to guide the model's style or continue a previous
     *                        audio segment. The prompt should match the audio language.
     * @param responseFormat  The format of the transcript output, in one of these options:
     *                        json, text, srt, verbose_json, or vtt. Defaults to json.
     * @param temperature     The sampling temperature, between 0 and 1. Higher values like
     *                        0.8 will make the output more random, while lower values like 0.2 will make it more
     *                        focused and deterministic. If set to 0, the model will use log probability to
     *                        automatically increase the temperature until certain thresholds are hit.
     * @param granularityType The timestamp granularities to populate for this
     *                        transcription. response_format must be set verbose_json to use timestamp
     *                        granularities. Either or both of these options are supported: word, or segment.
     *                        Note: There is no additional latency for segment timestamps, but generating word
     *                        timestamps incurs additional latency.
     */
    @JsonInclude(Include.NON_NULL)
    public record TranscriptionRequest(
            // @formatter:off
		@JsonProperty("file") byte[] file,
		@JsonProperty("model") String model,
		@JsonProperty("language") String language,
		@JsonProperty("prompt") String prompt,
		@JsonProperty("response_format") TranscriptResponseFormat responseFormat,
		@JsonProperty("temperature") Float temperature,
		@JsonProperty("timestamp_granularities") GranularityType granularityType) {
		// @formatter:on

        public enum GranularityType {

            // @formatter:off
			@JsonProperty("word") WORD("word"),
			@JsonProperty("segment") SEGMENT("segment");
			// @formatter:on

            public final String value;

            GranularityType(String value) {
                this.value = value;
            }

            public String getValue() {
                return this.value;
            }

        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private byte[] file;

            private String model = WhisperModel.WHISPER_1.getValue();

            private String language;

            private String prompt;

            private TranscriptResponseFormat responseFormat = TranscriptResponseFormat.JSON;

            private Float temperature;

            private GranularityType granularityType;

            public Builder withFile(byte[] file) {
                this.file = file;
                return this;
            }

            public Builder withModel(String model) {
                this.model = model;
                return this;
            }

            public Builder withLanguage(String language) {
                this.language = language;
                return this;
            }

            public Builder withPrompt(String prompt) {
                this.prompt = prompt;
                return this;
            }

            public Builder withResponseFormat(TranscriptResponseFormat response_format) {
                this.responseFormat = response_format;
                return this;
            }

            public Builder withTemperature(Float temperature) {
                this.temperature = temperature;
                return this;
            }

            public Builder withGranularityType(GranularityType granularityType) {
                this.granularityType = granularityType;
                return this;
            }

            public TranscriptionRequest build() {
                Assert.notNull(this.file, "file must not be null");
                Assert.hasText(this.model, "model must not be empty");
                Assert.notNull(this.responseFormat, "response_format must not be null");

                return new TranscriptionRequest(this.file, this.model, this.language, this.prompt, this.responseFormat,
                        this.temperature, this.granularityType);
            }

        }
    }

    /**
     * The format of the transcript and translation outputs, in one of these options:
     * json, text, srt, verbose_json, or vtt. Defaults to json.
     */
    public enum TranscriptResponseFormat {

        // @formatter:off
		@JsonProperty("json") JSON("json", StructuredResponse.class),
		@JsonProperty("text") TEXT("text", String.class),
		@JsonProperty("srt") SRT("srt", String.class),
		@JsonProperty("verbose_json") VERBOSE_JSON("verbose_json", StructuredResponse.class),
		@JsonProperty("vtt") VTT("vtt", String.class);
		// @formatter:on

        public final String value;

        public final Class<?> responseType;

        public boolean isJsonType() {
            return this == JSON || this == VERBOSE_JSON;
        }

        TranscriptResponseFormat(String value, Class<?> responseType) {
            this.value = value;
            this.responseType = responseType;
        }

        public String getValue() {
            return this.value;
        }

        public Class<?> getResponseType() {
            return this.responseType;
        }

    }

    /**
     * Request to translate an audio file to English.
     *
     * @param file           The audio file object (not file name) to translate, in one of these
     *                       formats: flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav, or webm.
     * @param model          ID of the model to use. Only whisper-1 is currently available.
     * @param prompt         An optional text to guide the model's style or continue a previous
     *                       audio segment. The prompt should be in English.
     * @param responseFormat The format of the transcript output, in one of these options:
     *                       json, text, srt, verbose_json, or vtt.
     * @param temperature    The sampling temperature, between 0 and 1. Higher values like
     *                       0.8 will make the output more random, while lower values like 0.2 will make it more
     *                       focused and deterministic. If set to 0, the model will use log probability to
     *                       automatically increase the temperature until certain thresholds are hit.
     */
    @JsonInclude(Include.NON_NULL)
    public record TranslationRequest(
            // @formatter:off
		@JsonProperty("file") byte[] file,
		@JsonProperty("model") String model,
		@JsonProperty("prompt") String prompt,
		@JsonProperty("response_format") TranscriptResponseFormat responseFormat,
		@JsonProperty("temperature") Float temperature) {
		// @formatter:on

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private byte[] file;

            private String model = WhisperModel.WHISPER_1.getValue();

            private String prompt;

            private TranscriptResponseFormat responseFormat = TranscriptResponseFormat.JSON;

            private Float temperature;

            public Builder withFile(byte[] file) {
                this.file = file;
                return this;
            }

            public Builder withModel(String model) {
                this.model = model;
                return this;
            }

            public Builder withPrompt(String prompt) {
                this.prompt = prompt;
                return this;
            }

            public Builder withResponseFormat(TranscriptResponseFormat responseFormat) {
                this.responseFormat = responseFormat;
                return this;
            }

            public Builder withTemperature(Float temperature) {
                this.temperature = temperature;
                return this;
            }

            public TranslationRequest build() {
                Assert.notNull(file, "file must not be null");
                Assert.hasText(model, "model must not be empty");
                Assert.notNull(responseFormat, "response_format must not be null");

                return new TranslationRequest(this.file, this.model, this.prompt, this.responseFormat,
                        this.temperature);
            }

        }
    }

    /**
     * The <a href=
     * "https://platform.openai.com/docs/api-reference/audio/verbose-json-object">Transcription
     * Object </a> represents a verbose json transcription response returned by model,
     * based on the provided input.
     *
     * @param language The language of the transcribed text.
     * @param duration The duration of the audio in seconds.
     * @param text     The transcribed text.
     * @param words    The extracted words and their timestamps.
     * @param segments The segments of the transcribed text and their corresponding
     *                 details.
     */
    @JsonInclude(Include.NON_NULL)
    public record StructuredResponse(
            // @formatter:off
		@JsonProperty("language") String language,
		@JsonProperty("duration") Float duration,
		@JsonProperty("text") String text,
		@JsonProperty("words") List<Word> words,
		@JsonProperty("segments") List<Segment> segments) {
		// @formatter:on

        /**
         * Extracted word and it corresponding timestamps.
         *
         * @param word  The text content of the word.
         * @param start The start time of the word in seconds.
         * @param end   The end time of the word in seconds.
         */
        @JsonInclude(Include.NON_NULL)
        public record Word(
                // @formatter:off
			@JsonProperty("word") String word,
			@JsonProperty("start") Float start,
			@JsonProperty("end") Float end) {
			// @formatter:on
        }

        /**
         * Segment of the transcribed text and its corresponding details.
         *
         * @param id               Unique identifier of the segment.
         * @param seek             Seek offset of the segment.
         * @param start            Start time of the segment in seconds.
         * @param end              End time of the segment in seconds.
         * @param text             The text content of the segment.
         * @param tokens           Array of token IDs for the text content.
         * @param temperature      Temperature parameter used for generating the segment.
         * @param avgLogprob       Average logprob of the segment. If the value is lower than
         *                         -1, consider the logprobs failed.
         * @param compressionRatio Compression ratio of the segment. If the value is
         *                         greater than 2.4, consider the compression failed.
         * @param noSpeechProb     Probability of no speech in the segment. If the value is
         *                         higher than 1.0 and the avg_logprob is below -1, consider this segment silent.
         */
        @JsonInclude(Include.NON_NULL)
        public record Segment(
                // @formatter:off
			@JsonProperty("id") Integer id,
			@JsonProperty("seek") Integer seek,
			@JsonProperty("start") Float start,
			@JsonProperty("end") Float end,
			@JsonProperty("text") String text,
			@JsonProperty("tokens") List<Integer> tokens,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("avg_logprob") Float avgLogprob,
			@JsonProperty("compression_ratio") Float compressionRatio,
			@JsonProperty("no_speech_prob") Float noSpeechProb) {
			// @formatter:on
        }
    }

    /**
     * Request to generates audio from the input text.
     *
     * @param requestBody The request body.
     * @return Response entity containing the audio binary.
     */
    public ResponseEntity<SpeechApiResponse> createSpeech(SpeechRequest requestBody) {
        return this.restClient.post().uri("/api/v1/tts").body(requestBody).retrieve().toEntity(SpeechApiResponse.class);
    }

    /**
     * Streams audio generated from the input text.
     * <p>
     * This method sends a POST request to the ByteDance API to generate audio from the
     * provided text. The audio is streamed back as a Flux of ResponseEntity objects, each
     * containing a byte array of the audio data.
     *
     * @param requestBody The request body containing the details for the audio
     *                    generation, such as the input text, model, voice, and response format.
     * @return A Flux of ResponseEntity objects, each containing a byte array of the audio
     * data.
     */
    public Flux<ResponseEntity<byte[]>> stream(SpeechRequest requestBody) {

        return webClient.post()
                .uri("/api/v1/tts/ws_binary")
                .body(Mono.just(requestBody), SpeechRequest.class)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .exchangeToFlux(clientResponse -> {
                    HttpHeaders headers = clientResponse.headers().asHttpHeaders();
                    return clientResponse.bodyToFlux(byte[].class)
                            .map(bytes -> ResponseEntity.ok().headers(headers).body(bytes));
                });
    }

    /**
     * Transcribes audio into the input language.
     *
     * @param requestBody The request body.
     * @return Response entity containing the transcribed text in either json or text
     * format.
     */
    public ResponseEntity<?> createTranscription(TranscriptionRequest requestBody) {
        return createTranscription(requestBody, requestBody.responseFormat().getResponseType());
    }

    /**
     * Transcribes audio into the input language. The response type is specified by the
     * responseType parameter.
     *
     * @param <T>          The response type.
     * @param requestBody  The request body.
     * @param responseType The response type class.
     * @return Response entity containing the transcribed text in the responseType format.
     */
    public <T> ResponseEntity<T> createTranscription(TranscriptionRequest requestBody, Class<T> responseType) {

        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("file", new ByteArrayResource(requestBody.file()) {
            @Override
            public String getFilename() {
                return "audio.webm";
            }
        });
        multipartBody.add("model", requestBody.model());
        multipartBody.add("language", requestBody.language());
        multipartBody.add("prompt", requestBody.prompt());
        multipartBody.add("response_format", requestBody.responseFormat().getValue());
        multipartBody.add("temperature", requestBody.temperature());
        if (requestBody.granularityType() != null) {
            Assert.isTrue(requestBody.responseFormat() == TranscriptResponseFormat.VERBOSE_JSON,
                    "response_format must be set to verbose_json to use timestamp granularities.");
            multipartBody.add("timestamp_granularities[]", requestBody.granularityType().getValue());
        }

        return this.restClient.post()
                .uri("/v1/audio/transcriptions")
                .body(multipartBody)
                .retrieve()
                .toEntity(responseType);
    }

    /**
     * Translates audio into English.
     *
     * @param requestBody The request body.
     * @return Response entity containing the transcribed text in either json or text
     * format.
     */
    public ResponseEntity<?> createTranslation(TranslationRequest requestBody) {
        return createTranslation(requestBody, requestBody.responseFormat().getResponseType());
    }

    /**
     * Translates audio into English. The response type is specified by the responseType
     * parameter.
     *
     * @param <T>          The response type.
     * @param requestBody  The request body.
     * @param responseType The response type class.
     * @return Response entity containing the transcribed text in the responseType format.
     */
    public <T> ResponseEntity<T> createTranslation(TranslationRequest requestBody, Class<T> responseType) {

        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("file", new ByteArrayResource(requestBody.file()) {
            @Override
            public String getFilename() {
                return "audio.webm";
            }
        });
        multipartBody.add("model", requestBody.model());
        multipartBody.add("prompt", requestBody.prompt());
        multipartBody.add("response_format", requestBody.responseFormat().getValue());
        multipartBody.add("temperature", requestBody.temperature());

        return this.restClient.post()
                .uri("/v1/audio/translations")
                .body(multipartBody)
                .retrieve()
                .toEntity(responseType);
    }

}
