package com.lightbend.akka.tutorial;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DeviceManagerActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    private static final Duration defaultTemperaturesQueryDuration = Duration.ofMinutes(3);

    @Test
    public void testRegistration() {

        final String groupId = "group1";
        final String deviceId = "deviceId";

        final TestProbe<DeviceManagerActor.DeviceRegistered> deviceRegisteredProbe =
                testKit.createTestProbe(DeviceManagerActor.DeviceRegistered.class);

        final ActorRef<DeviceManagerActor.Command> deviceManagerActor = testKit.spawn(DeviceManagerActor.create(defaultTemperaturesQueryDuration));

        deviceManagerActor.tell(new DeviceManagerActor.RegisterDevice(groupId, deviceId, deviceRegisteredProbe.getRef()));
        assertNotNull(deviceRegisteredProbe.receiveMessage());
    }

    @Test
    public void testListDevices() {

        final UUID listRequestId = UUID.randomUUID();

        final String groupId = "groupId";
        final String device1Id = "device1";
        final String device2Id = "device2";

        final TestProbe<DeviceManagerActor.DeviceRegistered> deviceRegisteredTestProbe =
                testKit.createTestProbe(DeviceManagerActor.DeviceRegistered.class);

        final ActorRef<DeviceManagerActor.Command> deviceManagerActor = testKit.spawn(DeviceManagerActor.create(defaultTemperaturesQueryDuration));

        deviceManagerActor.tell(new DeviceManagerActor.RegisterDevice(groupId, device1Id, deviceRegisteredTestProbe.getRef()));
        deviceRegisteredTestProbe.receiveMessage();

        deviceManagerActor.tell(new DeviceManagerActor.RegisterDevice(groupId, device2Id, deviceRegisteredTestProbe.getRef()));
        deviceRegisteredTestProbe.receiveMessage();

        final TestProbe<DeviceGroupActor.ReplyDeviceList> deviceListTestProbe =
                testKit.createTestProbe(DeviceGroupActor.ReplyDeviceList.class);

        deviceManagerActor.tell(new DeviceGroupActor.RequestDeviceList(listRequestId, groupId, deviceListTestProbe.getRef()));

        final DeviceGroupActor.ReplyDeviceList deviceList = deviceListTestProbe.receiveMessage();
        assertEquals(listRequestId, deviceList.requestId);
        assertEquals(Stream.of(device1Id, device2Id).collect(toSet()), deviceList.deviceIds);
    }

}
