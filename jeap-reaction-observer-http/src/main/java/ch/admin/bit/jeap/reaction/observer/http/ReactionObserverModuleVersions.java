package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.configuration.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.header.OperationNameResolver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The modules of the Reaction Observer are released together and use each other's classes without a frozen
 * contract. A service that combines this module with another version of a module it uses can fail anywhere,
 * for example with a {@link NoClassDefFoundError} in the middle of an outgoing call. The check makes such a
 * service fail at its start instead, with a message that says how to choose the version. It is made by the
 * {@link OnHttpRequestObservationCondition}, for a service whose HTTP requests are observed.
 * <p>
 * A module tells its version in the manifest of its jar ({@code Implementation-Version}), which the build of
 * this library writes. Without a jar - in the tests of this library, or in an IDE - the version is unknown and
 * the module is not checked.
 */
final class ReactionObserverModuleVersions {

    private static final String THIS_MODULE = "jeap-reaction-observer-http";

    private ReactionObserverModuleVersions() {
    }

    /**
     * @throws IllegalStateException if a module this module uses is of another version than this module
     */
    static void requireSameVersion() {
        requireSameVersion(null);
    }

    /**
     * @param failure what already failed, possibly because of modules of different versions: the cause of the
     *                exception then
     * @throws IllegalStateException if a module this module uses is of another version than this module
     */
    static void requireSameVersion(Throwable failure) {
        Map<String, String> versionsOfUsedModules = new LinkedHashMap<>();
        versionsOfUsedModules.put("jeap-reaction-observer-header-starter", versionOf(OperationNameResolver.class));
        versionsOfUsedModules.put("jeap-reaction-observer-core", versionOf(ReactionRecorder.class));
        versionsOfUsedModules.put("jeap-reaction-observer-configuration", versionOf(ReactionObserverProperties.class));
        requireSameVersion(versionOf(ReactionObserverModuleVersions.class), versionsOfUsedModules, failure);
    }

    /**
     * @param version               the version of this module, null if unknown
     * @param versionsOfUsedModules the versions of the modules this module uses by the name of the module, null
     *                              for a module whose version is unknown
     * @param failure               the cause of the exception, null if nothing failed
     */
    static void requireSameVersion(String version, Map<String, String> versionsOfUsedModules, Throwable failure) {
        if (version == null) {
            return;
        }
        String modulesOfAnotherVersion = versionsOfUsedModules.entrySet().stream()
                .filter(usedModule -> usedModule.getValue() != null && !usedModule.getValue().equals(version))
                .map(usedModule -> usedModule.getKey() + " is " + usedModule.getValue())
                .collect(Collectors.joining(", "));
        if (!modulesOfAnotherVersion.isEmpty()) {
            throw new IllegalStateException("All modules of the Reaction Observer have to be of the same version, but " +
                    THIS_MODULE + " is " + version + " and " + modulesOfAnotherVersion + ". Choose the version of all " +
                    "modules with the property jeap-reaction-observer.version of the jEAP parent, not with a version " +
                    "at a single dependency.", failure);
        }
    }

    private static String versionOf(Class<?> classOfModule) {
        return classOfModule.getPackage().getImplementationVersion();
    }
}
