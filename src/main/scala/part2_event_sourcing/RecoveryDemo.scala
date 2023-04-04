package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{
  PersistentActor,
  Recovery,
  RecoveryCompleted,
  SnapshotSelectionCriteria
}

object RecoveryDemo extends App {
  case class Command(contents: String)
  case class Event(id: Int, contents: String)
  class RecoveryActor extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case Event(id, contents) =>
//      if (contents.contains("314")) {
//        throw new RuntimeException("I can't take this anymore")
//      }
        log.info(s"Recovered: $contents")
        context.become(online(id + 1))
      /*
        This will not change the event handler during recovery
        After recovery the "normal" handler will be the result of all the stacking of context.become
       */
      case RecoveryCompleted => {
        // additional initialization
        log.info("I have finished recovery")
      }
    }

    override def receiveCommand: Receive = online(0)

    def online(id: Int): Receive = { case Command(contents) =>
      persist(Event(id, contents)) { event =>
        log.info(s"Successfully persisted $event")
        context.become(online(id + 1))
      }
    }

    override def persistenceId: String = "recovery-actor"

    override def onRecoveryFailure(
        cause: Throwable,
        event: Option[Any]
    ): Unit = {
      log.error("I failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

    //    override def recovery: Recovery = Recovery(toSequenceNr = 100)
    //    override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
//    override def recovery: Recovery = Recovery.none

  }
  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recoveryActor")
  for (i <- 1 to 1000) {
    recoveryActor ! Command(s"command $i")
  }
  // All commands sent during recovery are stashed

  /*
  2 - Failure during recovery
    - onRecoveryFailure => actor is stopped
   */

  /*
  3 - customizing recovery
    - Do NOT persist more events after a customized recoevry ( if customized recovery is not complete)
   */

  /*
  4 - Recovery status or knowing when recovery is complete
    - getting a signal when recovery is complete
   */

  /*
  5 - stateless actors
   */
}
