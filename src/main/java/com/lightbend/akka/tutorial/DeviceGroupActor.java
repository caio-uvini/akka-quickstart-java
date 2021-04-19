package com.lightbend.akka.tutorial;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.Signal;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.lightbend.akka.tutorial.model.TemperatureReading;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DeviceGroupActor extends AbstractBehavior<DeviceGroupActor.Command> {

    interface Command {
    }

    public static class DeviceTerminated implements Command {
        final ActorRef<DeviceActor.Command> device;
        final String groupId;
        final String deviceId;

        DeviceTerminated(final ActorRef<DeviceActor.Command> device, final String groupId, final String deviceId) {
            this.device = device;
            this.groupId = groupId;
            this.deviceId = deviceId;
        }
    }

    public static class RequestDeviceList implements Command, DeviceManagerActor.Command {

        final UUID requestId;
        final String groupId;
        final ActorRef<ReplyDeviceList> replyTo;

        RequestDeviceList(final UUID requestId, final String groupId, final ActorRef<ReplyDeviceList> replyTo) {
            this.requestId = requestId;
            this.groupId = groupId;
            this.replyTo = replyTo;
        }
    }

    public static class ReplyDeviceList implements Command, DeviceManagerActor.Command {

        final UUID requestId;
        final Set<String> deviceIds;

        ReplyDeviceList(final UUID requestId, final Set<String> deviceIds) {
            this.requestId = requestId;
            this.deviceIds = deviceIds;
        }
    }

    public static final class RequestAllTemperatures implements DeviceGroupActor.Command, DeviceManagerActor.Command {

        final UUID requestId;
        final String groupId;
        final ActorRef<DeviceGroupActor.RespondAllTemperatures> replyTo;

        public RequestAllTemperatures(final UUID requestId, final String groupId, final ActorRef<RespondAllTemperatures> replyTo) {
            this.requestId = requestId;
            this.groupId = groupId;
            this.replyTo = replyTo;
        }
    }

    public static final class RespondAllTemperatures implements DeviceGroupActor.Command {

        final UUID requestId;
        final Map<String, TemperatureReading> responseByDeviceId;

        public RespondAllTemperatures(final UUID requestId, final Map<String, TemperatureReading> responseByDeviceId) {
            this.requestId = requestId;
            this.responseByDeviceId = responseByDeviceId;
        }
    }

    private final String groupId;
    private final Duration queryTimeout;
    private final Map<String, ActorRef<DeviceActor.Command>> deviceActorById;

    public static Behavior<Command> create(final String groupId, final Duration queryTimeout) {
        return Behaviors.setup(context -> new DeviceGroupActor(context, groupId, queryTimeout));
    }

    private DeviceGroupActor(final ActorContext<Command> context, final String groupId, final Duration queryTimeout) {
        super(context);
        this.groupId = groupId;
        this.queryTimeout = queryTimeout;
        this.deviceActorById = new HashMap<>();

        context.getLog().info("DeviceGroup {} started!", groupId);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(DeviceManagerActor.RegisterDevice.class, this::onRegisterDevice)
                .onMessage(RequestDeviceList.class, this::onRequestDeviceList)
                .onMessage(RequestAllTemperatures.class, this::onRequestAllTemperatures)
                .onMessage(DeviceTerminated.class, this::onTerminate)
                .onSignal(PostStop.class, this::onPostStop)
                .build();
    }

    private Behavior<Command> onRegisterDevice(final DeviceManagerActor.RegisterDevice message) {

        if (!this.groupId.equals(message.groupId)) {
            getContext().getLog().info("Ignoring RegisterDevice request for group {}. " +
                    "This actor handles only group {}!", message.groupId, this.groupId);

            return Behaviors.same();
        }

        if (this.deviceActorById.containsKey(message.deviceId)) {
            message.replyTo.tell(new DeviceManagerActor.DeviceRegistered(deviceActorById.get(message.deviceId)));
            return Behaviors.same();
        }

        getContext().getLog().info("Creating device actor for {}!", message.deviceId);

        final ActorRef<DeviceActor.Command> deviceActor = getContext()
                .spawn(DeviceActor.create(this.groupId, message.deviceId), "device-" + message.deviceId);
        getContext().watchWith(deviceActor, new DeviceTerminated(deviceActor, this.groupId, message.deviceId));

        deviceActorById.put(message.deviceId, deviceActor);
        message.replyTo.tell(new DeviceManagerActor.DeviceRegistered(deviceActor));

        return Behaviors.same();
    }

    private Behavior<Command> onRequestDeviceList(final RequestDeviceList message) {

        if (!this.groupId.equals(message.groupId)) {
            getContext().getLog().info("Cannot list devices from group {}. This actor only handles group {}.",
                    message.groupId, this.groupId);

            return Behaviors.same();
        }

        message.replyTo.tell(new ReplyDeviceList(message.requestId, this.deviceActorById.keySet()));
        return Behaviors.same();
    }

    private Behavior<Command> onRequestAllTemperatures(final RequestAllTemperatures message) {

        if (!this.groupId.equals(message.groupId)) {
            getContext().getLog().info("Cannot request temperatures of group {}. Current actor cares only about " +
                    "group {}.", message.groupId, this.groupId);
            return Behaviors.same();
        }

        final Map<String, ActorRef<DeviceActor.Command>> deviceActorById = new HashMap<>(this.deviceActorById);

        getContext().spawnAnonymous(
                DeviceGroupQueryActor.create(message.requestId, deviceActorById, message.replyTo, queryTimeout)
        );

        return Behaviors.same();
    }

    private Behavior<Command> onTerminate(final DeviceTerminated message) {

        if (!this.groupId.equals(message.groupId)) {
            getContext().getLog().info("Device {} belongs to group {}. Current actor cares only about group {}. " +
                    "Ignoring termination signal.", message.device, message.groupId, this.groupId);

            return Behaviors.same();
        }

        final boolean removed = this.deviceActorById.remove(message.deviceId) != null;
        if (removed) {
            getContext().getLog().info("Device {} terminated! No longer part of group {}", message.device, this.groupId);
        } else {
            getContext().getLog().info("Device {} already not being tracked in group {}. " +
                    "Ignoring termination signal.", message.device, this.groupId);
        }

        return Behaviors.same();
    }

    private Behavior<Command> onPostStop(final Signal signal) {
        getContext().getLog().info("DeviceGroupActor {} stopped!", groupId);
        return Behaviors.same();
    }
}
