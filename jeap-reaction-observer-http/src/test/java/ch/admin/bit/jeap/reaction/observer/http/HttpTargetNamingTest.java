package ch.admin.bit.jeap.reaction.observer.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTargetNamingTest {

    private final HttpTargetNaming withoutPort = new HttpTargetNaming(false);
    private final HttpTargetNaming withPort = new HttpTargetNaming(true);

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            https://foo.bar.admin.ch/some-service/api/some/path/12354             | //foo.bar.admin.ch/some-service
            https://jme-nivel.bazg.admin.ch/jme-blabla-service/ui-api/foo/134     | //jme-nivel.bazg.admin.ch/jme-blabla-service
            https://foo.bar.admin.ch/api/orders/12354                             | //foo.bar.admin.ch
            https://foo.bar.admin.ch/some-service/actuator/health                 | //foo.bar.admin.ch
            https://foo.bar.admin.ch/tenants/4711/api/orders                      | //foo.bar.admin.ch
            """)
    void target_examplesOfTheSpecification(String uri, String expectedTarget) {
        assertThat(withoutPort.target(URI.create(uri))).contains(expectedTarget);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            /svc/api/x                     | svc
            /svc/ui-api/x                  | svc
            /svc/external-api/x            | svc
            /svc/api-internal/x            | svc
            /svc/api                       | svc
            /svc/api/                      | svc
            /Svc/api/x                     | Svc
            /process-context/api/x         | process-context
            /my.service_1-a/api/x          | my.service_1-a
            /rest/api/x                    | rest
            /v3/api-docs                   | v3
            /svc/a.b_c-api/x               | svc
            """)
    void context_firstSegmentBeforeAnApiRoot(String path, String expectedContext) {
        assertThat(HttpTargetNaming.context(path)).isEqualTo(expectedContext);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "/",
            "/svc",
            "/svc/",
            "/api/orders",
            "/svc/actuator/health",
            "/tenants/4711/api/orders",
            // The API root is lowercase
            "/svc/API/x",
            "/svc/Ui-Api/x",
            // "-api" and "api-" need a further part
            "/svc/-api/x",
            "/svc/api-/x",
            "/svc/apis/x",
            "/svc/myapi/x",
            // A first segment that is an API root itself is no context
            "/partner-api/api/x",
            "/api/api/x",
            "/api-internal/api/x",
            // Characters other than [A-Za-z0-9._-] in the first segment, also percent-encoded ones
            "/s%76c/api/x",
            "/sv c/api/x",
            "/svc;v=1/api/x",
            "/dienst-für-x/api/x",
            // Matrix parameters are not cut off the API root
            "/svc/api;v=1/x",
            // Segments are neither normalised nor skipped
            "//svc/api/x",
            "/svc//api/x",
            "/./api/x",
            "/../api/x",
            "svc/api/x"
    })
    void context_none(String path) {
        assertThat(HttpTargetNaming.context(path)).isNull();
    }

    @Test
    void context_noPath() {
        assertThat(HttpTargetNaming.context(null)).isNull();
        assertThat(withoutPort.target(URI.create("https://foo.bar.admin.ch"))).contains("//foo.bar.admin.ch");
    }

    @Test
    void context_atMost100Characters() {
        String segmentWith100Characters = "a".repeat(100);
        String segmentWith101Characters = "a".repeat(101);

        assertThat(HttpTargetNaming.context("/" + segmentWith100Characters + "/api/x")).isEqualTo(segmentWith100Characters);
        assertThat(HttpTargetNaming.context("/" + segmentWith101Characters + "/api/x")).isNull();
    }

    @Test
    void target_queryStringIsNotPartOfThePath() {
        assertThat(withoutPort.target(URI.create("https://foo.bar.admin.ch/svc/api?x=/a/api/b")))
                .contains("//foo.bar.admin.ch/svc");
        assertThat(withoutPort.target(URI.create("https://foo.bar.admin.ch/svc?x=/api/")))
                .contains("//foo.bar.admin.ch");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            https://FOO.Bar.Admin.CH/api/x                 | //foo.bar.admin.ch
            https://foo.bar.admin.ch./api/x                | //foo.bar.admin.ch
            http://foo.bar.admin.ch/api/x                  | //foo.bar.admin.ch
            https://user:secret@foo.bar.admin.ch/api/x     | //foo.bar.admin.ch
            https://[2001:DB8::1]/api/x                    | //[2001:db8::1]
            https://[2001:db8::1]:8443/svc/api/x           | //[2001:db8::1]/svc
            http://192.168.1.10:8080/api/x                 | //192.168.1.10
            http://localhost:8095/jme-ros-external/api/x   | //localhost/jme-ros-external
            http://my_service:8080/svc/api/x               | //my_service/svc
            http://user@My_Service/api/x                   | //my_service
            https://xn--bcher-kva.example/api/x            | //xn--bcher-kva.example
            """)
    void target_host_withoutPort(String uri, String expectedTarget) {
        assertThat(withoutPort.target(URI.create(uri))).contains(expectedTarget);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            https://foo.bar.admin.ch:8443/some-service/api/x | //foo.bar.admin.ch:8443/some-service
            https://foo.bar.admin.ch:8443/x                  | //foo.bar.admin.ch:8443
            https://foo.bar.admin.ch/some-service/api/x      | //foo.bar.admin.ch/some-service
            https://foo.bar.admin.ch:443/some-service/api/x  | //foo.bar.admin.ch/some-service
            http://foo.bar.admin.ch:80/api/x                 | //foo.bar.admin.ch
            http://foo.bar.admin.ch:443/api/x                | //foo.bar.admin.ch:443
            https://foo.bar.admin.ch:80/api/x                | //foo.bar.admin.ch:80
            https://[2001:db8::1]:8443/svc/api/x             | //[2001:db8::1]:8443/svc
            http://my_service:8080/svc/api/x                 | //my_service:8080/svc
            http://my_service/svc/api/x                      | //my_service/svc
            """)
    void target_host_withPort(String uri, String expectedTarget) {
        assertThat(withPort.target(URI.create(uri))).contains(expectedTarget);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/relative/api/x", "mailto:someone@example.com", "file:///tmp/x", "http:///api/x"})
    void target_uriWithoutHost_isEmpty(String uri) {
        assertThat(withoutPort.target(URI.create(uri))).isEmpty();
    }

    @Test
    void coarseFqn() {
        assertThat(HttpTargetNaming.coarseFqn("//foo.bar.admin.ch", "POST")).isEqualTo("//foo.bar.admin.ch POST /{*}");
        assertThat(HttpTargetNaming.coarseFqn("//foo.bar.admin.ch/some-service", "get"))
                .isEqualTo("//foo.bar.admin.ch/some-service GET /{*}");
    }

    @Test
    void isApiRoot() {
        assertThat(HttpTargetNaming.isApiRoot("api")).isTrue();
        assertThat(HttpTargetNaming.isApiRoot("ui-api")).isTrue();
        assertThat(HttpTargetNaming.isApiRoot("api-internal")).isTrue();
        assertThat(HttpTargetNaming.isApiRoot("")).isFalse();
        assertThat(HttpTargetNaming.isApiRoot("Api")).isFalse();
        assertThat(HttpTargetNaming.isApiRoot("ui-api;v=1")).isFalse();
    }
}
