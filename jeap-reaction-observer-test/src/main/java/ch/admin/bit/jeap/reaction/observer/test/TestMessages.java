package ch.admin.bit.jeap.reaction.observer.test;

import ch.admin.bit.jme.declaration.JmeCreateDeclarationCommand;
import ch.admin.bit.jme.declaration.JmeDeclarationCreatedEvent;
import ch.admin.bit.jme.test.JmeSimpleTestEvent;

import java.util.UUID;

public class TestMessages {

    public static JmeSimpleTestEvent createJmeSimpleTestEvent(String serviceName) {
        return JmeSimpleTestEventBuilder.create()
                .idempotenceId(UUID.randomUUID().toString())
                .message("test")
                .serviceName(serviceName)
                .build();
    }

    public static JmeCreateDeclarationCommand createJmeCreateDeclarationCommand(String serviceName) {
        return JmeCreateDeclarationCommandBuilder.create()
                .serviceName(serviceName)
                .idempotenceId(UUID.randomUUID().toString())
                .text("text")
                .build();
    }

    public static JmeDeclarationCreatedEvent createJmeDeclarationCreatedEvent(String serviceName, String message) {
        return JmeDeclarationCreatedEventBuilder.create()
                .idempotenceId(UUID.randomUUID().toString())
                .serviceName(serviceName)
                .message(message)
                .build();
    }

    public static JmeDeclarationCreatedEvent createJmeDeclarationCreatedEventWithVariant(String serviceName, String message, String variant) {
        return JmeDeclarationCreatedEventBuilder.create()
                .idempotenceId(UUID.randomUUID().toString())
                .serviceName(serviceName)
                .message(message)
                .variant(variant)
                .build();
    }

}
