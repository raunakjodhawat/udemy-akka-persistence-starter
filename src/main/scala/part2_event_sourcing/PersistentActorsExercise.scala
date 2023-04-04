package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistentActorsExercise extends App {
  /*
  Persistent actor for a voting station
  Keep:
    - the citizens who voted
    - the poll: mapping between a candidate and the number of received votes so far

   The actor must be able to recover its state if it's shut down or restarted
   */
  case class Vote(citizenPID: String, candidate: String)
  class VoteMachine() extends PersistentActor with ActorLogging {
    var citizensVotes: Set[String] = Set.empty
    var poll: Map[String, Int] = Map[String, Int]()
    override def receiveRecover: Receive = {
      case vote @ Vote(cPID, c) => {
        handleInternalStateChange(vote)
        log.info(s"recovered for ${cPID} with a vote to $c")
      }
    }

    override def receiveCommand: Receive = {
      case vote @ Vote(cPID, c) => {
        if (!citizensVotes.contains(vote.citizenPID)) {
          persist(vote) { _ =>
            handleInternalStateChange(vote)
            log.info(s"Poll saved for $c given by $cPID")
          }
        }
      }
      case "Print" => {
        log.info(s"Candidate: ${citizensVotes}, Poll: ${poll}")
      }
    }
    def handleInternalStateChange(vote: Vote): Unit = {
      citizensVotes += vote.citizenPID
      val currentVoteCount = poll.getOrElse(vote.candidate, 0)
      poll += (vote.candidate -> (currentVoteCount + 1))
    }
    override def persistenceId: String = "vote-machine-1"
  }

  val system = ActorSystem("votingSystem")
  val voteActor = system.actorOf(Props[VoteMachine], "voteMachine")

//  voteActor ! Vote("raunak", "a")
//  voteActor ! Vote("priyal", "a")
//  voteActor ! Vote("ankush", "a")
//  voteActor ! Vote("Harshal", "b")
//  voteActor ! Vote("Aashay", "b")
  voteActor ! "Print"
}
