package com.lightbend.akka.tutorial;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.lightbend.akka.tutorial.model.DeviceNotAvailable;
import com.lightbend.akka.tutorial.model.DeviceTimedOut;
import com.lightbend.akka.tutorial.model.Temperature;
import com.lightbend.akka.tutorial.model.TemperatureNotAvailable;
import com.lightbend.akka.tutorial.model.TemperatureReading;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class DeviceGroupQueryActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testReturnTemperatureValueForWorkingDevices() {

        final UUID queryRequestId = UUID.randomUUID();
        final String device1Id = "device1";
        final String device2Id = "device2";

        TestProbe<DeviceGroupActor.RespondAllTemperatures> requester =
                testKit.createTestProbe(DeviceGroupActor.RespondAllTemperatures.class);
        TestProbe<DeviceActor.Command> device1 = testKit.createTestProbe(DeviceActor.Command.class);
        TestProbe<DeviceActor.Command> device2 = testKit.createTestProbe(DeviceActor.Command.class);

        Map<String, ActorRef<DeviceActor.Command>> deviceIdToActor = new HashMap<>();
        deviceIdToActor.put(device1Id, device1.getRef());
        deviceIdToActor.put(device2Id, device2.getRef());

        ActorRef<DeviceGroupQueryActor.Command> queryActor = testKit.spawn(
                DeviceGroupQueryActor.create(
                        queryRequestId, deviceIdToActor, requester.getRef(), Duration.ofSeconds(3)));

        device1.expectMessageClass(DeviceActor.ReadTemperature.class);
        device2.expectMessageClass(DeviceActor.ReadTemperature.class);

        queryActor.tell(
                new DeviceGroupQueryActor.WrappedRespondTemperature(
                        new DeviceActor.RespondTemperature(UUID.randomUUID(), device1Id, Optional.of(1.0))));

        queryActor.tell(
                new DeviceGroupQueryActor.WrappedRespondTemperature(
                        new DeviceActor.RespondTemperature(UUID.randomUUID(), device2Id, Optional.of(2.0))));

        DeviceGroupActor.RespondAllTemperatures response = requester.receiveMessage();
        assertEquals(queryRequestId, response.requestId);

        Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put(device1Id, new Temperature(1.0));
        expectedTemperatures.put(device2Id, new Temperature(2.0));

        assertEquals(expectedTemperatures, response.responseByDeviceId);
    }

    @Test
    public void testReturnTemperatureNotAvailableForDevicesWithNoReadings() {

        final UUID queryRequestId = UUID.randomUUID();
        final String device1Id = "device1";
        final String device2Id = "device2";

        TestProbe<DeviceGroupActor.RespondAllTemperatures> requester =
                testKit.createTestProbe(DeviceGroupActor.RespondAllTemperatures.class);
        TestProbe<DeviceActor.Command> device1 = testKit.createTestProbe(DeviceActor.Command.class);
        TestProbe<DeviceActor.Command> device2 = testKit.createTestProbe(DeviceActor.Command.class);

        Map<String, ActorRef<DeviceActor.Command>> deviceIdToActor = new HashMap<>();
        deviceIdToActor.put(device1Id, device1.getRef());
        deviceIdToActor.put(device2Id, device2.getRef());

        ActorRef<DeviceGroupQueryActor.Command> queryActor =
                testKit.spawn(
                        DeviceGroupQueryActor.create(
                                queryRequestId, deviceIdToActor, requester.getRef(), Duration.ofSeconds(3)));

        queryActor.tell(
                new DeviceGroupQueryActor.WrappedRespondTemperature(
                        new DeviceActor.RespondTemperature(UUID.randomUUID(), device1Id, Optional.empty())));

        queryActor.tell(
                new DeviceGroupQueryActor.WrappedRespondTemperature(
                        new DeviceActor.RespondTemperature(UUID.randomUUID(), device2Id, Optional.of(2.0))));

        DeviceGroupActor.RespondAllTemperatures response = requester.receiveMessage();
        assertEquals(queryRequestId, response.requestId);

        Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put(device1Id, TemperatureNotAvailable.INSTANCE);
        expectedTemperatures.put(device2Id, new Temperature(2.0));

        assertEquals(expectedTemperatures, response.responseByDeviceId);
    }

    @Test
    public void testReturnDeviceNotAvailableIfDeviceStopsBeforeAnswering() {

        final UUID queryRequestId = UUID.randomUUID();
        final String device1Id = "device1";
        final String device2Id = "device2";

        TestProbe<DeviceGroupActor.RespondAllTemperatures> requester =
                testKit.createTestProbe(DeviceGroupActor.RespondAllTemperatures.class);
        TestProbe<DeviceActor.Command> device1 = testKit.createTestProbe(DeviceActor.Command.class);
        TestProbe<DeviceActor.Command> device2 = testKit.createTestProbe(DeviceActor.Command.class);

        Map<String, ActorRef<DeviceActor.Command>> deviceIdToActor = new HashMap<>();
        deviceIdToActor.put(device1Id, device1.getRef());
        deviceIdToActor.put(device2Id, device2.getRef());

        ActorRef<DeviceGroupQueryActor.Command> queryActor =
                testKit.spawn(
                        DeviceGroupQueryActor.create(
                                queryRequestId, deviceIdToActor, requester.getRef(), Duration.ofSeconds(3)));

        queryActor.tell(
                new DeviceGroupQueryActor.WrappedRespondTemperature(
                        new DeviceActor.RespondTemperature(UUID.randomUUID(), device1Id, Optional.of(1.0))));

        device2.stop();

        DeviceGroupActor.RespondAllTemperatures response = requester.receiveMessage();
        assertEquals(queryRequestId, response.requestId);

        Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put(device1Id, new Temperature(1.0));
        expectedTemperatures.put(device2Id, DeviceNotAvailable.INSTANCE);

        assertEquals(expectedTemperatures, response.responseByDeviceId);
    }

    @Test
    public void testReturnTemperatureReadingEvenIfDeviceStopsAfterAnswering() {

        final UUID queryRequestId = UUID.randomUUID();
        final String device1Id = "device1";
        final String device2Id = "device2";

        TestProbe<DeviceGroupActor.RespondAllTemperatures> requester =
                testKit.createTestProbe(DeviceGroupActor.RespondAllTemperatures.class);
        TestProbe<DeviceActor.Command> device1 = testKit.createTestProbe(DeviceActor.Command.class);
        TestProbe<DeviceActor.Command> device2 = testKit.createTestProbe(DeviceActor.Command.class);

        Map<String, ActorRef<DeviceActor.Command>> deviceIdToActor = new HashMap<>();
        deviceIdToActor.put(device1Id, device1.getRef());
        deviceIdToActor.put(device2Id, device2.getRef());

        ActorRef<DeviceGroupQueryActor.Command> queryActor =
                testKit.spawn(
                        DeviceGroupQueryActor.create(
                                queryRequestId, deviceIdToActor, requester.getRef(), Duration.ofSeconds(3)));

        queryActor.tell(
                new DeviceGroupQueryActor.WrappedRespondTemperature(
                        new DeviceActor.RespondTemperature(UUID.randomUUID(), device1Id, Optional.of(1.0))));

        queryActor.tell(
                new DeviceGroupQueryActor.WrappedRespondTemperature(
                        new DeviceActor.RespondTemperature(UUID.randomUUID(), device2Id, Optional.of(2.0))));

        device2.stop();

        DeviceGroupActor.RespondAllTemperatures response = requester.receiveMessage();
        assertEquals(queryRequestId, response.requestId);

        Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put(device1Id, new Temperature(1.0));
        expectedTemperatures.put(device2Id, new Temperature(2.0));

        assertEquals(expectedTemperatures, response.responseByDeviceId);
    }

    @Test
    public void testReturnDeviceTimedOutIfDeviceDoesNotAnswerInTime() {

        final UUID queryRequestId = UUID.randomUUID();
        final String device1Id = "device1";
        final String device2Id = "device2";

        TestProbe<DeviceGroupActor.RespondAllTemperatures> requester =
                testKit.createTestProbe(DeviceGroupActor.RespondAllTemperatures.class);
        TestProbe<DeviceActor.Command> device1 = testKit.createTestProbe(DeviceActor.Command.class);
        TestProbe<DeviceActor.Command> device2 = testKit.createTestProbe(DeviceActor.Command.class);

        Map<String, ActorRef<DeviceActor.Command>> deviceIdToActor = new HashMap<>();
        deviceIdToActor.put(device1Id, device1.getRef());
        deviceIdToActor.put(device2Id, device2.getRef());

        ActorRef<DeviceGroupQueryActor.Command> queryActor =
                testKit.spawn(
                        DeviceGroupQueryActor.create(
                                queryRequestId, deviceIdToActor, requester.getRef(), Duration.ofMillis(200)));

        queryActor.tell(
                new DeviceGroupQueryActor.WrappedRespondTemperature(
                        new DeviceActor.RespondTemperature(UUID.randomUUID(), device1Id, Optional.of(1.0))));

        // no reply from device2

        DeviceGroupActor.RespondAllTemperatures response = requester.receiveMessage();
        assertEquals(queryRequestId, response.requestId);

        Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put(device1Id, new Temperature(1.0));
        expectedTemperatures.put(device2Id, DeviceTimedOut.INSTANCE);

        assertEquals(expectedTemperatures, response.responseByDeviceId);
    }

}
