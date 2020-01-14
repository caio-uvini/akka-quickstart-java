package com.lightbend.akka.tutorial;

import akka.actor.typed.ActorRef;

public class DeviceManagerActor {

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

    public static final class DeviceRegistered implements DeviceActor.Command{

        final ActorRef<DeviceActor.Command> device;

        public DeviceRegistered(final ActorRef<DeviceActor.Command> device) {
            this.device = device;
        }

    }


}
