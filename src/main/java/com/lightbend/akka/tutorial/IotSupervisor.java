package com.lightbend.akka.tutorial;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class IotSupervisor extends AbstractBehavior<Void> {

    public static Behavior<Void> create() {
        return Behaviors.setup(IotSupervisor::new);
    }

    private IotSupervisor(ActorContext<Void> context) {
        super(context);
        context.getLog().info("IoT Application started!");
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
    }

    private Behavior<Void> onPostStop() {
        getContext().getLog().info("Iot Application stopped!");
        return Behaviors.same();
    }
}
