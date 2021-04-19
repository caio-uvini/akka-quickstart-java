package com.lightbend.akka.tutorial;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.lightbend.akka.tutorial.model.Temperature;
import com.lightbend.akka.tutorial.model.TemperatureNotAvailable;
import com.lightbend.akka.tutorial.model.TemperatureReading;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DeviceGroupActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    private static final Duration defaultTemperaturesQueryDuration = Duration.ofMinutes(3);

    @Test
    public void testRegistrationWhenRegisteringRepeatedDevices() {

        final String groupId = "group1";
        final String deviceId = "deviceId";

        final TestProbe<DeviceManagerActor.DeviceRegistered> deviceRegisteredProbe =
                testKit.createTestProbe(DeviceManagerActor.DeviceRegistered.class);

        final ActorRef<DeviceGroupActor.Command> deviceGroupActor = testKit.spawn(DeviceGroupActor.create(groupId, defaultTemperaturesQueryDuration));

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

        final ActorRef<DeviceGroupActor.Command> deviceGroupActor = testKit.spawn(DeviceGroupActor.create(groupId, defaultTemperaturesQueryDuration));

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

        final ActorRef<DeviceGroupActor.Command> deviceGroupActor = testKit.spawn(DeviceGroupActor.create(groupId, defaultTemperaturesQueryDuration));

        deviceGroupActor.tell(new DeviceManagerActor.RegisterDevice(anotherGroupId, deviceId, deviceRegisteredProbe.getRef()));
        deviceRegisteredProbe.expectNoMessage();
    }

    @Test
    public void testListActiveDevices() {

        final UUID listRequestId = UUID.randomUUID();

        final String groupId = "groupId";
        final String device1Id = "device1";
        final String device2Id = "device2";

        final TestProbe<DeviceManagerActor.DeviceRegistered> deviceRegisteredTestProbe =
                testKit.createTestProbe(DeviceManagerActor.DeviceRegistered.class);

        final ActorRef<DeviceGroupActor.Command> deviceGroupActor = testKit.spawn(DeviceGroupActor.create(groupId, defaultTemperaturesQueryDuration));

        deviceGroupActor.tell(new DeviceManagerActor.RegisterDevice(groupId, device1Id, deviceRegisteredTestProbe.getRef()));
        deviceRegisteredTestProbe.receiveMessage();

        deviceGroupActor.tell(new DeviceManagerActor.RegisterDevice(groupId, device2Id, deviceRegisteredTestProbe.getRef()));
        deviceRegisteredTestProbe.receiveMessage();

        final TestProbe<DeviceGroupActor.ReplyDeviceList> deviceListTestProbe =
                testKit.createTestProbe(DeviceGroupActor.ReplyDeviceList.class);

        deviceGroupActor.tell(new DeviceGroupActor.RequestDeviceList(listRequestId, groupId, deviceListTestProbe.getRef()));

        final DeviceGroupActor.ReplyDeviceList deviceList = deviceListTestProbe.receiveMessage();
        assertEquals(listRequestId, deviceList.requestId);
        assertEquals(Stream.of(device1Id, device2Id).collect(toSet()), deviceList.deviceIds);
    }

    @Test
    public void testListActiveDevicesAfterOneShutsDown() {

        final UUID listRequestId = UUID.randomUUID();
        final UUID updatedListRequestId = UUID.randomUUID();

        final String groupId = "groupId";
        final String device1Id = "device1";
        final String device2Id = "device2";

        final TestProbe<DeviceManagerActor.DeviceRegistered> deviceRegisteredTestProbe =
                testKit.createTestProbe(DeviceManagerActor.DeviceRegistered.class);

        final ActorRef<DeviceGroupActor.Command> deviceGroupActor = testKit.spawn(DeviceGroupActor.create(groupId, defaultTemperaturesQueryDuration));

        deviceGroupActor.tell(new DeviceManagerActor.RegisterDevice(groupId, device1Id, deviceRegisteredTestProbe.getRef()));
        final ActorRef<DeviceActor.Command> device1Actor = deviceRegisteredTestProbe.receiveMessage().device;

        deviceGroupActor.tell(new DeviceManagerActor.RegisterDevice(groupId, device2Id, deviceRegisteredTestProbe.getRef()));
        final ActorRef<DeviceActor.Command> device2Actor = deviceRegisteredTestProbe.receiveMessage().device;

        // lists both devices

        final TestProbe<DeviceGroupActor.ReplyDeviceList> deviceListTestProbe =
                testKit.createTestProbe(DeviceGroupActor.ReplyDeviceList.class);

        deviceGroupActor.tell(new DeviceGroupActor.RequestDeviceList(listRequestId, groupId, deviceListTestProbe.getRef()));

        final DeviceGroupActor.ReplyDeviceList deviceList = deviceListTestProbe.receiveMessage();
        assertEquals(listRequestId, deviceList.requestId);
        assertEquals(Stream.of(device1Id, device2Id).collect(toSet()), deviceList.deviceIds);

        // shuts down device2 and list again, only device1 must be returned

        device2Actor.tell(DeviceActor.Passivate.INSTANCE);
        deviceRegisteredTestProbe.expectTerminated(device2Actor, deviceRegisteredTestProbe.getRemainingOrDefault());

        deviceRegisteredTestProbe.awaitAssert(() -> {
            deviceGroupActor.tell(new DeviceGroupActor.RequestDeviceList(updatedListRequestId, groupId, deviceListTestProbe.getRef()));

            final DeviceGroupActor.ReplyDeviceList updatedList = deviceListTestProbe.receiveMessage();
            assertEquals(updatedListRequestId, updatedList.requestId);
            assertEquals(Stream.of(device1Id).collect(toSet()), updatedList.deviceIds);
            return null;
        });
    }

    @Test
    public void testCollectTemperaturesFromAllActiveDevices() {

        final String groupId = "group";
        final String device1Id = "device1";
        final String device2Id = "device2";
        final String device3Id = "device3";

        final UUID device1RecordTemperatureRequestId = UUID.randomUUID();
        final UUID device2RecordTemperatureRequestId = UUID.randomUUID();
        final UUID requestAllTemperaturesRequestId = UUID.randomUUID();

        TestProbe<DeviceManagerActor.DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceManagerActor.DeviceRegistered.class);
        ActorRef<DeviceGroupActor.Command> groupActor = testKit.spawn(DeviceGroupActor.create(groupId, defaultTemperaturesQueryDuration));

        groupActor.tell(new DeviceManagerActor.RegisterDevice(groupId, device1Id, registeredProbe.getRef()));
        ActorRef<DeviceActor.Command> deviceActor1 = registeredProbe.receiveMessage().device;

        groupActor.tell(new DeviceManagerActor.RegisterDevice(groupId, device2Id, registeredProbe.getRef()));
        ActorRef<DeviceActor.Command> deviceActor2 = registeredProbe.receiveMessage().device;

        groupActor.tell(new DeviceManagerActor.RegisterDevice(groupId, device3Id, registeredProbe.getRef()));
        ActorRef<DeviceActor.Command> deviceActor3 = registeredProbe.receiveMessage().device;

        // Check that the device actors are working
        TestProbe<DeviceActor.RecordTemperatureCompleted> recordProbe =
                testKit.createTestProbe(DeviceActor.RecordTemperatureCompleted.class);

        deviceActor1.tell(new DeviceActor.RecordTemperature(device1RecordTemperatureRequestId, 1.0, recordProbe.getRef()));
        assertEquals(device1RecordTemperatureRequestId, recordProbe.receiveMessage().requestId);

        deviceActor2.tell(new DeviceActor.RecordTemperature(device2RecordTemperatureRequestId, 2.0, recordProbe.getRef()));
        assertEquals(device2RecordTemperatureRequestId, recordProbe.receiveMessage().requestId);

        // No temperature for device 3

        TestProbe<DeviceGroupActor.RespondAllTemperatures> allTempProbe =
                testKit.createTestProbe(DeviceGroupActor.RespondAllTemperatures.class);

        groupActor.tell(new DeviceGroupActor.RequestAllTemperatures(requestAllTemperaturesRequestId, groupId, allTempProbe.getRef()));

        DeviceGroupActor.RespondAllTemperatures response = allTempProbe.receiveMessage();
        assertEquals(requestAllTemperaturesRequestId, response.requestId);

        Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put(device1Id, new Temperature(1.0));
        expectedTemperatures.put(device2Id, new Temperature(2.0));
        expectedTemperatures.put(device3Id, TemperatureNotAvailable.INSTANCE);

        assertEquals(expectedTemperatures, response.responseByDeviceId);
    }
}
