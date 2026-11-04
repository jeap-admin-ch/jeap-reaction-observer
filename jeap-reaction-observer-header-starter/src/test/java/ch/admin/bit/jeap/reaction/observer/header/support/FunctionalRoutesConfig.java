package ch.admin.bit.jeap.reaction.observer.header.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/** WebMvc.fn functional endpoint, to exercise the {@code HandlerFunction} branch of operation resolution. */
@Configuration
public class FunctionalRoutesConfig {

    @Bean
    public RouterFunction<ServerResponse> functionalRoutes() {
        return RouterFunctions.route()
                .GET("/functional/{id}", req -> ServerResponse.ok().body("functional-" + req.pathVariable("id")))
                .build();
    }
}
