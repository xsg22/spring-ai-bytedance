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
import com.yang.ai.metadata.ByteDanceChatResponseMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.AbstractFunctionCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ChatModel} and {@link StreamingChatModel} implementation for {@literal ByteDance}
 * backed by {@link ByteDanceChatApi}.
 *
 * @author xsg
 * @see ChatModel
 * @see StreamingChatModel
 * @see ByteDanceChatApi
 */
public class ByteDanceChatModel extends
        AbstractFunctionCallSupport<ByteDanceChatApi.ChatCompletionMessage, ByteDanceChatApi.ChatCompletionRequest, ResponseEntity<ByteDanceChatApi.ChatCompletion>>
        implements ChatModel, StreamingChatModel {

    private static final Logger logger = LoggerFactory.getLogger(ByteDanceChatModel.class);

    /**
     * 用于chat completion api请求的默认选项。
     */
    private ByteDanceChatOptions defaultOptions;

    /**
     * 用于重试API调用。
     */
    public final RetryTemplate retryTemplate;

    /**
     * 初始化ByteDanceChatModel的一个实例。
     */
    private final ByteDanceChatApi byteDanceChatApi;

    public ByteDanceChatModel(ByteDanceChatApi byteDanceChatApi) {
        this(byteDanceChatApi, ByteDanceChatOptions.builder().withTemperature(0.7f).build());
    }

    /**
     * 初始化ByteDanceChatModel的一个实例。
     *
     * @param byteDanceChatApi The ByteDanceChatApi instance to be used for interacting with the ByteDance
     *                         Chat API.
     * @param options          The ByteDanceChatOptions to configure the chat model.
     */
    public ByteDanceChatModel(ByteDanceChatApi byteDanceChatApi, ByteDanceChatOptions options) {
        this(byteDanceChatApi, options, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    /**
     * 初始化ByteDanceHatModel的新实例。
     *
     * @param byteDanceChatApi        The ByteDanceChatApi instance to be used for interacting with the ByteDance
     *                                Chat API.
     * @param options                 The ByteDanceChatOptions to configure the chat model.
     * @param functionCallbackContext The function callback context.
     * @param retryTemplate           The retry template.
     */
    public ByteDanceChatModel(ByteDanceChatApi byteDanceChatApi, ByteDanceChatOptions options,
                              FunctionCallbackContext functionCallbackContext, RetryTemplate retryTemplate) {
        super(functionCallbackContext);
        Assert.notNull(byteDanceChatApi, "ByteDanceChatApi must not be null");
        Assert.notNull(options, "Options must not be null");
        Assert.notNull(retryTemplate, "RetryTemplate must not be null");
        this.byteDanceChatApi = byteDanceChatApi;
        this.defaultOptions = options;
        this.retryTemplate = retryTemplate;
    }

    /**
     * 刷新apiKey
     *
     * @param newApiKey
     */
    public void refreshApiKey(String newApiKey) {
        byteDanceChatApi.refreshApiKey(newApiKey);
    }

    @Override
    public ChatResponse call(Prompt prompt) {

        ByteDanceChatApi.ChatCompletionRequest request = createRequest(prompt, false);

        return this.retryTemplate.execute(ctx -> {

            ResponseEntity<ByteDanceChatApi.ChatCompletion> completionEntity = this.callWithFunctionSupport(request);

            var chatCompletion = completionEntity.getBody();
            if (chatCompletion == null) {
                logger.warn("No chat completion returned for prompt: {}", prompt);
                return new ChatResponse(List.of());
            }

            List<Generation> generations = chatCompletion.choices().stream().map(choice -> new Generation(choice.message().content(), toMap(chatCompletion.id(), choice))
                    .withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason().name(), null))).toList();

            return new ChatResponse(generations,
                    ByteDanceChatResponseMetadata.from(completionEntity.getBody()));
        });
    }

    private Map<String, Object> toMap(String id, ByteDanceChatApi.ChatCompletion.Choice choice) {
        Map<String, Object> map = new HashMap<>();

        var message = choice.message();
        if (message.role() != null) {
            map.put("role", message.role().name());
        }
        if (choice.finishReason() != null) {
            map.put("finishReason", choice.finishReason().name());
        }
        map.put("id", id);
        return map;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {

        ByteDanceChatApi.ChatCompletionRequest request = createRequest(prompt, true);

        return this.retryTemplate.execute(ctx -> {

            Flux<ByteDanceChatApi.ChatCompletionChunk> completionChunks = this.byteDanceChatApi.chatCompletionStream(request);

            // For chunked responses, only the first chunk contains the choice role.
            // The rest of the chunks with same ID share the same role.
            ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

            // Convert the ChatCompletionChunk into a ChatCompletion to be able to reuse
            // the function call handling logic.
            return completionChunks.map(chunk -> chunkToChatCompletion(chunk))
                    .switchMap(
                            cc -> handleFunctionCallOrReturnStream(request, Flux.just(ResponseEntity.of(Optional.of(cc)))))
                    .map(ResponseEntity::getBody)
                    .map(chatCompletion -> {
                        try {
                            @SuppressWarnings("null")
                            String id = chatCompletion.id();

                            List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
                                if (choice.message().role() != null) {
                                    roleMap.putIfAbsent(id, choice.message().role().name());
                                }
                                String finish = (choice.finishReason() != null ? choice.finishReason().name() : "");
                                var generation = new Generation(choice.message().content(),
                                        Map.of("id", id, "role", roleMap.get(id), "finishReason", finish));
                                if (choice.finishReason() != null) {
                                    generation = generation.withGenerationMetadata(
                                            ChatGenerationMetadata.from(choice.finishReason().name(), null));
                                }
                                return generation;
                            }).toList();

                            return new ChatResponse(generations);
                        } catch (Exception e) {
                            logger.error("Error processing chat completion", e);
                            return new ChatResponse(List.of());
                        }

                    });
        });
    }

    /**
     * 将ChatCompletionChunk转换为ChatCompletion。
     *
     * @param chunk the ChatCompletionChunk to convert
     * @return the ChatCompletion
     */
    private ByteDanceChatApi.ChatCompletion chunkToChatCompletion(ByteDanceChatApi.ChatCompletionChunk chunk) {
        List<ByteDanceChatApi.ChatCompletion.Choice> choices = chunk.choices()
                .stream()
                .map(cc -> new ByteDanceChatApi.ChatCompletion.Choice(cc.finishReason(), cc.index(), cc.delta(), cc.logprobs()))
                .toList();

        return new ByteDanceChatApi.ChatCompletion(chunk.id(), choices, chunk.created(), chunk.model(), chunk.object(), chunk.usage());
    }

    /**
     * 可用于进行测试。
     */
    ByteDanceChatApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

        List<ByteDanceChatApi.ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions().stream().map(m -> {
            if (m.getMessageType() == MessageType.USER && !CollectionUtils.isEmpty(m.getMedia())) {
                // Add text content.
                List<ByteDanceChatApi.ChatCompletionMessage.MediaContent> contents = new ArrayList<>(List.of(new ByteDanceChatApi.ChatCompletionMessage.MediaContent(m.getContent())));

                // Add media content.
                contents.addAll(m.getMedia()
                        .stream()
                        .map(media -> new ByteDanceChatApi.ChatCompletionMessage.MediaContent(
                                new ByteDanceChatApi.ChatCompletionMessage.MediaContent.ImageUrl(this.fromMediaData(media.getMimeType(), media.getData()))))
                        .toList());

                return new ByteDanceChatApi.ChatCompletionMessage(contents, ByteDanceChatApi.ChatCompletionMessage.Role.valueOf(m.getMessageType().name()));
            }

            return new ByteDanceChatApi.ChatCompletionMessage(m.getContent(), ByteDanceChatApi.ChatCompletionMessage.Role.valueOf(m.getMessageType().name()));
        }).toList();

        ByteDanceChatApi.ChatCompletionRequest request = new ByteDanceChatApi.ChatCompletionRequest(chatCompletionMessages, stream);

        if (prompt.getOptions() != null) {
            if (prompt.getOptions() != null) {
                ChatOptions runtimeOptions = prompt.getOptions();
                ByteDanceChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
                        ChatOptions.class, ByteDanceChatOptions.class);

                request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ByteDanceChatApi.ChatCompletionRequest.class);
            } else {
                throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
                        + prompt.getOptions().getClass().getSimpleName());
            }
        }

        if (this.defaultOptions != null) {
            request = ModelOptionsUtils.merge(request, this.defaultOptions, ByteDanceChatApi.ChatCompletionRequest.class);
        }

        return request;
    }

    private String fromMediaData(MimeType mimeType, Object mediaContentData) {
        if (mediaContentData instanceof byte[] bytes) {
            // Assume the bytes are an image. So, convert the bytes to a base64 encoded
            // following the prefix pattern.
            return String.format("data:%s;base64,%s", mimeType.toString(), Base64.getEncoder().encodeToString(bytes));
        } else if (mediaContentData instanceof String text) {
            // Assume the text is a URLs or a base64 encoded image prefixed by the user.
            return text;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported media data type: " + mediaContentData.getClass().getSimpleName());
        }
    }

    @Override
    protected ByteDanceChatApi.ChatCompletionRequest doCreateToolResponseRequest(ByteDanceChatApi.ChatCompletionRequest previousRequest,
                                                                                 ByteDanceChatApi.ChatCompletionMessage responseMessage, List<ByteDanceChatApi.ChatCompletionMessage> conversationHistory) {
        throw new UnsupportedOperationException("ByteDance does not support tool response requests");
    }

    @Override
    protected List<ByteDanceChatApi.ChatCompletionMessage> doGetUserMessages(ByteDanceChatApi.ChatCompletionRequest request) {
        return request.messages();
    }

    @Override
    protected ByteDanceChatApi.ChatCompletionMessage doGetToolResponseMessage(ResponseEntity<ByteDanceChatApi.ChatCompletion> chatCompletion) {
        return chatCompletion.getBody().choices().iterator().next().message();
    }

    @Override
    protected ResponseEntity<ByteDanceChatApi.ChatCompletion> doChatCompletion(ByteDanceChatApi.ChatCompletionRequest request) {
        return this.byteDanceChatApi.chatCompletionEntity(request);
    }

    @Override
    protected Flux<ResponseEntity<ByteDanceChatApi.ChatCompletion>> doChatCompletionStream(ByteDanceChatApi.ChatCompletionRequest request) {
        return this.byteDanceChatApi.chatCompletionStream(request)
                .map(this::chunkToChatCompletion)
                .map(Optional::ofNullable)
                .map(ResponseEntity::of);
    }

    @Override
    protected boolean isToolFunctionCall(ResponseEntity<ByteDanceChatApi.ChatCompletion> chatCompletion) {
        return false;
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ByteDanceChatOptions.fromOptions(this.defaultOptions);
    }

}
