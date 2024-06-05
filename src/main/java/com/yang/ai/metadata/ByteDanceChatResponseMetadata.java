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
package com.yang.ai.metadata;

import com.yang.ai.api.ByteDanceChatApi;
import org.springframework.ai.chat.metadata.*;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.HashMap;

/**
 * {@link ChatResponseMetadata} implementation for {@literal ByteDance}.
 *
 * @author yang
 * @see ChatResponseMetadata
 * @see RateLimit
 * @see Usage
 */
public class ByteDanceChatResponseMetadata extends HashMap<String, Object> implements ChatResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, id: %2$s, usage: %3$s, rateLimit: %4$s }";

	public static ByteDanceChatResponseMetadata from(ByteDanceChatApi.ChatCompletion result) {
		Assert.notNull(result, "ByteDance ChatCompletionResult must not be null");
		ByteDanceUsage usage = ByteDanceUsage.from(result.usage());
		ByteDanceChatResponseMetadata chatResponseMetadata = new ByteDanceChatResponseMetadata(result.id(), usage);
		return chatResponseMetadata;
	}

	private final String id;

	@Nullable
	private RateLimit rateLimit;

	private final Usage usage;

	protected ByteDanceChatResponseMetadata(String id, ByteDanceUsage usage) {
		this(id, usage, null);
	}

	protected ByteDanceChatResponseMetadata(String id, ByteDanceUsage usage, @Nullable ByteDanceRateLimit rateLimit) {
		this.id = id;
		this.usage = usage;
		this.rateLimit = rateLimit;
	}

	public String getId() {
		return this.id;
	}

	@Override
	@Nullable
	public RateLimit getRateLimit() {
		RateLimit rateLimit = this.rateLimit;
		return rateLimit != null ? rateLimit : new EmptyRateLimit();
	}

	@Override
	public Usage getUsage() {
		Usage usage = this.usage;
		return usage != null ? usage : new EmptyUsage();
	}

	public ByteDanceChatResponseMetadata withRateLimit(RateLimit rateLimit) {
		this.rateLimit = rateLimit;
		return this;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getId(), getUsage(), getRateLimit());
	}

}
