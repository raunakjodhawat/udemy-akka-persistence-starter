package part2_event_sourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistAsyncDemo extends App {

  case class Command(contents: String)
  case class Event(contents: String)
  object CriticalStreamProcessor {
    def props(eventAggregator: ActorRef): Props = Props(
      new CriticalStreamProcessor(eventAggregator)
    )
  }
  class CriticalStreamProcessor(eventAggregator: ActorRef)
      extends PersistentActor
      with ActorLogging {
    override def receiveRecover: Receive = { case message =>
      log.info(s"Recovered: $message")
    }

    override def receiveCommand: Receive = {
      case Command(contents) => {
        eventAggregator ! s"Processing $contents"
        persistAsync(
          Event(contents)
        ) /* TIME GAP no stashing on case of persistAsync*/ { e =>
          eventAggregator ! e
        }
        // some actual computation
        val processedContents = contents + "_processed"
        persistAsync(Event(processedContents)) /* TIME GAP */ { e =>
          eventAggregator ! e
        }
      }
    }

    override def persistenceId: String = "critical-stream-processor"
  }

  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = { case message =>
      log.info(s"$message")
    }
  }

  val system = ActorSystem("PersistAsyncDemo")
  val eventAggregator =
    system.actorOf(Props[EventAggregator], "eventAggregator")
  val streamProcessor =
    system.actorOf(
      CriticalStreamProcessor.props(eventAggregator),
      "streamProcessor"
    )

  streamProcessor ! Command("Command #1")
  streamProcessor ! Command("Command #2")
  // order of persistAsync is maintained

  /*
  persistAsync vs persist
  persistAsync has upper hand in terms of performance - high throughput env. be careful with mutable state
  persist -> ordering guarantees
   */
}
