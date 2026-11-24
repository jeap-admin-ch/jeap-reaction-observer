package ch.admin.bit.jeap.reaction.observer.http;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactionObserverModuleVersionsTest {

    private static final String HEADER = "jeap-reaction-observer-header-starter";
    private static final String CORE = "jeap-reaction-observer-core";
    private static final String CONFIGURATION = "jeap-reaction-observer-configuration";

    @Test
    void allModulesOfTheSameVersion_passes() {
        Map<String, String> usedModules = versions("11.39.0", "11.39.0", "11.39.0");

        assertThatCode(() -> ReactionObserverModuleVersions.requireSameVersion("11.39.0", usedModules, null))
                .doesNotThrowAnyException();
    }

    @Test
    void moduleOfAnotherVersion_isRejectedWithTheVersionsAndTheWayToChooseThem() {
        Map<String, String> usedModules = versions("11.38.0", "11.39.0", "11.39.0");

        assertThatThrownBy(() -> ReactionObserverModuleVersions.requireSameVersion("11.39.0", usedModules, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jeap-reaction-observer-http is 11.39.0 and " + HEADER + " is 11.38.0.")
                .hasMessageContaining("property jeap-reaction-observer.version of the jEAP parent")
                .hasMessageNotContaining(CORE)
                .hasMessageNotContaining(CONFIGURATION);
    }

    @Test
    void severalModulesOfAnotherVersion_areAllNamed() {
        Map<String, String> usedModules = versions("11.38.0", "11.36.0", "11.40.0-SNAPSHOT");

        assertThatThrownBy(() -> ReactionObserverModuleVersions.requireSameVersion("11.39.0", usedModules, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(HEADER + " is 11.38.0, " + CORE + " is 11.36.0, " + CONFIGURATION + " is 11.40.0-SNAPSHOT.");
    }

    /**
     * A newer module is as wrong as an older one: the modules are released together.
     */
    @Test
    void newerModule_isRejected() {
        Map<String, String> usedModules = versions("11.40.0", "11.39.0", "11.39.0");

        assertThatThrownBy(() -> ReactionObserverModuleVersions.requireSameVersion("11.39.0", usedModules, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(HEADER + " is 11.40.0");
    }

    /**
     * Classes that are not loaded from a jar have no version, as in the tests of this library.
     */
    @Test
    void versionOfThisModuleUnknown_nothingIsChecked() {
        Map<String, String> usedModules = versions("11.38.0", "11.36.0", "11.39.0");

        assertThatCode(() -> ReactionObserverModuleVersions.requireSameVersion(null, usedModules, null))
                .doesNotThrowAnyException();
    }

    @Test
    void versionOfAUsedModuleUnknown_theOtherModulesAreChecked() {
        Map<String, String> onlyUnknown = versions(null, "11.39.0", "11.39.0");
        Map<String, String> unknownAndAnotherVersion = versions(null, "11.36.0", "11.39.0");

        assertThatCode(() -> ReactionObserverModuleVersions.requireSameVersion("11.39.0", onlyUnknown, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ReactionObserverModuleVersions.requireSameVersion("11.39.0", unknownAndAnotherVersion, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CORE + " is 11.36.0")
                .hasMessageNotContaining(HEADER);
    }

    /**
     * The tests of this library run from the class directories of the modules: the versions are unknown, and
     * the auto-configurations start.
     */
    @Test
    void inTheTestsOfThisLibrary_nothingIsChecked() {
        assertThatCode(() -> ReactionObserverModuleVersions.requireSameVersion()).doesNotThrowAnyException();
    }

    /**
     * A module can be too old to be used at all, which shows as a {@link LinkageError}: the versions explain it.
     */
    @Test
    void moduleOfAnotherVersionAfterAFailure_theFailureIsTheCause() {
        Map<String, String> usedModules = versions("11.39.0", "11.36.0", "11.39.0");
        LinkageError failure = new NoClassDefFoundError("ch/admin/bit/jeap/reaction/observer/core/domain/model/ReactionModel");

        assertThatThrownBy(() -> ReactionObserverModuleVersions.requireSameVersion("11.39.0", usedModules, failure))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CORE + " is 11.36.0")
                .hasCause(failure);
    }

    private static Map<String, String> versions(String header, String core, String configuration) {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put(HEADER, header);
        versions.put(CORE, core);
        versions.put(CONFIGURATION, configuration);
        return versions;
    }
}
