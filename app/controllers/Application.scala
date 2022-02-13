package controllers

import dao.SalesDAO
import models.Sale
import play.api.libs.json.Writes
import play.api.mvc.{
  AbstractController,
  AnyContent,
  ControllerComponents,
  Request
}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class Application @Inject() (
    salesDAO: SalesDAO,
    controllerComponents: ControllerComponents
)(implicit executionContext: ExecutionContext)
    extends AbstractController(controllerComponents) {
  implicit val baseUrl: Request[AnyContent] => String =
    (request: Request[AnyContent]) =>
      s"""${if (request.secure) "https://" else "http://"}${request
        .headers("host")}${request.path}?"""
  def listSales(
      since: Option[String],
      until: Option[String],
      limit: Option[Int],
      before: Option[Long],
      after: Option[Long],
      order: Option[String]
  )(implicit writes: Writes[Results[Sale]]) = Action.async { implicit request =>
    val flag = order.fold(true) {
      case "DESC" => true
      case "ASC"  => false
      case _      => true
    }
    val default = LocalDate.parse("2022-02-14")
    val dateStart = since.getOrElse(
      default.minusWeeks(1).toString
    )
    val startLocalDateTime = LocalDate
      .parse(dateStart)
      .atStartOfDay()
    val dateEnd = until.getOrElse(
      default.toString
    )
    val endLocalDateTime = LocalDate
      .parse(dateEnd)
      .atStartOfDay()
    val result = (before, after) match {
      case (Some(id), None) =>
        salesDAO.findBefore(
          id,
          startLocalDateTime,
          endLocalDateTime,
          flag,
          limit
        )
      case (None, Some(id)) =>
        salesDAO.findAfter(
          id,
          startLocalDateTime,
          endLocalDateTime,
          flag,
          limit
        )
      case _ => salesDAO.find(startLocalDateTime, endLocalDateTime, flag, limit)
    }
    result.map(x =>
      Ok(
        x.asJsValue(
          order.fold(Map("since" -> Seq(dateStart), "until" -> Seq(dateEnd))) {
            order =>
              Map(
                "since" -> Seq(dateStart),
                "until" -> Seq(dateEnd),
                "order" -> Seq(order)
              )
          }
        )
      )
    )
  }
}
