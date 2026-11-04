package ch.admin.bit.jeap.reaction.observer.header;

import ch.admin.bit.jeap.reaction.observer.header.support.HeaderTestApplication;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderNames.REACTION_MODEL_VERSION;
import static ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderNames.REACTION_OBSERVATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = {HeaderTestApplication.class, ReactionObserverHeaderApiVersionIT.VersionConfig.class},
        webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ReactionObserverHeaderApiVersionIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @ParameterizedTest
    @CsvSource({
            "/versioned/baseline, 1.3, baseline, test-app GET /versioned/baseline ;v=1.2+",
            "/versioned/baseline, 1.5, baseline, test-app GET /versioned/baseline ;v=1.2+",
            "/versioned/exact, 2.0, exact, test-app GET /versioned/exact ;v=2",
            "/method-versioned, 1.0, method, test-app GET /method-versioned ;v=1",
            "/api/items/42, 1.3, item-42, test-app GET /api/items/{}",
            "/api/items/42, 1.5, item-42, test-app GET /api/items/{}",
            "/functional/7, 1.3, functional-7, test-app GET /functional/{}",
            "/functional-versioned, 1.0, functional-v1, test-app GET /functional-versioned",
            "/functional-versioned, 2.0, functional-v2, test-app GET /functional-versioned"
    })
    void versionHeader_doesNotChangeDeclaredOperationName(String path, String version,
                                                         String body, String fqn) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(REACTION_MODEL_VERSION, "2");
        headers.set("API-Version", version);

        ResponseEntity<String> response = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(body);
        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode(fqn));
    }

    @TestConfiguration
    @Import({VersionedController.class, MethodVersionedController.class})
    static class VersionConfig implements WebMvcConfigurer {
        @Override
        public void configureApiVersioning(ApiVersionConfigurer configurer) {
            configurer.useRequestHeader("API-Version")
                    .addSupportedVersions("1", "1.2", "1.3", "1.5", "2");
        }

        @Bean
        RouterFunction<ServerResponse> versionedRoutes() {
            return RouterFunctions.route()
                    .GET("/functional-versioned", RequestPredicates.version("1"),
                            request -> ServerResponse.ok().body("functional-v1"))
                    .GET("/functional-versioned", RequestPredicates.version("2"),
                            request -> ServerResponse.ok().body("functional-v2"))
                    .build();
        }
    }

    @RestController
    @RequestMapping(path = "/versioned", version = "1.2+")
    static class VersionedController {
        @GetMapping("/baseline")
        public String baseline() {
            return "baseline";
        }

        @GetMapping(path = "/exact", version = "2")
        public String exact() {
            return "exact";
        }
    }

    @RestController
    static class MethodVersionedController {
        @GetMapping(path = "/method-versioned", version = "1")
        public String method() {
            return "method";
        }
    }
}
