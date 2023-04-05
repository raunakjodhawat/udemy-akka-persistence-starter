package part3_stores_serialization

import akka.actor.ActorLogging
import akka.persistence.{
  PersistentActor,
  RecoveryCompleted,
  SaveSnapshotSuccess,
  SnapshotOffer,
  SaveSnapshotFailure
}

class SimplePersistentActor extends PersistentActor with ActorLogging {
  // mutable state
  var nMessages = 0
  override def receiveRecover: Receive = {
    case RecoveryCompleted => log.info("Recovery completed")
    case SnapshotOffer(_, payload: Int) => {
      log.info(s"recovered snapshot: $payload")
      nMessages = payload
    }
    case message => {
      log.info(s"Recovered $message")
      nMessages += 1
    }
  }

  override def receiveCommand: Receive = {
    case "print" => {
      log.info(s"I have persisted $nMessages so far")
    }
    case "snap" => {
      saveSnapshot(nMessages)
    }
    case SaveSnapshotSuccess(_) => log.info("Save snapshot was successful")
    case SaveSnapshotFailure(_, cause) =>
      log.warning(s"Save snapshot failed: $cause")
    case message =>
      persist(message) { _ =>
        log.info(s"Persisting $message")
        nMessages += 1
      }
  }

  override def persistenceId: String = "simple-persistent-actor"
}
