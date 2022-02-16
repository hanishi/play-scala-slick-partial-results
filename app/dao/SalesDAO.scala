package dao

import models.Cursor.{firstPage, page}
import models.{Cursor, Sale}
import org.h2.tools.Server
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logging}
import slick.jdbc.JdbcProfile

import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait SalesComponent {
  this: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._
  class Sales(tag: Tag) extends Table[Sale](tag, "sales") {
    override def * = (id, date) <> (intoSale, fromSale)

    def id = column[Long]("sale_id", O.PrimaryKey, O.AutoInc)

    def date = column[Timestamp]("sale_date")

    def intoSale(row: (Long, Timestamp)) = Sale(row._1, row._2.toLocalDateTime)

    def fromSale(sale: Sale): Option[(Long, Timestamp)] =
      Some(sale.id, Timestamp.from(sale.date.toInstant(ZoneOffset.UTC)))
  }
}

@Singleton()
class SalesDAO @Inject() (
    configuration: Configuration,
    applicationLifecycle: ApplicationLifecycle,
    protected val dbConfigProvider: DatabaseConfigProvider
)(implicit executionContext: ExecutionContext)
    extends SalesComponent
    with HasDatabaseConfigProvider[JdbcProfile]
    with Logging {

  val server = Server
    .createTcpServer(
      "-tcp",
      "-tcpAllowOthers",
      "-tcpPort",
      configuration
        .getOptional[String]("app.h2.server.port")
        .getOrElse("9092"),
      "-tcpDaemon"
    )
    .start()
  logger.info(
    "started H2 tcp server! You can access it with 'jdbc:h2:tcp://localhost:9092/mem:play'"
  )
  applicationLifecycle.addStopHook(() =>
    Future {
      logger.info("shutting down H2 tcp server")
      server.shutdown()
    }
  )

  import profile.api._
  val sales = TableQuery[Sales]
  private val cursorDefaultLimit =
    configuration
      .getOptional[Int]("app.cursor-default-limit")
      .getOrElse(10)

  def find(
      start: LocalDateTime,
      end: LocalDateTime,
      desc: Boolean = true,
      size: Option[Int] = None
  ): Future[(Seq[Sale], Option[Cursor], Int)] = for {
    (sales, limit) <- find(start, end, defaultSize(size), desc)
    count <- count(between(start, end))
  } yield firstPage(sales, limit) match {
    case (sales, cursor) => (sales, cursor, count)
  }

  private def find(
      start: LocalDateTime,
      end: LocalDateTime,
      size: Int,
      desc: Boolean
  ): Future[(Seq[Sale], Int)] = db
    .run(
      sales
        .filter(between(start, end))
        .sortBy(sales => if (desc) sales.id.desc else sales.id.asc)
        .sortBy(sales => if (desc) sales.date.desc else sales.date.asc)
        .take(size + 1)
        .result
    )
    .map {
      case a if a.length <= size => (a, 0)
      case b                     => (b.dropRight(1), size)
    }

  def findBefore(
      id: Long,
      start: LocalDateTime,
      end: LocalDateTime,
      desc: Boolean = true,
      size: Option[Int]
  ): Future[(Seq[Sale], Option[Cursor], Int)] = for {
    (sales, limit) <- findForward(defaultSize(size), id, start, end, true, desc)
    page <- before(id, start, end, false, desc, size).map {
      case (prev, offset) =>
        page(prev, offset, sales, limit)
    }
    count <- count(between(start, end))
  } yield page match {
    case (sales, cursor) => (sales, cursor, count)
  }

  private def count(filter: Sales => Rep[Boolean]): Future[Int] =
    db.run(sales.filter(filter).length.result)

  private def before(
      id: Long,
      start: LocalDateTime,
      end: LocalDateTime,
      inclusive: Boolean,
      desc: Boolean,
      size: Option[Int] = None
  ): Future[(Option[Sale], Int)] =
    findBackward(defaultSize(size), id, start, end, inclusive, desc)
      .map(sales => (sales.lastOption, sales.size))

  private def findBackward(
      size: Int,
      id: Long,
      start: LocalDateTime,
      end: LocalDateTime,
      inclusive: Boolean,
      desc: Boolean
  ): Future[Seq[Sale]] = db.run(
    sales
      .filter(between(start, end))
      .filter(
        if (desc) filterLarger(inclusive, id)
        else filterSmaller(inclusive, id)
      )
      .sortBy(sales => if (desc) sales.id.asc else sales.id.desc)
      .sortBy(sales => if (desc) sales.date.asc else sales.date.desc)
      .take(size)
      .result
  )

  private def defaultSize(size: Option[Int]): Int =
    size.getOrElse(cursorDefaultLimit)

  private def findForward(
      size: Int,
      id: Long,
      start: LocalDateTime,
      end: LocalDateTime,
      inclusive: Boolean,
      desc: Boolean
  ): Future[(Seq[Sale], Int)] =
    db.run(
      sales
        .filter(between(start, end))
        .filter(
          if (desc) filterSmaller(inclusive, id)
          else filterLarger(inclusive, id)
        )
        .sortBy(sales => if (desc) sales.id.desc else sales.id.asc)
        .sortBy(sales => if (desc) sales.date.desc else sales.date.asc)
        .take(size + 1)
        .result
    ).map {
      case a if a.length <= size => (a, 0)
      case b                     => (b.dropRight(1), size)
    }

  private def between(
      start: LocalDateTime,
      end: LocalDateTime
  ): Sales => Rep[Boolean] = sales =>
    sales.date.between(
      Timestamp.valueOf(start.truncatedTo(ChronoUnit.DAYS)),
      Timestamp.valueOf(end.truncatedTo(ChronoUnit.DAYS))
    )

  private def filterSmaller(
      inclusive: Boolean,
      id: Long
  ): Sales => Rep[Boolean] = sales =>
    if (inclusive) sales.id <= id else sales.id < id

  private def filterLarger(
      inclusive: Boolean,
      id: Long
  ): Sales => Rep[Boolean] = sales =>
    if (inclusive) sales.id >= id else sales.id > id

  def findAfter(
      id: Long,
      start: LocalDateTime,
      end: LocalDateTime,
      desc: Boolean = true,
      size: Option[Int]
  ): Future[(Seq[Sale], Option[Cursor], Int)] = for {
    (sales, limit) <- findForward(
      defaultSize(size),
      id,
      start,
      end,
      false,
      desc
    )
    (prev, offset) <- before(id, start, end, true, desc, size)
    count <- count(between(start, end))
  } yield page(prev, offset, sales, limit) match {
    case (sales, cursor) => (sales, cursor, count)
  }

}
