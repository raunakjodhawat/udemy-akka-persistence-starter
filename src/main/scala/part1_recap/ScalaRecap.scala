package part1_recap

import scala.concurrent.Future
import scala.util.{Success, Failure}

object ScalaRecap extends App {
  val aCondition: Boolean = true
  def myFunction(x: Int) = {
    // code
    if (x > 4) 42 else 65
  }
  // instructions vs expressions
  // types + type inference

  // OO features of Scala
  class Animal
  trait Carnivore {
    def eat(a: Animal): Unit
  }
  object Carnivore

  // generics
  abstract class MyList[+A]

  // method notations
  1 + 2 // 1.+(2) // infix notation

  // ! tell
  // ? ask

  // FP
  val anIncrementer: Int => Int = (x: Int) => x + 1
  println(anIncrementer(1))

  List(1, 2, 3).map(anIncrementer) // HOF's
  // map, flatMap, filter
  // for-comprehensions
  // Monads: Option, Try
  // Pattern matching!

  val unknwon: Any = 2
  val order = unknwon match {
    case 1 => "first"
    case 2 => "second"
    case _ => "unknown"
  }

  try {
    // code that can throw
    throw new RuntimeException()
  } catch {
    case g: Exception => println("I caught one!")
  }

  // Scala Advanced
  // multi threading problems
  import scala.concurrent.ExecutionContext.Implicits.global
  val future = Future {
    // long computation
    // executed on some other thread
  }
  // map, flatMap, filter + other niceties e.g. // recover/recoverWith
  future.onComplete {
    case Success(value) => println(s"I found the $value")
    case Failure(exception) =>
      println(s"I have the error ${exception.getMessage}")
  } // on some thread

  val partialFunction: PartialFunction[Int, Int] = {
    case 1 => 42
    case 2 => 65
    case _ => 999
  }
  // based on pattern matching

  // type aliases
  type AkkaReceive = PartialFunction[Any, Unit]
  def receive: AkkaReceive = {
    case 1 => println("hello")
    case _ => println("confused...")
  }

  // Implicits!
  implicit val timeout = 3000
  def setTimeout(f: () => Unit)(implicit timeout: Int) = f()

  setTimeout(() => println("timeout"))

  // conversions
  // 1. implicit methods
  case class Person(name: String) {
    def greet: String = s"Hi, $name"
  }

  implicit def fromStringToPerson(name: String) = Person(name)
  "Raunak".greet

  // 2. implicit classes
  implicit class Dog(name: String) {
    def bark = println("bark!")
  }

  "Lassie".bark

  // implicits organizations
  // local scope
  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  println(List(1, 2, 3).sorted)

  // imported scope
  // companion objects of the types involved in the call
  object Person {
    implicit val personOrdering: Ordering[Person] =
      Ordering.fromLessThan((a, b) => a.name.compareTo(b.name) < 0)
  }

  println(List(Person("Bob"), Person("Alice")).sorted)
}
