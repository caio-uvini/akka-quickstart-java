package com.lightbend.akka.sample;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

class StartStopMain extends AbstractBehavior<String> {

    static Behavior<String> create() {
        return Behaviors.setup(StartStopMain::new);
    }

    private StartStopMain(ActorContext<String> ctx) {
        super(ctx);
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder()
                .onMessageEquals("start", this::onStart)
                .build();
    }

    private Behavior<String> onStart() {
        ActorRef<String> firstRef = getContext().spawn(StartStopActor1.create(), "first");
        firstRef.tell("stop");
        return Behaviors.same();
    }

}

class StartStopActor1 extends AbstractBehavior<String> {

    static Behavior<String> create() {
        return Behaviors.setup(StartStopActor1::new);
    }

    private StartStopActor1(ActorContext<String> ctx) {
        super(ctx);
        System.out.println("first started");

        ctx.spawn(StartStopActor2.create(), "second");
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder()
                .onMessageEquals("stop", Behaviors::stopped)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<String> onPostStop() {
        System.out.println("first stopped");
        return this;
    }
}

class StartStopActor2 extends AbstractBehavior<String> {

    static Behavior<String> create() {
        return Behaviors.setup(StartStopActor2::new);
    }

    private StartStopActor2(ActorContext<String> ctx) {
        super(ctx);
        System.out.println("second started");
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder()
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<String> onPostStop() {
        System.out.println("second stopped");
        return this;
    }

}

public class ActorPostStopExperiments {
    public static void main(String[] args) {
        ActorSystem<String> startStopSystem = ActorSystem.create(StartStopMain.create(), "start-stop-system");
        startStopSystem.tell("start");
    }
}
