package part2_event_sourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

import java.util.Date

object MultiplePersistence extends App {

  /*
  Diligent accountant: with every invoice, will persist two events
  - a tax record for the fiscal authority
  - a invoice record for personal logs or some auditing authority
   */
  // COMMAND
  case class Invoice(recipient: String, date: Date, amount: Int)

  // EVENTS
  case class TaxRecord(taxId: String, recordId: Int, date: Date, amount: Int)
  case class InvoiceRecord(
      invoiceRecordId: Int,
      recipient: String,
      date: Date,
      amount: Int
  )

  object DiligentAccountant {
    def props(taxId: String, taxAuthority: ActorRef) = Props(
      new DiligentAccountant(taxId, taxAuthority)
    )
  }
  class DiligentAccountant(taxId: String, taxAuthority: ActorRef)
      extends PersistentActor
      with ActorLogging {
    var latestTaxRecordId = 0
    var latestInvoiceRecordId = 0
    override def receiveRecover: Receive = { case event =>
      log.info(s"Recovered: $event")
    }

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) => {
        // journal ! TaxRecord
        persist(TaxRecord(taxId, latestTaxRecordId, date, amount / 3)) {
          record =>
            taxAuthority ! record
            latestTaxRecordId += 1
            persist(
              "I herby declare this tax record this tax record to be true and complete."
            ) { declaration =>
              taxAuthority ! declaration
            }
        }
        // journal ! InvoiceRecord
        persist(InvoiceRecord(latestInvoiceRecordId, recipient, date, amount)) {
          invoiceRecord =>
            taxAuthority ! invoiceRecord
            latestInvoiceRecordId += 1
            persist(
              "I herby declare this invoice record this tax record to be true and complete."
            ) { declaration =>
              taxAuthority ! declaration
            }
        }
      }
    }

    override def persistenceId: String = "diligent-accountant"
  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = { case message =>
      log.info(s"Received: $message")
    }
  }
  val system = ActorSystem("MultiplePersistsDemo")
  val taxAuthority = system.actorOf(Props[TaxAuthority], "HMRC")
  val accountant =
    system.actorOf(DiligentAccountant.props("UK23234_234234", taxAuthority))

  accountant ! Invoice("the sofa company", new Date, 2000)

  accountant ! Invoice("the super car sofa company", new Date, 5000)

  /*
  The message ordering (TaxRecord -> InvoiceRecord) is GUARANTEED
   */
  /** PERSISTENCE is also based on message parsing
    */

  // nested persisting
}
