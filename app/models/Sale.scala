package models

import java.time.LocalDateTime

case class Sale(id: Long, date: LocalDateTime) extends Cursor.Identity
