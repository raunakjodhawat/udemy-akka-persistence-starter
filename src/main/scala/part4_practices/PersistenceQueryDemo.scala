package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt
import scala.util.Random

object PersistenceQueryDemo extends App {
  val system = ActorSystem(
    "PersistenceQueryDemo",
    ConfigFactory.load().getConfig("persistence-query")
  )

  // read journal
  val readJournal = PersistenceQuery(system)
    .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // 1. give me all persistence Id's
  val persistenceIds = readJournal.persistenceIds()

  implicit val materializer = ActorMaterializer()(system)
//  persistenceIds.runForeach { persistenceId =>
//    println(s"Found persistence Id: $persistenceId")
//  }

  class SimplePersistentActor extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = { case e =>
      log.info(s"Recovered ${e}")
    }

    override def receiveCommand: Receive = { case message =>
      persist(message) { _ => log.info(s"Persisted the $message") }
    }

    override def persistenceId: String = "persistence-query-id"
  }

  val simpleActor =
    system.actorOf(Props[SimplePersistentActor], "simplePersistentActor")

  import system.dispatcher
//  system.scheduler.scheduleOnce(5.seconds) {
//    simpleActor ! "Hello persistent actor"
//  }

  // 2. pick up events by persistent ids
  val events =
    readJournal.eventsByPersistenceId("persistence-query-id", 0, Long.MaxValue)

  events.runForeach { e =>
    println(s"Read event ${e}")
  }

  // 3. event by tags
  val genres = Array("pop", "rock", "hip-hop", "disco", "jazz")
  case class Song(artist: String, title: String, genre: String)
  // command
  case class PlayList(songs: List[Song])

  // event
  case class PlayListPurchased(id: Int, songs: List[Song])

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {
    var latestPlaylistId = 0
    override def receiveRecover: Receive = {
      case event @ PlayListPurchased(id, _) => {
        log.info(s"Recovered $event")
        latestPlaylistId += id
      }
    }

    override def receiveCommand: Receive = { case PlayList(songs) =>
      persist(PlayListPurchased(latestPlaylistId, songs)) { _ =>
        log.info(s"user purchased $songs")
        latestPlaylistId += 1
      }
    }

    override def persistenceId: String = "music-store-checkout"
  }

  class MusicStoreEventAdaptor extends WriteEventAdapter {
    override def manifest(event: Any): String = "music-store"

    override def toJournal(event: Any): Any = {
      event match {
        case event @ PlayListPurchased(_, songs) => {
          val genres = songs.map(_.genre).toSet
          Tagged(event, genres)
        }
        case event => event
      }
    }
  }
  val checkoutActor =
    system.actorOf(Props[MusicStoreCheckoutActor], "musicStoreActor")
  val r = new Random()
  for (_ <- 1 to 5) {
    val maxSongs = r.nextInt(5)
    val songs = for (i <- 1 to maxSongs) yield {
      val randomGenre = genres(r.nextInt(5))
      Song(s"Artist $i", s"hello ${i}", randomGenre)
    }
    checkoutActor ! PlayList(songs.toList)
  }

  val rockPlaylist = readJournal.eventsByTag("rock", Offset.noOffset)
  rockPlaylist.runForeach { e =>
    println(s"found a playlist with a rock song: $e")
  }
}
