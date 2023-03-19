package part1_recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{
  Actor,
  ActorLogging,
  ActorSystem,
  OneForOneStrategy,
  PoisonPill,
  Props,
  Stash,
  SupervisorStrategy
}
import akka.util.Timeout
object AkkaRecap extends App {
  class SimpleActor extends Actor with ActorLogging with Stash {
    override def preStart(): Unit = {
      log.info("I am starting")
    }
    override def receive: Receive = {
      case "createChild" => {
        val childActor = context.actorOf(Props[SimpleActor], "myChild")
        childActor ! "Hello"
      }
      case "stashThis" => stash()
      case "changeHandlerNow" => {
        unstashAll()
        context.become(anotherHandler)
      }
      case "change" => context.become(anotherHandler)
      case message =>
        println(s"I received: $message")
    }
    def anotherHandler: Receive = { case message =>
      println(s"in another received: $message")
    }

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart
      case _                   => Stop
    }
  }

  val system = ActorSystem("test")
  // #1 you can only instantiate an actor through the actor system
  val actor = system.actorOf(Props[SimpleActor], "simple-actor")
  // #2: Sending message
  actor ! "Hello"
  /*
  Messages are sent async
  many actors can share a few dozen thread
  each message is processed atomically (no need for locks)
   */
  // changing actor behavior + Stashing

  // actors can spawn other actors

  // guardians: /system, /user, / = root guardian

  // actors have a defined lfecycle: started, stopped, suspended, resumed, restarted

  // stopping actors - context.stop
  actor ! PoisonPill

  // logging
  // supervision
  // configure akka infra: dispatchers, routers, mailboxes
  // schedulers
  import scala.concurrent.duration._
  import system.dispatcher
  system.scheduler.scheduleOnce(2 seconds) {
    actor ! "Happy Bithday"
  }

  // Akka patterns including Finite State Machine + ask pattern
  import akka.pattern.ask
  implicit val timeout = Timeout(3 seconds)
  val future = actor ? "question"
  // pipe pattern
  import akka.pattern.pipe
  val anotherActor = system.actorOf(Props[SimpleActor], "anotherSimpleActor")
  future.mapTo[String].pipeTo(anotherActor)
}
