package com.lightbend.akka.tutorial;

import akka.actor.typed.ActorSystem;

import java.io.IOException;

public class IoTMain {

    public static void main(String[] args) {

        final ActorSystem<Void> actorSystemRef = ActorSystem.create(IotSupervisor.create(), "iot-system");

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            actorSystemRef.terminate();
        }
    }

}
