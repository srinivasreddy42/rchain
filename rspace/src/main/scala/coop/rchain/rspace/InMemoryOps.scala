package coop.rchain.rspace
import scala.concurrent.SyncVar
import cats._, cats.data._, cats.implicits._

trait InMemTransaction[S] {
  def commit(): Unit
  def abort(): Unit
  def close(): Unit
  def readState[R](f: S => R): R
  def writeState[R](f: S => (S, R)): R
  def name: String
}

trait InMemoryOps[S] extends CloseOps {
  protected[rspace] type Transaction = InMemTransaction[S]

  protected[rspace] type StateType = S

  private[rspace] def emptyState: S

  private[rspace] def updateGauges(): Unit

  private[rspace] val stateRef: SyncVar[S] = {
    val sv = new SyncVar[S]
    sv.put(emptyState)
    sv
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  // TODO stop throwing exceptions
  protected[this] def updateGauges(update: Transaction => Unit): Unit = {
    val txn = createTxnRead()
    try {
      update(txn)
      txn.commit()
    } catch {
      case ex: Throwable =>
        txn.abort()
        throw ex
    } finally {
      txn.close()
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  // TODO stop throwing exceptions
  private[rspace] def withTxn[R](txn: Transaction)(f: Transaction => R): R =
    try {
      val ret: R = f(txn)
      txn.commit()
      ret
    } catch {
      case ex: Throwable =>
        txn.abort()
        throw ex
    } finally {
      txn.close()
      updateGauges()
    }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  // TODO stop throwing exceptions
  private[rspace] def createTxnRead(): InMemTransaction[S] = {
    failIfClosed()

    new InMemTransaction[S] {

      val name: String = "read-" + Thread.currentThread().getId.show

      private[this] val state = stateRef.get

      override def commit(): Unit = {}

      override def abort(): Unit = {}

      override def close(): Unit = {}

      override def readState[R](f: S => R): R = f(state)

      override def writeState[R](f: S => (S, R)): R =
        throw new RuntimeException("read txn cannot write")
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private[rspace] def createTxnWrite(): InMemTransaction[S] = {
    failIfClosed()

    new InMemTransaction[S] {
      val name: String = "write-" + Thread.currentThread().getId.show

      private[this] val initial = stateRef.take
      private[this] var current = initial

      override def commit(): Unit =
        stateRef.put(current)

      override def abort(): Unit = stateRef.put(initial)

      override def close(): Unit = {}

      override def readState[R](f: S => R): R = f(current)

      override def writeState[R](f: S => (S, R)): R = {
        val (newState, result) = f(current)
        current = newState
        result
      }
    }
  }
}
