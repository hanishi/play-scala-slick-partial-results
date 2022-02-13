import models.{Cursor, Sale}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{AnyContent, Request}

package object controllers {
  implicit val salesWrites = Json.writes[Sale]
  implicit val pagingWrites = Json.writes[Paging]

  sealed abstract case class UnpagedResults[T](
      override val data: Seq[T],
      override val count: Int
  ) extends Results[T](data, count)

  sealed abstract case class PagedResults[T](
      override val data: Seq[T],
      override val count: Int,
      paging: Paging
  ) extends Results[T](data, count)

  abstract class Results[T](val data: Seq[T], val count: Int)

  case class Paging(previous: Option[String], next: Option[String])

  object Results {
    implicit object resultsWritesSale extends Writes[Results[Sale]] {
      override def writes(results: Results[Sale]): JsValue =
        results match {
          case results: PagedResults[Sale] =>
            if (results.count > 0 && results.data.nonEmpty)
              Json.obj(
                "data" -> results.data,
                "paging" -> results.paging,
                "total" -> results.count
              )
            else
              Json.obj(
                "data" -> results.data,
                "paging" -> results.paging
              )
          case results: UnpagedResults[Sale] =>
            if (results.count > 0 && results.data.nonEmpty)
              Json.obj("data" -> results.data, "total" -> results.count)
            else
              Json.obj("data" -> results.data)
        }
    }

    def from[T](data: Seq[T], cursor: Cursor, queryString: String, count: Int)(
        implicit url: String
    ): Results[T] =
      cursor.toQueryParameters match {
        case (previous, next) =>
          new PagedResults(
            data,
            count,
            Paging(
              previous.map(parameters => link(url, parameters, queryString)),
              next.map(parameters => link(url, parameters, queryString))
            )
          ) {}
      }

    def link(url: String, parameters: String, queryString: String) = if (
      queryString.isEmpty
    ) s"$url$parameters"
    else s"$url$parameters&$queryString"

    def from[T](data: Seq[T], count: Int): Results[T] =
      new UnpagedResults(data, count) {}
  }

  implicit class pageToJsValue[T <: Cursor.Identity](
      tuple3: Tuple3[Seq[T], Option[Cursor], Int]
  )(implicit
      request: Request[AnyContent],
      writes: Writes[Results[T]]
  ) {
    def asJsValue(
        extra: Map[String, Seq[String]] = Map.empty
    )(implicit url: Request[AnyContent] => String): JsValue =
      tuple3 match {
        case (elements, Some(cursor), count) =>
          implicit val urlRequested = url(request)
          Json.toJson(
            Results.from(
              elements,
              cursor,
              extra
                .foldLeft("")((a, b) => {
                  b match {
                    case (param, values) =>
                      val queryString = s"$param=${values.mkString(",")}"
                      if (a.isEmpty) queryString else s"$a&$queryString"
                  }
                }),
              count
            )
          )
        case (elements, None, count) =>
          Json.toJson(Results.from(elements, count))
      }
  }
}
