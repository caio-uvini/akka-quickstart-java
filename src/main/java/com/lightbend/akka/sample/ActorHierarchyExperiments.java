package com.lightbend.akka.sample;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

class PrintMyActorRefActor extends AbstractBehavior<String> {

    static Behavior<String> create() {
        return Behaviors.setup(PrintMyActorRefActor::new);
    }

    private PrintMyActorRefActor(ActorContext<String> ctx) {
        super(ctx);
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder()
                .onMessageEquals("printIt", this::onPrintIt)
                .build();
    }

    private Behavior<String> onPrintIt() {
        ActorRef<Object> secondRef = getContext().spawn(Behaviors.empty(), "second-actor");
        System.out.println("Second: " + secondRef);
        return this;
    }

}

class HierarchyMain extends AbstractBehavior<String> {

    static Behavior<String> create() {
        return Behaviors.setup(HierarchyMain::new);
    }

    private HierarchyMain(ActorContext<String> ctx) {
        super(ctx);
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder()
                .onMessageEquals("start", this::onStart)
                .build();
    }

    private Behavior<String> onStart() {
        ActorRef<String> firstRef = getContext().spawn(PrintMyActorRefActor.create(), "first-actor");

        System.out.println("First: " + firstRef);
        firstRef.tell("printIt");
        return Behaviors.same();
    }

}

public class ActorHierarchyExperiments {
    public static void main(String[] args) {
        ActorSystem<String> testSystem = ActorSystem.create(HierarchyMain.create(), "testSystem");
        testSystem.tell("start");
    }
}