package models

import java.sql.Timestamp

import managers.DBWrapper.api._
import slick.lifted.{ForeignKeyQuery, ProvenShape}


/**
  * Representation of the UserSession model.
  *
  * @param id unique identifier of the model
  * @param uuid to store on the http session to access the session
  * @param expiration date of expiration for this session
  * @param user_id for which this session is valid
  */
case class UserSession(id: Option[Long], uuid: String, expiration: Option[Timestamp], user_id: Long)
  extends AbstractModel(id)


/**
  * Table in which to store sessions
  *
  * @param tag to give to the table
  */
class SessionTable(tag: Tag) extends AbstractTable[UserSession](tag, "sessions") {
  /**
    * Table containing users for joining
    */
  val users: TableQuery[UserTable] = TableQuery[UserTable]

  /**
    * Column containing the uuid.
    *
    * @return the column containing the uuid.
    */
  def uuid: Rep[String] = column[String]("uuid", O.Unique)

  /**
    * Column containing the expiration date
    *
    * @return the column containing the expiration date
    */
  def expiration: Rep[Timestamp] = column[Timestamp]("expiration")

  /**
    * Column containing the user id to which the sesison refer.
    *
    * @return the column containing the user id
    */
  def user_id: Rep[Long] = column[Long]("user_id")

  /**
    * User that the session references
    *
    * @return the foreign key to the user.
    */
  def user: ForeignKeyQuery[UserTable, User] =
    foreignKey("user_fk", user_id, users)(_.id, onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)

  override def * : ProvenShape[UserSession] =
    (id.?, uuid, expiration.?, user_id) <> (UserSession.tupled, UserSession.unapply)
}
