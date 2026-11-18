package ch.admin.bit.jeap.reaction.observer.http;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits the number of different targets an instance names coarse actions for. Targets can come from data
 * (webhooks, callbacks); the limit bounds the vocabulary of an instance in that case. The targets are counted
 * from the start of the instance.
 */
class HttpTargetRegistry {

    private final int maxTargetCount;
    private final Set<String> targets = ConcurrentHashMap.newKeySet();

    /**
     * @param maxTargetCount the number of different targets that are accepted, 0 accepts none
     */
    HttpTargetRegistry(int maxTargetCount) {
        if (maxTargetCount < 0) {
            throw new IllegalArgumentException("The maximum number of targets must not be negative: " + maxTargetCount);
        }
        this.maxTargetCount = maxTargetCount;
    }

    int maxTargetCount() {
        return maxTargetCount;
    }

    /**
     * @return true if the target is one of the targets this instance names coarse actions for - it has been
     * registered before or there was room left for it
     */
    boolean register(String target) {
        if (targets.contains(target)) {
            return true;
        }
        synchronized (targets) {
            if (targets.size() >= maxTargetCount) {
                return targets.contains(target);
            }
            targets.add(target);
            return true;
        }
    }
}
