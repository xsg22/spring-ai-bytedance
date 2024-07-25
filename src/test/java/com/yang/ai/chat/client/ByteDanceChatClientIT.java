package com.yang.ai.chat.client;

import com.yang.ai.ByteDanceChatOptions;
import com.yang.ai.ByteDanceTestConfiguration;
import com.yang.ai.api.tool.MockWeatherService;
import com.yang.ai.testutils.AbstractIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ByteDanceTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "BYTE_DANCE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "MODEL_END_POINT", matches = ".+")
class ByteDanceChatClientIT extends AbstractIT {

    private static final Logger logger = LoggerFactory.getLogger(ByteDanceChatClientIT.class);

    @Value("classpath:/prompts/system-message.st")
    private Resource systemTextResource;

    String modelEndPoint = System.getenv("MODEL_END_POINT");

    record ActorsFilms(String actor, List<String> movies) {
    }

    @Test
    void call() {

        // @formatter:off
		ChatResponse response = ChatClient.builder(chatModel).build().prompt()
				.options(ByteDanceChatOptions.builder()
						.withModel(modelEndPoint)
						.build()
				)
				.system(s -> s.text(systemTextResource)
						.param("name", "Bob")
						.param("voice", "pirate"))
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.call()
				.chatResponse();
		// @formatter:on

        logger.info("" + response);
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getOutput().getContent()).contains("Blackbeard");
    }

    @Test
    void listOutputConverterString() {
        // @formatter:off
		List<String> collection = ChatClient.builder(chatModel).build().prompt()
                .options(ByteDanceChatOptions.builder()
                        .withModel(modelEndPoint)
                        .build()
                )
				.user(u -> u.text("List five {subject}")
						.param("subject", "ice cream flavors"))
				.call()
				.entity(new ParameterizedTypeReference<List<String>>() {});
		// @formatter:on

        logger.info(collection.toString());
        assertThat(collection).hasSize(5);
    }

    @Test
    void listOutputConverterBean() {

        // @formatter:off
		List<ActorsFilms> actorsFilms = ChatClient.builder(chatModel).build().prompt()
                .options(ByteDanceChatOptions.builder()
                        .withModel(modelEndPoint)
                        .build()
                )
				.user("Generate the filmography of 5 movies for Tom Hanks and Bill Murray.")
				.call()
				.entity(new ParameterizedTypeReference<List<ActorsFilms>>() {
				});
		// @formatter:on

        logger.info("" + actorsFilms);
        assertThat(actorsFilms).hasSize(2);
    }

    @Test
    void customOutputConverter() {

        var toStringListConverter = new ListOutputConverter(new DefaultConversionService());

        // @formatter:off
		List<String> flavors = ChatClient.builder(chatModel).build().prompt()
                .options(ByteDanceChatOptions.builder()
                        .withModel(modelEndPoint)
                        .build()
                )
				.user(u -> u.text("List five {subject}")
				.param("subject", "ice cream flavors"))
				.call()
				.entity(toStringListConverter);
		// @formatter:on

        logger.info("ice cream flavors" + flavors);
        assertThat(flavors).hasSize(5);
        assertThat(flavors).containsAnyOf("Vanilla", "vanilla");
    }

    @Test
    void mapOutputConverter() {
        // @formatter:off
		Map<String, Object> result = ChatClient.builder(chatModel).build().prompt()
                .options(ByteDanceChatOptions.builder()
                        .withModel(modelEndPoint)
                        .build()
                )
				.user(u -> u.text("Provide me a List of {subject}")
						.param("subject", "an array of numbers from 1 to 9 under they key name 'numbers'"))
				.call()
				.entity(new ParameterizedTypeReference<Map<String, Object>>() {
				});
		// @formatter:on

        assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));
    }

    @Test
    void beanOutputConverter() {

        // @formatter:off
		ActorsFilms actorsFilms = ChatClient.builder(chatModel).build().prompt()
                .options(ByteDanceChatOptions.builder()
                        .withModel(modelEndPoint)
                        .build()
                )
				.user("Generate the filmography for a random actor.")
				.call()
				.entity(ActorsFilms.class);
		// @formatter:on

        logger.info("" + actorsFilms);
        assertThat(actorsFilms.actor()).isNotBlank();
    }

    @Test
    void beanOutputConverterRecords() {

        // @formatter:off
		ActorsFilms actorsFilms = ChatClient.builder(chatModel).build().prompt()
                .options(ByteDanceChatOptions.builder()
                        .withModel(modelEndPoint)
                        .build()
                )
				.user("Generate the filmography of 5 movies for Tom Hanks.")
				.call()
				.entity(ActorsFilms.class);
		// @formatter:on

        logger.info("" + actorsFilms);
        assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
        assertThat(actorsFilms.movies()).hasSize(5);
    }

    @Test
    void beanStreamOutputConverterRecords() {

        BeanOutputConverter<ActorsFilms> outputConverter = new BeanOutputConverter<>(ActorsFilms.class);

        // @formatter:off
		Flux<String> chatResponse = ChatClient.builder(chatModel)
				.build()
				.prompt()
                .options(ByteDanceChatOptions.builder()
                        .withModel(modelEndPoint)
                        .build()
                )
				.user(u -> u
						.text("Generate the filmography of 5 movies for Tom Hanks. " + System.lineSeparator()
								+ "{format}")
						.param("format", outputConverter.getFormat()))
				.stream()
				.content();

		String generationTextFromStream = chatResponse.collectList()
				.block()
				.stream()
				.collect(Collectors.joining());
		// @formatter:on

        ActorsFilms actorsFilms = outputConverter.convert(generationTextFromStream);

        logger.info("" + actorsFilms);
        assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
        assertThat(actorsFilms.movies()).hasSize(5);
    }

    @Test
    void streamFunctionCallTest() {

        // @formatter:off
		Flux<String> response = ChatClient.builder(chatModel).build().prompt()
                .options(ByteDanceChatOptions.builder()
                        .withModel(modelEndPoint)
                        .build()
                )
				.user("What's the weather like in San Francisco, Tokyo, and Paris?")
				.function("getCurrentWeather", "Get the weather in location", new MockWeatherService())
				.stream()
				.content();
		// @formatter:on

        String content = response.collectList().block().stream().collect(Collectors.joining());
        logger.info("Response: {}", content);

        assertThat(content).containsAnyOf("30.0", "30");
        assertThat(content).containsAnyOf("10.0", "10");
        assertThat(content).containsAnyOf("15.0", "15");
    }

//    @ParameterizedTest(name = "{0} : {displayName} ")
//    @ValueSource(strings = {"gpt-4-vision-preview", "gpt-4o"})
//    void multiModalityEmbeddedImage(String modelName) throws IOException {
//
//        // @formatter:off
//		String response = ChatClient.builder(chatModel).build().prompt()
//				// TODO consider adding model(...) method to ChatClient as a shortcut to
//				// OpenAiChatOptions.builder().withModel(modelName).build()
//				.options(ByteDanceChatOptions.builder().withModel(modelName).build())
//				.user(u -> u.text("Explain what do you see on this picture?")
//						.media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.png")))
//				.call()
//				.content();
//		// @formatter:on
//
//        logger.info(response);
//        assertThat(response).contains("bananas", "apple");
//        assertThat(response).containsAnyOf("bowl", "basket");
//    }
//
//    @ParameterizedTest(name = "{0} : {displayName} ")
//    @ValueSource(strings = {"gpt-4-vision-preview", "gpt-4o"})
//    void multiModalityImageUrl(String modelName) throws IOException {
//
//        // TODO: add url method that wrapps the checked exception.
//        URL url = new URL("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png");
//
//        // @formatter:off
//		String response = ChatClient.builder(chatModel).build().prompt()
//				// TODO consider adding model(...) method to ChatClient as a shortcut to
//				// OpenAiChatOptions.builder().withModel(modelName).build()
//				.options(ByteDanceChatOptions.builder().withModel(modelName).build())
//				.user(u -> u.text("Explain what do you see on this picture?").media(MimeTypeUtils.IMAGE_PNG, url))
//				.call()
//				.content();
//		// @formatter:on
//
//        logger.info(response);
//        assertThat(response).contains("bananas", "apple");
//        assertThat(response).containsAnyOf("bowl", "basket");
//    }
//
//    @Test
//    void streamingMultiModalityImageUrl() throws IOException {
//
//        // TODO: add url method that wrapps the checked exception.
//        URL url = new URL("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png");
//
//        // @formatter:off
//		Flux<String> response = ChatClient.builder(chatModel).build().prompt()
//				.options(ByteDanceChatOptions.builder().withModel(ByteDanceChatApi.ChatModel.GPT_4_VISION_PREVIEW.getValue())
//						.build())
//				.user(u -> u.text("Explain what do you see on this picture?")
//						.media(MimeTypeUtils.IMAGE_PNG, url))
//				.stream()
//				.content();
//		// @formatter:on
//
//        String content = response.collectList().block().stream().collect(Collectors.joining());
//
//        logger.info("Response: {}", content);
//        assertThat(content).contains("bananas", "apple");
//        assertThat(content).containsAnyOf("bowl", "basket");
//    }

}