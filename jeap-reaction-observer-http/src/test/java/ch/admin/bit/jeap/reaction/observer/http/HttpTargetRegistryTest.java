package ch.admin.bit.jeap.reaction.observer.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpTargetRegistryTest {

    private static final int MAX_TARGET_COUNT = 5;

    private final HttpTargetRegistry registry = new HttpTargetRegistry(MAX_TARGET_COUNT);

    @Test
    void register_acceptsTargetsUpToTheLimit() {
        for (int i = 0; i < MAX_TARGET_COUNT; i++) {
            assertThat(registry.register("//host-" + i)).isTrue();
        }

        assertThat(registry.register("//one-too-many")).isFalse();
        assertThat(registry.register("//one-too-many"))
                .describedAs("A target over the limit stays over the limit")
                .isFalse();
        assertThat(registry.register("//host-0"))
                .describedAs("A registered target stays registered")
                .isTrue();
    }

    @Test
    void register_sameTargetTakesOnePlace() {
        for (int i = 0; i < 1000; i++) {
            assertThat(registry.register("//host")).isTrue();
        }

        assertThat(registry.register("//other-host")).isTrue();
    }

    @Test
    void register_hostAndContextAreDifferentTargets() {
        for (int i = 0; i < MAX_TARGET_COUNT; i++) {
            assertThat(registry.register("//host/service-" + i)).isTrue();
        }

        assertThat(registry.register("//host")).isFalse();
    }

    @Test
    void limitZero_acceptsNoTarget() {
        HttpTargetRegistry noTargets = new HttpTargetRegistry(0);

        assertThat(noTargets.register("//host")).isFalse();
        assertThat(noTargets.maxTargetCount()).isZero();
    }

    @Test
    void negativeLimit_isRejected() {
        assertThatThrownBy(() -> new HttpTargetRegistry(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }
}
