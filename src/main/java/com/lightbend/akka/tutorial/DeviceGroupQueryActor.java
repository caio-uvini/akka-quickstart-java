package com.lightbend.akka.tutorial;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;
import com.lightbend.akka.tutorial.model.DeviceNotAvailable;
import com.lightbend.akka.tutorial.model.DeviceTimedOut;
import com.lightbend.akka.tutorial.model.Temperature;
import com.lightbend.akka.tutorial.model.TemperatureNotAvailable;
import com.lightbend.akka.tutorial.model.TemperatureReading;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DeviceGroupQueryActor extends AbstractBehavior<DeviceGroupQueryActor.Command> {

    interface Command {
    }

    private static enum GroupQueryTimeout implements Command {
        INSTANCE
    }

    static class WrappedRespondTemperature implements Command {
        final DeviceActor.RespondTemperature response;

        public WrappedRespondTemperature(final DeviceActor.RespondTemperature response) {
            this.response = response;
        }
    }

    private static class DeviceTerminated implements Command {

        final String deviceId;

        private DeviceTerminated(final String deviceId) {
            this.deviceId = deviceId;
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessageEquals(GroupQueryTimeout.INSTANCE, this::onGroupQueryTimeout)
                .onMessage(WrappedRespondTemperature.class, this::onRespondTemperature)
                .onMessage(DeviceTerminated.class, this::onDeviceTerminated)
                .build();
    }

    private final UUID requestId;
    private final ActorRef<DeviceGroupActor.RespondAllTemperatures> replyTo;

    private final Map<String, TemperatureReading> responseByDeviceId;
    private final Set<String> waitingDeviceIds;

    public static Behavior<DeviceGroupQueryActor.Command> create(final UUID requestID,
                                                                 final Map<String, ActorRef<DeviceActor.Command>> actorByDeviceId,
                                                                 final ActorRef<DeviceGroupActor.RespondAllTemperatures> replyTo,
                                                                 final Duration timeout) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers ->
                        new DeviceGroupQueryActor(requestID, actorByDeviceId, replyTo, timeout, context, timers)
                )
        );
    }

    DeviceGroupQueryActor(
            final UUID requestID,
            final Map<String, ActorRef<DeviceActor.Command>> actorByDeviceId,
            final ActorRef<DeviceGroupActor.RespondAllTemperatures> replyTo,
            final Duration timeout,
            final ActorContext<Command> context,
            final TimerScheduler<Command> timers) {

        super(context);
        this.requestId = requestID;
        this.replyTo = replyTo;
        this.responseByDeviceId = new HashMap<>();
        this.waitingDeviceIds = new HashSet<>(actorByDeviceId.keySet());

        // sends a `GroupQueryTimeout` message after the given timeout
        timers.startSingleTimer("device-group-query-temperatures", GroupQueryTimeout.INSTANCE, timeout);

        // it only converts from DeviceActor protocol to DeviceGroupQuery protocol
        // FIXME: could it be removed by making DeviceActor.RespondTemperature implement DeviceGroupQueryActor.Command?
        final ActorRef<DeviceActor.RespondTemperature> respondTemperatureAdapterActorRef =
                context.messageAdapter(DeviceActor.RespondTemperature.class, WrappedRespondTemperature::new);

        // queries each device for the temperature, watching for any termination
        actorByDeviceId.forEach((key, value) -> {
            context.watchWith(value, new DeviceTerminated(key));
            value.tell(new DeviceActor.ReadTemperature(UUID.randomUUID(), respondTemperatureAdapterActorRef));
        });
    }

    private Behavior<Command> onRespondTemperature(final WrappedRespondTemperature message) {

        if (!this.waitingDeviceIds.contains(message.response.deviceId)) {
            return respondWhenAllCollected();
        }

        final TemperatureReading reading = message.response.value
                .map(v -> (TemperatureReading) new Temperature(v))
                .orElse(TemperatureNotAvailable.INSTANCE);

        this.responseByDeviceId.put(message.response.deviceId, reading);
        this.waitingDeviceIds.remove(message.response.deviceId);
        return respondWhenAllCollected();
    }

    private Behavior<Command> onDeviceTerminated(final DeviceTerminated message) {

        if (!this.waitingDeviceIds.contains(message.deviceId)) {
            return respondWhenAllCollected();
        }

        this.responseByDeviceId.put(message.deviceId, DeviceNotAvailable.INSTANCE);
        this.waitingDeviceIds.remove(message.deviceId);

        return respondWhenAllCollected();
    }

    private Behavior<Command> onGroupQueryTimeout() {

        this.waitingDeviceIds.forEach(deviceId -> this.responseByDeviceId.put(deviceId, DeviceTimedOut.INSTANCE));
        this.waitingDeviceIds.clear();

        return respondWhenAllCollected();
    }

    private Behavior<Command> respondWhenAllCollected() {

        if (!this.waitingDeviceIds.isEmpty()) {
            return Behaviors.same();
        }

        this.replyTo.tell(new DeviceGroupActor.RespondAllTemperatures(this.requestId, this.responseByDeviceId));
        return Behaviors.stopped();
    }
}
