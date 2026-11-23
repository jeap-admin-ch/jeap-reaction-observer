package ch.admin.bit.jeap.reaction.observer.http;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@ConfigurationProperties("jeap.reaction.observer.http")
@Data
public class ReactionObserverHttpProperties {

    private TargetHost targetHost = new TargetHost();

    /**
     * The number of different targets (host and, where recognised, context) an instance names coarse HTTP
     * request actions for, counted from the start of the instance. Requests to further targets that do not name
     * their operation are not observed. With 0 the instance names no coarse actions at all, without a log entry;
     * the loss metric counts these requests as target_limit. A negative value fails the start of the service.
     */
    private int maxTargetCount = 100;

    /**
     * The number of new operation names one target (host and, where recognised, context) may contribute to the
     * operation names the instance accepts, counted from the start of the instance. A name that is already
     * accepted may be named by every target and does not count. A request whose response names a further new
     * operation is observed like one whose operation is not named; the diagnosis metric counts it as
     * operation_limit. With 0 the instance accepts no operation names at all, without a warning about the limit.
     * A negative value fails the start of the service.
     */
    private int maxNewOperationCountPerTarget = 100;

    /**
     * The number of different operation names an instance accepts in total, whichever targets name them,
     * counted from the start of the instance. A request whose response names a further operation is observed
     * like one whose operation is not named; the diagnosis metric counts it as operation_limit. With 0 the
     * instance accepts no operation names at all, without a warning about the limit. A negative value fails the
     * start of the service.
     */
    private int maxOperationCount = 1000;

    /**
     * How often the observation of HTTP requests writes each of its recurring log entries (failed call, target
     * over the limit, operation name over the limit, failure of the observation of an outgoing or an incoming
     * request): at most once per interval, with the number of occurrences since the last entry. A number without a unit is in seconds.
     * With 0 every occurrence is logged.
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration logInterval = Duration.ofMinutes(1);

    @Data
    public static class TargetHost {
        /**
         * Whether the port belongs to the host in the name of a coarse HTTP request action. Switch it on if the
         * service calls several targets on the same host that differ in the port only. A default port (80 for
         * http, 443 for https) is never part of the name.
         */
        private boolean includePort = false;
    }
}
