package ch.admin.bit.jeap.reaction.observer.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class HttpTriggerComponentNameTest {

    private static final String WARNING = "carry another name than the component";

    @Test
    void namesDiffer_warns(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment().withProperty("jeap.messaging.kafka.service-name", "partner");

        assertThat(HttpTriggerComponentName.warnIfDifferentFromProvider("partner-service", environment)).isTrue();

        assertThat(output).contains(WARNING).contains("'partner-service'").contains("'partner'");
    }

    @Test
    void namesDiffer_componentNameInCamelCase_warns(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment().withProperty("jeap.messaging.kafka.serviceName", "partner");

        assertThat(HttpTriggerComponentName.warnIfDifferentFromProvider("partner-service", environment)).isTrue();

        assertThat(output).contains(WARNING);
    }

    @Test
    void namesAreEqual_doesNotWarn(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment().withProperty("jeap.messaging.kafka.service-name", "partner-service");

        assertThat(HttpTriggerComponentName.warnIfDifferentFromProvider("partner-service", environment)).isFalse();

        assertThat(output).doesNotContain(WARNING);
    }

    @Test
    void noComponentName_doesNotWarn(CapturedOutput output) {
        assertThat(HttpTriggerComponentName.warnIfDifferentFromProvider("partner-service", new MockEnvironment())).isFalse();

        assertThat(output).doesNotContain(WARNING);
    }

    @Test
    void noProvider_doesNotWarn(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment().withProperty("jeap.messaging.kafka.service-name", "partner");

        assertThat(HttpTriggerComponentName.warnIfDifferentFromProvider("", environment)).isFalse();
        assertThat(HttpTriggerComponentName.warnIfDifferentFromProvider(null, environment)).isFalse();

        assertThat(output).doesNotContain(WARNING);
    }
}
