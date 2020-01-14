package com.lightbend.akka.tutorial;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static com.lightbend.akka.tutorial.DeviceActor.create;
import static org.junit.Assert.assertEquals;

public class DeviceActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {

        // given
        final UUID requestId = UUID.randomUUID();
        final TestProbe<DeviceActor.RespondTemperature> probe = testKit.createTestProbe(DeviceActor.RespondTemperature.class);
        final ActorRef<DeviceActor.Command> deviceActorRef = testKit.spawn(create("group", "device"));

        // when
        deviceActorRef.tell(new DeviceActor.ReadTemperature(requestId, probe.getRef()));

        // then
        final DeviceActor.RespondTemperature response = probe.receiveMessage();

        assertEquals(requestId, response.requestId);
        assertEquals(Optional.empty(), response.value);
    }

    @Test
    public void testReadTemperatureWhenTemperatureIsKnown() {

        // given
        final UUID recordRequestId = UUID.randomUUID();
        final UUID readRequestId = UUID.randomUUID();
        final double temperature = 10.5;

        final TestProbe<DeviceActor.RespondTemperature> readProbe = testKit.createTestProbe(DeviceActor.RespondTemperature.class);
        final TestProbe<DeviceActor.RecordTemperatureCompleted> recordProbe = testKit.createTestProbe(DeviceActor.RecordTemperatureCompleted.class);
        final ActorRef<DeviceActor.Command> deviceActorRef = testKit.spawn(create("group", "device"));

        // when
        deviceActorRef.tell(new DeviceActor.RecordTemperature(recordRequestId, temperature, recordProbe.getRef()));
        deviceActorRef.tell(new DeviceActor.ReadTemperature(readRequestId, readProbe.getRef()));

        // then
        final DeviceActor.RecordTemperatureCompleted recordResponse = recordProbe.receiveMessage();
        assertEquals(recordRequestId, recordResponse.requestId);

        final DeviceActor.RespondTemperature readResponse = readProbe.receiveMessage();
        assertEquals(readRequestId, readResponse.requestId);
        assertEquals(Optional.of(temperature), readResponse.value);
    }

    @Test
    public void testRecordTemperature() {

        // given
        final UUID requestId = UUID.randomUUID();
        final TestProbe<DeviceActor.RecordTemperatureCompleted> probe = testKit.createTestProbe(DeviceActor.RecordTemperatureCompleted.class);
        final ActorRef<DeviceActor.Command> deviceActorRef = testKit.spawn(DeviceActor.create("group", "device"));

        // when
        deviceActorRef.tell(new DeviceActor.RecordTemperature(requestId, 35.0, probe.getRef()));

        // then
        final DeviceActor.RecordTemperatureCompleted response = probe.receiveMessage();

        assertEquals(requestId, response.requestId);
    }

    @Test
    public void testReadTemperatureWhenMultipleRecordingsAreCompleted() {

        // given
        final UUID recordRequest1Id = UUID.randomUUID();
        final UUID recordRequest2Id = UUID.randomUUID();
        final UUID recordRequest3Id = UUID.randomUUID();
        final UUID readRequest1Id = UUID.randomUUID();
        final UUID readRequest2Id = UUID.randomUUID();
        final UUID readRequest3Id = UUID.randomUUID();

        final double temperature1 = 10.5;
        final double temperature2 = 15.5;
        final double temperature3 = 8.5;

        final TestProbe<DeviceActor.RespondTemperature> readProbe = testKit.createTestProbe(DeviceActor.RespondTemperature.class);
        final TestProbe<DeviceActor.RecordTemperatureCompleted> recordProbe = testKit.createTestProbe(DeviceActor.RecordTemperatureCompleted.class);
        final ActorRef<DeviceActor.Command> deviceActorRef = testKit.spawn(create("group", "device"));

        // when: first recording
        deviceActorRef.tell(new DeviceActor.RecordTemperature(recordRequest1Id, temperature1, recordProbe.getRef()));
        deviceActorRef.tell(new DeviceActor.ReadTemperature(readRequest1Id, readProbe.getRef()));

        // then: first recording
        final DeviceActor.RecordTemperatureCompleted recordResponse1 = recordProbe.receiveMessage();
        assertEquals(recordRequest1Id, recordResponse1.requestId);

        final DeviceActor.RespondTemperature readResponse1 = readProbe.receiveMessage();
        assertEquals(readRequest1Id, readResponse1.requestId);
        assertEquals(Optional.of(temperature1), readResponse1.value);

        // ==

        // when: second recording
        deviceActorRef.tell(new DeviceActor.RecordTemperature(recordRequest2Id, temperature2, recordProbe.getRef()));
        deviceActorRef.tell(new DeviceActor.ReadTemperature(readRequest2Id, readProbe.getRef()));

        // then: second recording
        final DeviceActor.RecordTemperatureCompleted recordResponse2 = recordProbe.receiveMessage();
        assertEquals(recordRequest2Id, recordResponse2.requestId);

        final DeviceActor.RespondTemperature readResponse2 = readProbe.receiveMessage();
        assertEquals(readRequest2Id, readResponse2.requestId);
        assertEquals(Optional.of(temperature2), readResponse2.value);

        // ==

        // when: third recording
        deviceActorRef.tell(new DeviceActor.RecordTemperature(recordRequest3Id, temperature3, recordProbe.getRef()));
        deviceActorRef.tell(new DeviceActor.ReadTemperature(readRequest3Id, readProbe.getRef()));

        // then: third recording
        final DeviceActor.RecordTemperatureCompleted recordResponse3 = recordProbe.receiveMessage();
        assertEquals(recordRequest3Id, recordResponse3.requestId);

        final DeviceActor.RespondTemperature readResponse3 = readProbe.receiveMessage();
        assertEquals(readRequest3Id, readResponse3.requestId);
        assertEquals(Optional.of(temperature3), readResponse3.value);
    }

}
