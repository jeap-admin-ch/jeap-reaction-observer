package ch.admin.bit.jeap.reaction.observer.http;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits the number of different operation names an instance accepts from the targets it calls. The names come
 * from the called targets, and each new name is a new action: a target that names a different operation with
 * every response - by mistake or not - would otherwise create a new reaction per call.
 * <p>
 * The accepted names form one stock that all targets share:
 * <ul>
 *     <li>A target may contribute a limited number of new names to the stock, so that a single target cannot use
 *     up more than its share.</li>
 *     <li>Every target may name an operation that is already in the stock: the name is an action already, and
 *     refusing it would not keep the vocabulary any smaller.</li>
 *     <li>The stock as a whole is limited, because the limit per target alone bounds nothing if the targets
 *     themselves come from data (webhooks, callbacks).</li>
 * </ul>
 * The target is the one the consumer called, as {@link HttpTargetNaming} names it - not the component the
 * operation name claims to belong to, which is part of what cannot be trusted here. The names are counted from
 * the start of the instance.
 */
class HttpOperationRegistry {

    private final int maxNewOperationCountPerTarget;
    private final int maxOperationCount;
    private final Set<String> operations = ConcurrentHashMap.newKeySet();
    /**
     * The number of names each target contributed to the stock, read and changed only while holding the lock of
     * this registry. A target is only remembered with a name it contributed, so the limit of the stock bounds
     * the targets too.
     */
    private final Map<String, Integer> newOperationCountByTarget = new HashMap<>();

    /**
     * @param maxNewOperationCountPerTarget the number of new operation names one target may contribute, 0
     *                                      accepts none
     * @param maxOperationCount             the number of different operation names accepted in total, 0 accepts
     *                                      none
     */
    HttpOperationRegistry(int maxNewOperationCountPerTarget, int maxOperationCount) {
        if (maxNewOperationCountPerTarget < 0) {
            throw new IllegalArgumentException(
                    "The maximum number of new operations per target must not be negative: " + maxNewOperationCountPerTarget);
        }
        if (maxOperationCount < 0) {
            throw new IllegalArgumentException("The maximum number of operations must not be negative: " + maxOperationCount);
        }
        this.maxNewOperationCountPerTarget = maxNewOperationCountPerTarget;
        this.maxOperationCount = maxOperationCount;
    }

    int maxNewOperationCountPerTarget() {
        return maxNewOperationCountPerTarget;
    }

    int maxOperationCount() {
        return maxOperationCount;
    }

    /**
     * @return whether one of the limits is 0, so that no operation name is accepted at all
     */
    boolean acceptsNoOperations() {
        return maxNewOperationCountPerTarget == 0 || maxOperationCount == 0;
    }

    /**
     * @return true if the operation is one of the operations this instance accepts - it has been accepted
     * before, from whatever target, or there was room left for it: the target may still contribute a new name
     * and the stock is not full
     */
    boolean register(String target, String operation) {
        if (operations.contains(operation)) {
            return true;
        }
        synchronized (this) {
            if (operations.contains(operation)) {
                return true;
            }
            int newOperationCountOfTarget = newOperationCountByTarget.getOrDefault(target, 0);
            if (newOperationCountOfTarget >= maxNewOperationCountPerTarget || operations.size() >= maxOperationCount) {
                return false;
            }
            operations.add(operation);
            newOperationCountByTarget.put(target, newOperationCountOfTarget + 1);
            return true;
        }
    }
}
