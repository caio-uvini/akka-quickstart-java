package com.lightbend.akka.tutorial;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.Signal;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.Optional;
import java.util.UUID;

public class DeviceActor extends AbstractBehavior<DeviceActor.Command> {

    interface Command {
    }

    public static final class RecordTemperature implements Command {

        final UUID requestId;
        final double value;
        final ActorRef<RecordTemperatureCompleted> replyTo;

        public RecordTemperature(final UUID requestId, final double value, final ActorRef<RecordTemperatureCompleted> replyTo) {
            this.requestId = requestId;
            this.value = value;
            this.replyTo = replyTo;
        }
    }

    public static final class RecordTemperatureCompleted implements Command {

        final UUID requestId;

        public RecordTemperatureCompleted(final UUID requestId) {
            this.requestId = requestId;
        }
    }

    public static final class ReadTemperature implements Command {

        final UUID requestId;
        final ActorRef<RespondTemperature> replyTo;

        public ReadTemperature(final UUID requestId, final ActorRef<RespondTemperature> replyTo) {
            this.requestId = requestId;
            this.replyTo = replyTo;
        }
    }

    public static final class RespondTemperature implements Command {

        final UUID requestId;
        final String deviceId;
        final Optional<Double> value;

        public RespondTemperature(final UUID requestId, final String deviceId, final Optional<Double> value) {
            this.requestId = requestId;
            this.deviceId = deviceId;
            this.value = value;
        }
    }

    public static enum Passivate implements Command {
        INSTANCE
    }

    private final String groupId;
    private final String deviceId;

    private Optional<Double> lastTemperatureReading = Optional.empty();

    public static Behavior<Command> create(final String groupId, final String deviceId) {
        return Behaviors.setup(context -> new DeviceActor(context, groupId, deviceId));
    }

    private DeviceActor(final ActorContext<Command> context, final String groupId, final String deviceId) {
        super(context);
        this.groupId = groupId;
        this.deviceId = deviceId;

        context.getLog().info("Device actor {}-{} started!", groupId, deviceId);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(RecordTemperature.class, this::onRecordTemperature)
                .onMessage(ReadTemperature.class, this::onReadTemperature)
                .onMessageEquals(Passivate.INSTANCE, this::onPassivate)
                .onSignal(PostStop.class, this::onPostStop)
                .build();
    }

    private Behavior<Command> onPassivate() {
        getContext().getLog().info("Stopping device actor {} due to passivation message.", this.deviceId);
        return Behaviors.stopped();
    }

    private Behavior<Command> onRecordTemperature(final RecordTemperature message) {

        getContext().getLog().info("Recorded temperature reading {} for request id {}.", message.value, message.requestId);

        this.lastTemperatureReading = Optional.of(message.value);
        message.replyTo.tell(new RecordTemperatureCompleted(message.requestId));

        return Behaviors.same();
    }

    private Behavior<Command> onReadTemperature(final ReadTemperature message) {
        message.replyTo.tell(new RespondTemperature(message.requestId, this.deviceId, lastTemperatureReading));
        return Behaviors.same();
    }

    private Behavior<Command> onPostStop(final Signal signal) {
        getContext().getLog().info("Device actor {}-{} stopped!", groupId, deviceId);
        return Behaviors.same();
    }

}
