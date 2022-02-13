package models

object Cursor {

  def firstPage[T <: Identity](
      elements: Seq[T],
      limit: Int
  ): (Seq[T], Option[Cursor]) =
    if (elements.isEmpty)
      (elements, None)
    else if (limit > 0)
      (elements, Some(Cursor.first(Some(elements.last.id), limit)))
    else (elements, None)

  private def first(after: Option[Long], size: Int) =
    Cursor(None, 0, after, size)

  def page[T <: Identity](
      prev: Option[T],
      offset: Int,
      elements: Seq[T],
      limit: Int
  ): (Seq[T], Option[Cursor]) = {
    if (elements.isEmpty)
      (elements, None)
    else if (limit > 0 && offset > 0)
      (
        elements,
        Some(Cursor(prev.map(_.id), offset, Some(elements.last.id), limit))
      )
    else if (limit > 0)
      (elements, Some(Cursor.first(Some(elements.last.id), limit)))
    else if (offset > 0)
      (elements, Some(Cursor.last(prev.map(_.id), offset)))
    else (elements, None)
  }

  private def last(before: Option[Long], size: Int) =
    Cursor(before, size, None, 0)

  trait Identity {
    def id: Long
  }

  trait QueryParameter {
    def toQueryParameters: (Option[String], Option[String])
  }

}
case class Cursor(
    before: Option[Long],
    offset: Int,
    after: Option[Long],
    limit: Int
) extends Cursor.QueryParameter {

  override def toQueryParameters: (Option[String], Option[String]) = {
    (
      before.map(value => s"limit=$offset&before=$value"),
      after.map(value => s"limit=$limit&after=$value")
    )
  }
}
