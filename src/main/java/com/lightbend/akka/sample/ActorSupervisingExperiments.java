package com.lightbend.akka.sample;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.PreRestart;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

class SupervisorActor extends AbstractBehavior<String> {

    static Behavior<String> create() {
        return Behaviors.setup(SupervisorActor::new);
    }

    private final ActorRef<String> child;

    private SupervisorActor(ActorContext<String> ctx) {
        super(ctx);
        this.child = ctx.spawn(Behaviors.supervise(SupervisedActor.create()).onFailure(SupervisorStrategy.restart()), "supervised-actor");
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder()
                .onMessageEquals("failChild", this::onFailChild)
                .build();
    }

    private Behavior<String> onFailChild() {
        child.tell("fail");
        return this;
    }
}

class SupervisedActor extends AbstractBehavior<String> {

    static Behavior<String> create() {
        return Behaviors.setup(SupervisedActor::new);
    }

    private SupervisedActor(ActorContext<String> ctx) {
        super(ctx);
        System.out.println("supervised actor started");
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder()
                .onMessageEquals("fail", this::onFail)
                .onSignal(PreRestart.class, signal -> onPreRestart())
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<String> onPostStop() {
        System.out.println("supervised stopped");
        return this;
    }

    private Behavior<String> onPreRestart() {
        System.out.println("supervised will be restarted");
        return this;
    }

    private Behavior<String> onFail() {
        System.out.println("supervised failing");
        throw new RuntimeException("I failed!");
    }
}

class SupervisingMain extends AbstractBehavior<String> {

    static Behavior<String> create() {
        return Behaviors.setup(SupervisingMain::new);
    }

    private final ActorRef<String> child;

    private SupervisingMain(ActorContext<String> ctx) {
        super(ctx);
        child = getContext().spawn(SupervisorActor.create(), "supervisor-actor");
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder().onMessageEquals("start", this::onStart).build();
    }

    private Behavior<String> onStart() {
        child.tell("failChild");
        return this;
    }

}

public class ActorSupervisingExperiments {
    public static void main(String[] args) {
        ActorSystem<String> supervisingSystem = ActorSystem.create(SupervisingMain.create(), "supervising-system");
        supervisingSystem.tell("start");
    }
}
