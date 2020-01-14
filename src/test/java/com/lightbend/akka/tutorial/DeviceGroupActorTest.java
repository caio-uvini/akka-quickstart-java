package com.lightbend.akka.tutorial;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DeviceGroupActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testRegistrationWhenRegisteringRepeatedDevices() {

        final String groupId = "group1";
        final String deviceId = "deviceId";

        final TestProbe<DeviceManagerActor.DeviceRegistered> deviceRegisteredProbe =
                testKit.createTestProbe(DeviceManagerActor.DeviceRegistered.class);

        final ActorRef<DeviceGroupActor.Command> deviceGroupActor = testKit.spawn(DeviceGroupActor.create(groupId));

        deviceGroupActor.tell(new DeviceManagerActor.RegisterDevice(groupId, deviceId, deviceRegisteredProbe.getRef()));
        final DeviceManagerActor.DeviceRegistered device1RegisteredResponse = deviceRegisteredProbe.receiveMessage();

        deviceGroupActor.tell(new DeviceManagerActor.RegisterDevice(groupId, deviceId, deviceRegisteredProbe.getRef()));
        final DeviceManagerActor.DeviceRegistered device2RegisteredResponse = deviceRegisteredProbe.receiveMessage();

        assertEquals(device1RegisteredResponse.device, device2RegisteredResponse.device);
    }

    @Test
    public void testRegistrationWhenRegisteringDistinctDevices() {

        final String groupId = "group1";
        final String device1Id = "deviceId1";
        final String device2Id = "deviceId2";

        final TestProbe<DeviceManagerActor.DeviceRegistered> deviceRegisteredProbe =
                testKit.createTestProbe(DeviceManagerActor.DeviceRegistered.class);

        final ActorRef<DeviceGroupActor.Command> deviceGroupActor = testKit.spawn(DeviceGroupActor.create(groupId));

        deviceGroupActor.tell(new DeviceManagerActor.RegisterDevice(groupId, device1Id, deviceRegisteredProbe.getRef()));
        final DeviceManagerActor.DeviceRegistered device1RegisteredResponse = deviceRegisteredProbe.receiveMessage();

        deviceGroupActor.tell(new DeviceManagerActor.RegisterDevice(groupId, device2Id, deviceRegisteredProbe.getRef()));
        final DeviceManagerActor.DeviceRegistered device2RegisteredResponse = deviceRegisteredProbe.receiveMessage();

        assertNotEquals(device1RegisteredResponse.device, device2RegisteredResponse.device);
    }

    @Test
    public void testRegistrationWhenRegisteringDevicesOfAnotherGroup() {

        final String groupId = "group1";
        final String anotherGroupId = groupId + "another";
        final String deviceId = "deviceId1";

        final TestProbe<DeviceManagerActor.DeviceRegistered> deviceRegisteredProbe =
                testKit.createTestProbe(DeviceManagerActor.DeviceRegistered.class);

        final ActorRef<DeviceGroupActor.Command> deviceGroupActor = testKit.spawn(DeviceGroupActor.create(groupId));

        deviceGroupActor.tell(new DeviceManagerActor.RegisterDevice(anotherGroupId, deviceId, deviceRegisteredProbe.getRef()));
        deviceRegisteredProbe.expectNoMessage();
    }

}
