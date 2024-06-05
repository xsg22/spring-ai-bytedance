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
package com.yang.ai.metadata.support;

/**
 * {@link Enum Enumeration} of {@literal ByteDance} API response headers.
 *
 * @author yang
 */
public enum ByteDanceApiResponseHeaders {

	REQUESTS_LIMIT_HEADER("x-ratelimit-limit-requests", "Total number of requests allowed within timeframe."),
	REQUESTS_REMAINING_HEADER("x-ratelimit-remaining-requests", "Remaining number of requests available in timeframe."),
	REQUESTS_RESET_HEADER("x-ratelimit-reset-requests", "Duration of time until the number of requests reset."),
	TOKENS_RESET_HEADER("x-ratelimit-reset-tokens", "Total number of tokens allowed within timeframe."),
	TOKENS_LIMIT_HEADER("x-ratelimit-limit-tokens", "Remaining number of tokens available in timeframe."),
	TOKENS_REMAINING_HEADER("x-ratelimit-remaining-tokens", "Duration of time until the number of tokens reset.");

	private final String headerName;

	private final String description;

	ByteDanceApiResponseHeaders(String headerName, String description) {
		this.headerName = headerName;
		this.description = description;
	}

	public String getName() {
		return this.headerName;
	}

	public String getDescription() {
		return this.description;
	}

}
