package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object DetachingModels extends App {
  import DomainModel._
  class CouponManager extends PersistentActor with ActorLogging {
    val coupons: mutable.Map[String, User] = new mutable.HashMap[String, User]()
    override def receiveRecover: Receive = {
      case event @ CouponApplied(code, user) => {
        log.info(s"Recovered  ${event}")
        coupons.put(code, user)
      }
    }

    override def receiveCommand: Receive = { case ApplyCoupon(user, coupon) =>
      if (!coupons.contains(coupon.code)) {
        persist(CouponApplied(coupon.code, user)) { e =>
          coupons.put(coupon.code, user)
          log.info(s"Persisted event: ${e}")
        }
      }
    }

    override def persistenceId: String = "coupon-manager"
  }
  val system = ActorSystem(
    "DetachingModels",
    ConfigFactory.load().getConfig("detachingModels")
  )
  val couponManager = system.actorOf(Props[CouponManager], "couponManager")

//  for (i <- 10 to 15) {
//    val coupon = Coupon(s"MEGA_COUPON_$i", 100)
//    val user = User(s"$i", s"user_$i@gmail.com", s"user_$i")
//    couponManager ! ApplyCoupon(user, coupon)
//  }
}

object DomainModel {
  case class User(id: String, email: String, name: String)
  case class Coupon(code: String, promotionAmount: Int)
  // Command
  case class ApplyCoupon(user: User, coupon: Coupon)
  // event
  case class CouponApplied(code: String, user: User)
}

object DataModel {
  case class WrittenCouponApplied(
      code: String,
      userId: String,
      email: String
  )
  case class WrittenCouponAppliedV2(
      code: String,
      userId: String,
      email: String,
      name: String
  )
}

class ModelAdaptor extends EventAdapter {
  import DomainModel._
  import DataModel._
  override def manifest(event: Any): String = "CMA"

  // actor -> toJournal -> serializer -> journal
  override def toJournal(event: Any): Any = event match {
    case event @ CouponApplied(code, user) => {
      println(s"I am converting $event to dataModel")
      WrittenCouponAppliedV2(code, user.id, user.email, user.name)
    }
  }

  // journal -> serializer -> fromJournal -> to the actor
  override def fromJournal(event: Any, manifest: String): EventSeq =
    event match {
      case WrittenCouponApplied(code, userId, userEmail) => {
        println(s"I am converting $event to domain model")
        EventSeq.single(
          CouponApplied(code, User(userId, userEmail, "default_name"))
        )
      }
      case WrittenCouponAppliedV2(code, userId, userEmail, name) => {
        println(s"I am converting $event to domain model")
        EventSeq.single(CouponApplied(code, User(userId, userEmail, name)))
      }
      case e => EventSeq.single(e)
    }
}
