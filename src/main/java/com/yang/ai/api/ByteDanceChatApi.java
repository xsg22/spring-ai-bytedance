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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yang.ai.api.common.ApiUtils;
import com.yang.ai.api.common.ByteDanceApiConstants;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;


/**
 * Single class implementation of the ByteDance Chat Completion API: https://www.volcengine.com/docs/82379/1263482#chat-completions and
 *
 * @author yang
 */
public class ByteDanceChatApi {

    private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

    private final RestClient restClient;

    private WebClient webClient;

    /**
     * 创建一个新的聊天完成api，默认URL设置为 https://ark.cn-beijing.volces.com
     *
     * @param apiKey ByteDance apiKey.
     */
    public ByteDanceChatApi(String apiKey) {
        this(ByteDanceApiConstants.DEFAULT_CHAT_BASE_URL, apiKey);
    }

    /**
     * 创建一个新的聊天完成api。
     *
     * @param baseUrl api base URL.
     * @param apiKey  ByteDance apiKey.
     */
    public ByteDanceChatApi(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, RestClient.builder(),  WebClient.builder());
    }

    /**
     * 创建一个新的聊天完成api。
     *
     * @param baseUrl           api base URL.
     * @param apiKey            ByteDance apiKey.
     * @param restClientBuilder RestClient builder.
     */
    public ByteDanceChatApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        this(baseUrl, apiKey, restClientBuilder, webClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
    }

    /**
     * 创建一个新的聊天完成api。
     *
     * @param baseUrl              api base URL.
     * @param apiKey               ByteDance apiKey.
     * @param restClientBuilder    RestClient builder.
     * @param responseErrorHandler Response error handler.
     */
    public ByteDanceChatApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeaders(ApiUtils.getJsonContentHeaders(apiKey))
                .defaultStatusHandler(responseErrorHandler)
                .build();

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeaders(ApiUtils.getJsonContentHeaders(apiKey))
                .build();
    }

    /**
     * 刷新apiKey
     *
     * @param newApiKey
     */
    public void refreshApiKey(String newApiKey) {
        this.webClient = webClient.mutate()
                .defaultHeaders(ApiUtils.getJsonContentHeaders(newApiKey))
                .build();
    }

    /**
     * 为给定的聊天会话创建一个模型响应。
     *
     * @param messages         本次对话的消息列表，包含用户输入的最后一条消息。
     * @param model            以 endpoint_id 索引对应的模型接入点。
     * @param frequencyPenalty -2.0 到 2.0 之间的数字。如果为正，会根据新 token 在文本中的出现频率对其进行惩罚，从而降低模型重复相同内容的可能性。
     * @param logitBias        修改指定 token 在模型输出内容中出现的概率。 接受一个 map，该对象将 token(token id 使用 tokenization 接口获取)映射到从-100到100的关联偏差值。
     *                         每个模型的效果有所不同，但-1和1之间的值会减少或增加选择的可能性；-100或100应该导致禁止或排他选择相关的 token。
     * @param logprobs         是否返回输出 tokens 的 logprobs。如果为 true，则返回 message (content) 中每个输出 token 的 logprobs。
     * @param topLogprobs      0 到 20 之间的整数，指定每个 token 位置最有可能返回的token数量，每个token 都有关联的对数概率。 如果使用此参数，则 logprobs 必须设置为 true
     * @param maxTokens        模型最大输出 token 数。
     *                         输入 token 和输出 token 的总长度还受模型的上下文长度限制。
     * @param stop             用于指定模型在生成响应时应停止的词语。当模型生成的响应中包含这些词汇时，生成过程将停止。
     * @param stream           是否流式返回。如果为 true，则按 SSE 协议返回数据。
     * @param streamOptions    如果设置，则在data: [DONE]消息之前会返回一个额外的块。此块上的 usage 字段显示了整个请求的 token 用量，
     *                         其 choices 字段是一个空数组。所有其他块也将包含usage字段，但值为 null。
     * @param temperature      采样温度在0到2之间。较高的值(如0.8)将使输出更加随机，而较低的值(如0.2)将使输出更加集中和确定。
     *                         通常建议修改 temperature 或 top_p，但不建议两者都修改。
     * @param topP             temperature 抽样的另一种选择，称为核抽样，其中模型考虑具有 top_p 概率质量的 token。所以 0.1 意味着只考虑包含前 10% 概率质量的标记。
     *                         一般建议修改 top_p 或 temperature，但不建议两者都修改
     */
    @JsonInclude(Include.NON_NULL)
    public record ChatCompletionRequest(
            @JsonProperty("messages") @NonNull List<ChatCompletionMessage> messages,
            @JsonProperty("model") @NonNull String model,
            @JsonProperty("frequency_penalty") Float frequencyPenalty,
            @JsonProperty("logit_bias") Map<String, Integer> logitBias,
            @JsonProperty("logprobs") Boolean logprobs,
            @JsonProperty("top_logprobs") Integer topLogprobs,
            @JsonProperty("max_tokens") Integer maxTokens,
            @JsonProperty("stop") List<String> stop,
            @JsonProperty("stream") Boolean stream,
            @JsonProperty("stream_options") ChatCompletionStreamOption streamOptions,
            @JsonProperty("temperature") Float temperature,
            @JsonProperty("top_p") Float topP
    ) {
        public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
            this(messages, null, null, null, null, null, null, null, stream, null, null, null);
        }

        public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature, boolean stream) {
            this(messages, model, null, null, null, null, null, null, stream, null, temperature, null);
        }
    }

    /**
     * 流式输出的选项。
     *
     * @param includeUsage 如果设置，则在data: [DONE]消息之前会返回一个额外的块。此块上的 usage 字段显示了整个请求的 token 用量，其 choices 字段是一个空数组。所有其他块也将包含usage字段，但值为 null。
     */
    public record ChatCompletionStreamOption(
            @JsonProperty("include_usage") Boolean includeUsage
    ) {
    }

    /**
     * 模型输出的消息内容
     *
     * @param rawContent 消息的内容。 可以是 {@link MediaContent} 或 {@link String}.
     *                   响应消息内容始终是 {@link String}.
     * @param role       消息作者的角色。 可能是 {@link Role} 类型的其中之一.
     */
    @JsonInclude(Include.NON_NULL)
    public record ChatCompletionMessage(
            @JsonProperty("content") Object rawContent,
            @JsonProperty("role") Role role) {

        /**
         * 以字符串形式获取消息内容。
         */
        public String content() {
            if (this.rawContent == null) {
                return null;
            }
            if (this.rawContent instanceof String text) {
                return text;
            }
            throw new IllegalStateException("The content is not a string!");
        }

        /**
         * 消息作者的角色。
         */
        public enum Role {
            /**
             * System message.
             */
            @JsonProperty("system") SYSTEM,
            /**
             * User message.
             */
            @JsonProperty("user") USER,
            /**
             * Assistant message.
             */
            @JsonProperty("assistant") ASSISTANT
        }

        /**
         * 具有已定义类型的内容部件数组。每个MediaContent可以是“text”或“image_url”类型。不是两者都有。
         *
         * @param type     内容类型，每个都可以是text或image_url类型。
         * @param text     消息里文本内容。
         * @param imageUrl 消息里图像内容。你可以通过多个通过添加多个image_url内容部分生成图像。仅图像输入在使用gpt-4可视化审查模型时得到支持。
         */
        @JsonInclude(Include.NON_NULL)
        public record MediaContent(
                @JsonProperty("type") String type,
                @JsonProperty("text") String text,
                @JsonProperty("image_url") ImageUrl imageUrl) {

            /**
             * @param url    图像的URL或base64编码的图像数据。
             *               base64编码的图像数据必须具有以下格式的特殊前缀：
             *               "data:{mimetype};base64,{base64-encoded-image-data}".
             * @param detail 指定图像的详细程度。
             */
            @JsonInclude(Include.NON_NULL)
            public record ImageUrl(
                    @JsonProperty("url") String url,
                    @JsonProperty("detail") String detail) {

                public ImageUrl(String url) {
                    this(url, null);
                }
            }

            /**
             * Shortcut constructor for a text content.
             *
             * @param text The text content of the message.
             */
            public MediaContent(String text) {
                this("text", text, null);
            }

            /**
             * 图像内容的快捷构造方式。
             *
             * @param imageUrl 消息的图像内容。
             */
            public MediaContent(ImageUrl imageUrl) {
                this("image_url", null, imageUrl);
            }
        }
    }

    public static String getTextContent(List<ChatCompletionMessage.MediaContent> content) {
        return content.stream()
                .filter(c -> "text".equals(c.type()))
                .map(ChatCompletionMessage.MediaContent::text)
                .reduce("", (a, b) -> a + b);
    }

    /**
     * 模型停止生成token的原因。
     */
    @JsonDeserialize(using = ChatCompletionFinishReasonEnumDeserializer.class)
    public enum ChatCompletionFinishReason {
        /**
         * 表示正常生成结束
         */
        @JsonProperty("stop") STOP,
        /**
         * 表示已经到了生成的最大 token 数量
         */
        @JsonProperty("length") LENGTH,
        /**
         * 表示命中审核提前终止
         */
        @JsonProperty("content_filter") CONTENT_FILTER
    }

    public static class ChatCompletionFinishReasonEnumDeserializer extends JsonDeserializer<ChatCompletionFinishReason> {
        @Override
        public ChatCompletionFinishReason deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            String value = node.textValue();

            if (value == null || value.isEmpty()) {
                return null;
            }

            for (ChatCompletionFinishReason enumValue : ChatCompletionFinishReason.values()) {
                if (enumValue.name().equalsIgnoreCase(value)) {
                    return enumValue;
                }
            }

            throw new IllegalArgumentException("Unknown enum value: " + value);
        }
    }

    /**
     * chat completion接口的响应内容
     *
     * @param id      A 一次 chat completion 接口调用的唯一标识。
     * @param choices 本次 chat 结果列表。长度固定为 1。
     * @param created 本次对话生成时间戳（秒）。
     * @param model   实际使用的模型名称和版本。
     * @param object  固定为 chat.completion（非流式）和 chat.completion.chunk（流式）。
     * @param usage   本次请求的 tokens 用量。
     */
    @JsonInclude(Include.NON_NULL)
    public record ChatCompletion(
            @JsonProperty("id") String id,
            @JsonProperty("choices") List<Choice> choices,
            @JsonProperty("created") Long created,
            @JsonProperty("model") String model,
            @JsonProperty("object") String object,
            @JsonProperty("usage") Usage usage) {

        /**
         * Chat completion choice.
         *
         * @param finishReason 模型生成结束原因，stop表示正常生成结束，length 表示已经到了生成的最大 token 数量，content_filter 表示命中审核提前终止。
         * @param index        该元素在 choices 列表的索引。
         * @param message      模型输出的消息内容
         * @param logprobs     该输出结果的概率信息
         */
        @JsonInclude(Include.NON_NULL)
        public record Choice(
                @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
                @JsonProperty("index") Integer index,
                @JsonProperty("message") ChatCompletionMessage message,
                @JsonProperty("logprobs") LogProbs logprobs) {

        }
    }

    /**
     * 该输出结果的概率信息
     *
     * @param content A list of message content tokens with log probability information.
     */
    @JsonInclude(Include.NON_NULL)
    public record LogProbs(
            @JsonProperty("content") List<Content> content) {

        /**
         * 表示message列表中每个元素content token的概率信息
         *
         * @param token       对应 token；
         * @param logprob     token的概率；
         * @param probBytes   表示 token 的 UTF-8 字节表示的整数列表。在字符由多个 token 表示，并且它们的字节表示必须组合以生成正确的文本表示的情况下(表情符号或特殊字符)非常有用。如果 token 没有 byte 表示，则可以为空。
         * @param topLogprobs 最可能的token列表及其在此 token位置的对数概率：
         */
        @JsonInclude(Include.NON_NULL)
        public record Content(
                @JsonProperty("token") String token,
                @JsonProperty("logprob") Float logprob,
                @JsonProperty("bytes") List<Integer> probBytes,
                @JsonProperty("top_logprobs") List<TopLogProbs> topLogprobs) {

            /**
             * 最可能的token列表及其在此 token位置的对数概率：
             *
             * @param token     对应token
             * @param logprob   token的概率
             * @param probBytes 表示 token 的 UTF-8 字节表示的整数列表。在字符由多个 token 表示，并且它们的字节表示必须组合以生成正确的文本表示的情况下(表情符号或特殊字符)非常有用。如果 token 没有 byte 表示，则可以为空。
             */
            @JsonInclude(Include.NON_NULL)
            public record TopLogProbs(
                    @JsonProperty("token") String token,
                    @JsonProperty("logprob") Float logprob,
                    @JsonProperty("bytes") List<Integer> probBytes) {
            }
        }
    }

    /**
     * completion request的token使用情况统计信息。
     *
     * @param completionTokens 模型生成的 token 数量。
     * @param promptTokens     本次请求中输入的 token 数量。
     * @param totalTokens      总的 token 数量。
     */
    @JsonInclude(Include.NON_NULL)
    public record Usage(
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {

    }

    /**
     * 表示模型根据提供的输入返回的chat completion响应的流式块。
     *
     * @param id      一次 chat completion 接口调用的唯一标识，一次流式调用所有的 chunk 有相同的 id。
     * @param choices 结果列表。长度固定为 1。如果设置了stream_options: {"include_usage": true}，则最后一个 chunk 的 choices 也为空列表。
     * @param created The Unix timestamp (in seconds) of when the chat completion was created. Each chunk has the same
     *                timestamp.
     * @param model   The model used for the chat completion.
     * @param usage   本次请求的 tokens 用量。
     * @param object  The object type, which is always 'chat.completion.chunk'.
     */
    @JsonInclude(Include.NON_NULL)
    public record ChatCompletionChunk(
            @JsonProperty("id") String id,
            @JsonProperty("choices") List<ChunkChoice> choices,
            @JsonProperty("created") Long created,
            @JsonProperty("model") String model,
            @JsonProperty("usage") Usage usage,
            @JsonProperty("object") String object) {

        /**
         * Chat completion 选项
         *
         * @param finishReason 模型生成结束原因，stop表示正常生成结束，length 表示已经到了生成的最大 token 数量，content_filter 表示命中审核提前终止。
         * @param index        该元素在 choices 列表的索引。
         * @param delta        A chat completion delta generated by streamed model responses.
         * @param logprobs     该输出结果的概率信息，其只有一个 content 字段，类型为 array，表示message列表中每个元素content token的概率信息，content 元素子字段说明如下：
         *                     token [string]: 对应 token；
         *                     logprob [number]：token的概率；
         *                     bytes [array]：表示 token 的 UTF-8 字节表示的整数列表。在字符由多个 token 表示，并且它们的字节表示必须组合以生成正确的文本表示的情况下(表情符号或特殊字符)非常有用。如果 token 没有 byte 表示，则可以为空。
         *                     top_logprobs [array]：最可能的token列表及其在此 token位置的对数概率：
         *                     token [string]: 对应token；
         *                     logprob [number]：token的概率；
         *                     bytes [array]：表示 token 的 UTF-8 字节表示的整数列表。在字符由多个 token 表示，并且它们的字节表示必须组合以生成正确的文本表示的情况下(表情符号或特殊字符)非常有用。如果 token 没有 byte 表示，则可以为空。
         */
        @JsonInclude(Include.NON_NULL)
        public record ChunkChoice(
                @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
                @JsonProperty("index") Integer index,
                @JsonProperty("delta") ChatCompletionMessage delta,
                @JsonProperty("logprobs") LogProbs logprobs) {
        }
    }

    /**
     * 获取完整输出
     *
     * @param chatRequest 入参
     * @return 以 {@link ChatCompletion}为主体、HTTP状态代码和标头的实体响应。
     */
    public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {

        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(!chatRequest.stream(), "Request must set the steam property to false.");

        return this.restClient.post()
                .uri("/api/v3/chat/completions")
                .body(chatRequest)
                .retrieve()
                .toEntity(ChatCompletion.class);
    }

    /**
     * 获取流式输出
     *
     * @param chatRequest 请求入参。必须将流属性设置为true。
     * @return 从聊天接口返回一个{@link Flux}流。
     */
    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {

        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(chatRequest.stream(), "Request must set the steam property to true.");

        AtomicBoolean isInsideTool = new AtomicBoolean(false);

        return this.webClient.post()
                .uri("/api/v3/chat/completions")
                .body(Mono.just(chatRequest), ChatCompletionRequest.class)
                .retrieve()
                .bodyToFlux(String.class)
                .map(content -> {
                    // 返回的content-type不是text/event-stream，导致当作普通处理，需要手动解析
                    if (content.startsWith("data:")) {
                        int length = content.length();
                        if (length > 5) {
                            int index = (content.charAt(5) != ' ' ? 5 : 6);
                            if (length > index) {
                                return content.substring(index);
                            }
                        }
                    }
                    return content;
                })
                .filter(StringUtils::hasText)
                // cancels the flux stream after the "[DONE]" is received.
                .takeUntil(SSE_DONE_PREDICATE)
                // filters out the "[DONE]" message.
                .filter(SSE_DONE_PREDICATE.negate())
                .map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class));
    }
}
