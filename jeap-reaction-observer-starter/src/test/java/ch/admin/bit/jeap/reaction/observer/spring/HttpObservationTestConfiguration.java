package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * A consumer that calls providers with a RestClient built by the builder of Spring Boot, as a service does. The
 * providers are stubbed with a {@link MockRestServiceServer}.
 */
@TestConfiguration
class HttpObservationTestConfiguration {

    /**
     * @param server     stubs the providers called by the RestClient
     * @param restClient built by the builder of Spring Boot
     */
    record StubbedRestClient(MockRestServiceServer server, RestClient restClient) {
    }

    @Bean
    StubbedRestClient stubbedRestClient(RestClient.Builder restClientBuilder) {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).ignoreExpectOrder(true).build();
        return new StubbedRestClient(server, restClientBuilder.build());
    }

    @Bean
    HttpTestConsumer httpTestConsumer(KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate, StubbedRestClient stubbedRestClient) {
        return new HttpTestConsumer(kafkaTemplate, stubbedRestClient.restClient());
    }

    @Bean
    ReactionEventsTestConsumer reactionEventsTestConsumer() {
        return new ReactionEventsTestConsumer();
    }
}
