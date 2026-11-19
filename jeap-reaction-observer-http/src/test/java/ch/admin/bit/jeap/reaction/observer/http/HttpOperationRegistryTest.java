package ch.admin.bit.jeap.reaction.observer.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpOperationRegistryTest {

    private static final int MAX_NEW_OPERATION_COUNT_PER_TARGET = 3;
    private static final int MAX_OPERATION_COUNT = 5;
    private static final String TARGET = "//host/service";

    private final HttpOperationRegistry registry = new HttpOperationRegistry(MAX_NEW_OPERATION_COUNT_PER_TARGET, MAX_OPERATION_COUNT);

    @Test
    void register_aTargetContributesNewOperationsUpToTheLimitPerTarget() {
        for (int i = 0; i < MAX_NEW_OPERATION_COUNT_PER_TARGET; i++) {
            assertThat(registry.register(TARGET, "service GET /api/operation-" + i)).isTrue();
        }

        assertThat(registry.register(TARGET, "service GET /api/one-too-many")).isFalse();
        assertThat(registry.register(TARGET, "service GET /api/one-too-many"))
                .describedAs("An operation over the limit stays over the limit")
                .isFalse();
        assertThat(registry.register(TARGET, "service GET /api/operation-0"))
                .describedAs("An accepted operation stays accepted")
                .isTrue();
    }

    @Test
    void register_sameOperationTakesOnePlace() {
        for (int i = 0; i < 1000; i++) {
            assertThat(registry.register(TARGET, "service GET /api/operation")).isTrue();
        }

        assertThat(registry.register(TARGET, "service GET /api/other-operation")).isTrue();
    }

    @Test
    void register_aTargetOverItsLimitLeavesTheOtherTargetsTheirOperations() {
        for (int i = 0; i < 1000; i++) {
            registry.register(TARGET, "service GET /api/operation-" + i);
        }

        assertThat(registry.register("//other-host", "other-service GET /api/operation")).isTrue();
        assertThat(registry.register("//host", "service GET /api/operation-1000"))
                .describedAs("Host and context are different targets")
                .isTrue();
    }

    @Test
    void register_acceptsDifferentOperationsUpToTheLimitInTotal() {
        for (int i = 0; i < MAX_OPERATION_COUNT; i++) {
            assertThat(registry.register("//host-" + i, "service-" + i + " POST /api/callback")).isTrue();
        }

        assertThat(registry.register("//one-too-many", "service POST /api/callback")).isFalse();
        assertThat(registry.register("//host-0", "service-0 POST /api/other"))
                .describedAs("Also a target that may still contribute gets no further operation accepted")
                .isFalse();
        assertThat(registry.register("//host-0", "service-0 POST /api/callback"))
                .describedAs("An accepted operation stays accepted")
                .isTrue();
    }

    /**
     * Many targets naming the same operation - for example the subscribers of a webhook running the same
     * software - name one action. They take one place, however many they are.
     */
    @Test
    void register_anAcceptedOperationIsAcceptedFromEveryTarget_withoutTakingAPlace() {
        HttpOperationRegistry twoOperations = new HttpOperationRegistry(1, 2);

        for (int i = 0; i < 1000; i++) {
            assertThat(twoOperations.register("//subscriber-" + i, "subscriber-service POST /api/callback")).isTrue();
        }

        assertThat(twoOperations.register("//subscriber-0", "subscriber-service POST /api/other"))
                .describedAs("The target that contributed the operation is at its limit")
                .isFalse();
        assertThat(twoOperations.register("//subscriber-1", "subscriber-service POST /api/other"))
                .describedAs("Naming an accepted operation did not count as a contribution, and there is one place left")
                .isTrue();
        assertThat(twoOperations.register("//subscriber-0", "subscriber-service POST /api/other"))
                .describedAs("Once accepted, also the target at its limit may name the operation")
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource({"0, 5", "3, 0", "0, 0"})
    void limitZero_acceptsNoOperation(int maxNewOperationCountPerTarget, int maxOperationCount) {
        HttpOperationRegistry noOperations = new HttpOperationRegistry(maxNewOperationCountPerTarget, maxOperationCount);

        assertThat(noOperations.register(TARGET, "service GET /api/operation")).isFalse();
        assertThat(noOperations.acceptsNoOperations()).isTrue();
    }

    @Test
    void limitsAboveZero_operationsAreAccepted() {
        assertThat(registry.acceptsNoOperations()).isFalse();
    }

    @Test
    void negativeLimit_isRejected() {
        assertThatThrownBy(() -> new HttpOperationRegistry(-1, MAX_OPERATION_COUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The maximum number of new operations per target must not be negative: -1");
        assertThatThrownBy(() -> new HttpOperationRegistry(MAX_NEW_OPERATION_COUNT_PER_TARGET, -2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The maximum number of operations must not be negative: -2");
    }

    /**
     * Requests are observed in parallel: however the registrations interleave, no target contributes more than
     * its share.
     */
    @Test
    void register_inParallel_noTargetContributesMoreThanTheLimitPerTarget() throws Exception {
        HttpOperationRegistry roomInTotal = new HttpOperationRegistry(MAX_NEW_OPERATION_COUNT_PER_TARGET, 1000);

        Map<String, Long> acceptedByTarget = registerInParallel(roomInTotal, 4, 50);

        assertThat(acceptedByTarget).hasSize(4).containsOnly(
                Map.entry("//host-0", 3L), Map.entry("//host-1", 3L), Map.entry("//host-2", 3L), Map.entry("//host-3", 3L));
    }

    @Test
    void register_inParallel_neverAcceptsMoreThanTheLimitInTotal() throws Exception {
        Map<String, Long> acceptedByTarget = registerInParallel(registry, 4, 50);

        assertThat(acceptedByTarget.values().stream().mapToLong(Long::longValue).sum())
                .describedAs("Four targets that may contribute three operations each, but only five in total")
                .isEqualTo(MAX_OPERATION_COUNT);
        assertThat(acceptedByTarget.values()).allMatch(accepted -> accepted <= MAX_NEW_OPERATION_COUNT_PER_TARGET);
    }

    /**
     * Registers different operations for each of the targets, all in parallel.
     *
     * @return the number of accepted operations by target, without the targets none was accepted from
     */
    private static Map<String, Long> registerInParallel(HttpOperationRegistry registry, int targetCount,
                                                        int operationCountPerTarget) throws Exception {
        List<Callable<Optional<String>>> registrations = new ArrayList<>();
        for (int target = 0; target < targetCount; target++) {
            for (int operation = 0; operation < operationCountPerTarget; operation++) {
                String targetName = "//host-" + target;
                String operationName = "service-" + target + " GET /api/operation-" + operation;
                registrations.add(() -> registry.register(targetName, operationName) ? Optional.of(targetName) : Optional.empty());
            }
        }
        List<String> targetsOfAcceptedOperations = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (Future<Optional<String>> registration : executor.invokeAll(registrations)) {
                registration.get().ifPresent(targetsOfAcceptedOperations::add);
            }
        }
        return targetsOfAcceptedOperations.stream().collect(Collectors.groupingBy(target -> target, Collectors.counting()));
    }
}
