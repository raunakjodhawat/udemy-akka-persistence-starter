package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{
  PersistentActor,
  SaveSnapshotFailure,
  SaveSnapshotSuccess,
  SnapshotOffer
}

import scala.collection.mutable

object Snapshots extends App {

  // commands
  case class ReceivedMessage(contents: String) // message from your contact
  case class SendMessage(contents: String) // message TO your contact
  // events
  case class ReceiveMessageRecord(id: Int, contents: String)
  case class SendMessageRecord(id: Int, contents: String)
  object Chat {
    def props(owner: String, contact: String): Props = Props(
      new Chat(owner, contact)
    )
  }
  class Chat(owner: String, contact: String)
      extends PersistentActor
      with ActorLogging {
    val MAX_MESSAGES = 10
    var commandsWithoutCheckpoint = 0
    var currentMessageId = 0
    val lastMessages = new mutable.Queue[(String, String)]

    override def receiveRecover: Receive = {
      case ReceiveMessageRecord(id, contents) =>
        log.info(s"Recovered receive message: $id : $contents")
        maybeReplaceMessage(contact, contents)
        currentMessageId = id
      case SendMessageRecord(id, contents) =>
        log.info(s"Recovered sent message: $id : $contents")
        maybeReplaceMessage(owner, contents)
        currentMessageId = id
      case SnapshotOffer(metadata, contents) =>
        log.info(s"Recovered snapshot: $metadata")
        contents
          .asInstanceOf[mutable.Queue[(String, String)]]
          .foreach(lastMessages.enqueue(_))
    }

    override def receiveCommand: Receive = {
      case ReceivedMessage(contents) =>
        persist(ReceiveMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Received message: $contents")
          maybeReplaceMessage(contact, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case SendMessage(contents) =>
        persist(SendMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Sent message: $contents")
          maybeReplaceMessage(owner, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case "print" => log.info(s"Most recent messages: $lastMessages")
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"Saving snapshot succeeded: $metadata")
      case SaveSnapshotFailure(metadata, reason) =>
        log.warning(s"saving snapshot: $metadata failed because of $reason")
    }

    def maybeReplaceMessage(sender: String, contents: String) = {
      if (lastMessages.size >= MAX_MESSAGES) {
        lastMessages.dequeue()
      }
      lastMessages.enqueue((sender, contents))
    }
    def maybeCheckpoint(): Unit = {
      commandsWithoutCheckpoint += 1
      if (commandsWithoutCheckpoint >= MAX_MESSAGES) {
        log.info("Saving checkpoint...")
        saveSnapshot(lastMessages) // async operation
        commandsWithoutCheckpoint = 0
      }
    }

    override def persistenceId: String = s"$owner-$contact-chat"
  }

  val system = ActorSystem("SnapshotsDemo")
  val chat = system.actorOf(Chat.props("raunak", "priyal"))

//  for (i <- 1 to 100000) {
//    chat ! ReceivedMessage(s"Akka rocks $i")
//    chat ! SendMessage(s"Akka rules: $i")
//  }

  // snapshots!
  chat ! "print"
}
