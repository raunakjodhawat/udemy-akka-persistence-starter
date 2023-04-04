package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.PersistentActor

import java.util.Date

object PersistentActors extends App {
  /*
  Scenario: we have a business and an accountant which keeps track of our invoices and sums up the total
   */

  case class Invoice(recipient: String, date: Date, amount: Int)
  case class InvoiceRecorded(
      id: Int,
      recipient: String,
      date: Date,
      amount: Int
  )
  // special messages
  case object Shutdown
  case class InvoiceBulk(invoices: List[Invoice])
  class Accountant extends PersistentActor with ActorLogging {
    var latestInvoiceId = 0
    var totalAmount = 0
    // Handler that will be called on recovery
    override def receiveRecover: Receive = {
      /*
      best practice: follow the logic in the persist steps of receiveCommand
       */
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId = id
        totalAmount += amount
        log.info(
          s"recovered, invoice #$id for amount $amount, total amount: $totalAmount"
        )

    }

    // The "normal" receive method
    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        /** When you receive a command
          * 1) you create an event to persisit into the store
          * 2) you persisit the event, pass in a callback that will be triggered once the event is written
          * 3) we update the actor's state when the event has persisted
          */
        log.info(s"Received invoice for amount: $amount")
        persist(
          InvoiceRecorded(latestInvoiceId, recipient, date, amount)
        ) /*time gap all other messages sent to this actor are stashed */ { e =>
          // safe to access mutable state here

          // update state
          latestInvoiceId += 1
          totalAmount += amount

          // correctly identify the sender of the command
          sender() ! "PersistenceACK"
          log.info(
            s"Persisted $e as invoice #${e.id}. for total amount: $totalAmount"
          )

          // not obliged to persist
        }
      case InvoiceBulk(invoices) => {
        /*
        1. create events
        2. persists all the event state
        3. update the actor state when each event os persisted
         */
        val invoiceIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoiceIds).map { pair =>
          val id = pair._2
          val invoice = pair._1
          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }
        persistAll(events) { e =>
          latestInvoiceId += 1
          totalAmount += e.amount
          log.info(
            s"Persisted Single $e as invoice #${e.id}. for total amount: $totalAmount"
          )
        }
      }

      case Shutdown => context.stop(self)
      case "Print" =>
        log.info(s"Latest invoice for $latestInvoiceId has $totalAmount")
    }

    /* This method is called if persisting failed.
     * The actor will be stopped
     *
     * Best practice: start the actor again after a while
     * use backoff supervisor
     */
    override def onPersistFailure(
        cause: Throwable,
        event: Any,
        seqNr: Long
    ): Unit = {
      log.error(s"Fail to persist: $event Because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    /*
    Called if the journal fails to persist the event
    the actor is resumed
     */
    override def onPersistRejected(
        cause: Throwable,
        event: Any,
        seqNr: Long
    ): Unit = {
      log.error(s"Persist rejected for $event because of $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
    override def persistenceId: String =
      "simple-accountant" // best practice: make it unique
  }
  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

  for (i <- 1 to 10) {
    accountant ! Invoice("the sofa company", new Date, i * 1000)
  }
  accountant ! "Print"

  val newInvoices =
    for (i <- 1 to 5) yield Invoice("the awesome chair", new Date, i * 2000)

//  accountant ! InvoiceBulk(newInvoices.toList)
  /*

  Persistent failures:
  1. Failures to persist
  2. journal impl fails to persist
   */
  /*
  Persisting multiple events
  persistAll
   */

  /** Never ever call persist or persistall from futures
    */
  /*
  Shutdown of persistent actors
  Best practice: defined your own "shutdown" messages
   */
  // accountant ! PoisonPill
  accountant ! Shutdown
}
