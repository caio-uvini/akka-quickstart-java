package com.lightbend.akka.tutorial;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.Signal;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

public class DeviceManagerActor extends AbstractBehavior<DeviceManagerActor.Command> {

    interface Command {
    }

    public static final class RegisterDevice implements DeviceManagerActor.Command, DeviceGroupActor.Command {

        final String groupId;
        final String deviceId;
        final ActorRef<DeviceRegistered> replyTo;

        public RegisterDevice(final String groupId, final String deviceId, final ActorRef<DeviceRegistered> replyTo) {
            this.groupId = groupId;
            this.deviceId = deviceId;
            this.replyTo = replyTo;
        }
    }

    public static final class DeviceRegistered implements DeviceActor.Command {

        final ActorRef<DeviceActor.Command> device;

        public DeviceRegistered(final ActorRef<DeviceActor.Command> device) {
            this.device = device;
        }

    }

    public static final class DeviceGroupTerminated implements DeviceManagerActor.Command {

        final String groupId;

        public DeviceGroupTerminated(final String groupId) {
            this.groupId = groupId;
        }
    }

    public static Behavior<DeviceManagerActor.Command> create(final Duration queryGroupTemperaturesDuration) {
        return Behaviors.setup(context -> new DeviceManagerActor(context, queryGroupTemperaturesDuration));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(DeviceManagerActor.RegisterDevice.class, this::onRegisterDevice)
                .onMessage(DeviceGroupActor.RequestDeviceList.class, this::onRequestDeviceList)
                .onMessage(DeviceGroupActor.RequestAllTemperatures.class, this::onRequestAllTemperatures)
                .onMessage(DeviceManagerActor.DeviceGroupTerminated.class, this::onDeviceGroupTerminated)
                .onSignal(PostStop.class, this::onPostStop)
                .build();
    }

    private final Map<String, ActorRef<DeviceGroupActor.Command>> deviceGroupActorById;

    private final Duration queryGroupTemperaturesDuration;

    private DeviceManagerActor(final ActorContext<DeviceManagerActor.Command> context, final Duration queryGroupTemperaturesDuration) {
        super(context);
        this.queryGroupTemperaturesDuration = queryGroupTemperaturesDuration;
        this.deviceGroupActorById = new HashMap<>();
        context.getLog().info("DeviceManagerActor started!");
    }

    private Behavior<DeviceManagerActor.Command> onRegisterDevice(final DeviceManagerActor.RegisterDevice message) {

        if (!this.deviceGroupActorById.containsKey(message.groupId)) {

            getContext().getLog().info("Creating device group for {}!", message.groupId);

            final ActorRef<DeviceGroupActor.Command> deviceGroupRef =
                    getContext().spawn(DeviceGroupActor.create(message.groupId, queryGroupTemperaturesDuration), "group-" + message.groupId);

            getContext().watchWith(deviceGroupRef, new DeviceGroupTerminated(message.groupId));

            this.deviceGroupActorById.put(message.groupId, deviceGroupRef);
        }

        this.deviceGroupActorById.get(message.groupId).tell(message);
        return Behaviors.same();
    }

    private Behavior<DeviceManagerActor.Command> onRequestDeviceList(final DeviceGroupActor.RequestDeviceList message) {

        if (!this.deviceGroupActorById.containsKey(message.groupId)) {
            message.replyTo.tell(new DeviceGroupActor.ReplyDeviceList(message.requestId, emptySet()));
            return Behaviors.same();
        }

        this.deviceGroupActorById.get(message.groupId).tell(message);
        return Behaviors.same();
    }

    private Behavior<DeviceManagerActor.Command> onRequestAllTemperatures(final DeviceGroupActor.RequestAllTemperatures message) {

        if (!this.deviceGroupActorById.containsKey(message.groupId)) {
            message.replyTo.tell(new DeviceGroupActor.RespondAllTemperatures(message.requestId, emptyMap()));
            return Behaviors.same();
        }

        this.deviceGroupActorById.get(message.groupId).tell(message);
        return Behaviors.same();
    }

    private Behavior<DeviceManagerActor.Command> onDeviceGroupTerminated(final DeviceManagerActor.DeviceGroupTerminated message) {
        getContext().getLog().info("Device group actor for {} has been terminated", message.groupId);
        this.deviceGroupActorById.remove(message.groupId);
        return Behaviors.same();
    }

    private Behavior<DeviceManagerActor.Command> onPostStop(final Signal signal) {
        getContext().getLog().info("DeviceManager stopped");
        return Behaviors.same();
    }


}
