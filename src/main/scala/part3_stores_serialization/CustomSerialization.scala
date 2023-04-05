package part3_stores_serialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory

// command
case class RegisterUser(email: String, name: String)

// event
case class UserRegistered(id: Int, email: String, name: String)

// serializer
class UserRegisterationSerializer extends Serializer {
  val SEPARATOR = "//"
  override def identifier: Int = 53287

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event @ UserRegistered(id, email, name) => {
      println(s"Serializing: $event")
      s"[$id$SEPARATOR$email$SEPARATOR$name]".getBytes
    }
    case _ =>
      throw new IllegalArgumentException(
        "Only user registeration events supported, in this serializer"
      )
  }

  override def includeManifest: Boolean = false

  override def fromBinary(
      bytes: Array[Byte],
      manifest: Option[Class[_]]
  ): AnyRef = {
    val string = new String(bytes)
    val values = string.substring(1, string.length - 1).split(SEPARATOR)
    val id = values(0).toInt
    val email = values(1)
    val name = values(2)
    val result = UserRegistered(id, email, name)
    println(s"Deserialized :$result")
    result
  }
}
object CustomSerialization extends App {

  class UserRegisterationActor extends PersistentActor with ActorLogging {
    var currentId = 0
    override def receiveRecover: Receive = {
      case event @ UserRegistered(id, _, _) => {
        log.info(s"Recovered: $event")
        currentId = id
      }
    }

    override def receiveCommand: Receive = {
      case RegisterUser(email, name) => {
        persist(UserRegistered(currentId, email, name)) { e =>
          currentId += 1
          log.info(s"Persisted: ${e}")
        }
      }
    }

    override def persistenceId: String = "user-registeration"
  }

  val system = ActorSystem(
    "customSerialization",
    ConfigFactory.load().getConfig("customSerializerDemo")
  )

  val userRegisterationActor =
    system.actorOf(Props[UserRegisterationActor], "userRegisteration")
//  for (i <- 1 to 10) {
//    userRegisterationActor ! RegisterUser("user_i@gmail.com", i.toString)
//  }
}
