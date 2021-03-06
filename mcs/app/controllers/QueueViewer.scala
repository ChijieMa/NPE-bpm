package controllers

import play.api.mvc.Action
import play.api.mvc.Controller
import org.nights.npe.backend.db.TasksDAO
import play.libs.Akka
import scala.concurrent.ExecutionContext
import org.nights.npe.backend.db.SimpleDAO
import org.nights.npe.backend.db.KOProcdef
import org.nights.npe.backend.db.KOTasks
import scala.reflect.classTag
import scala.collection.mutable.ListBuffer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
case class KOQueueStat(val taskname: String = null, val interstate: Option[Int] = null, val counter: Option[Long] = null)
object QueueStatDAO extends SimpleDAO[KOQueueStat] {
  val ttag = classTag[KOQueueStat];
  val tablename = "tasks";
  val keyname = "taskinstid"
}
case class KOTimeoutWarn(val taskname: String = null, val interstate: Option[Int] = null, val taskinstid: String = null,
    		val obtainer: String = null, val obtaintime: Option[Long] = null,  val startMS: Option[Long] = null);

object TimeoutWarnDAO extends SimpleDAO[KOTimeoutWarn] {
  val ttag = classTag[KOTimeoutWarn];
  val tablename = "tasks";
  val keyname = "taskinstid"
}

object QueueViewer
  extends Controller {

  implicit val simpleDbLookups: ExecutionContext = Akka.system.dispatchers.lookup("simple-db-lookups")

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  def statsByName = Action.async { request =>
    val result = QueueStatDAO.exec("""select taskname,interstate,count(*) as counter 
         from tasks where nodetype = 0 group by taskname,interstate order by taskname,interstate
    		""", Seq.empty)

    result.map { qr =>
      val retList: ListBuffer[String] = ListBuffer.empty
      if (qr.rowsAffected > 0) {
        QueueStatDAO.resultRow(qr).asInstanceOf[List[KOQueueStat]].map { ko =>
          retList.+=:(mapper.writeValueAsString(ko))
        }
      }

      Ok(retList.mkString("[", ",", "]"))
    }
  }

  def warnByTimeout(timeout: Long, states: String) = Action.async { request =>
    val result = TimeoutWarnDAO.exec("""select * from tasks where obtaintime < ? and interstate in (?) order by obtaintime desc
    		""", Seq(System.currentTimeMillis() - timeout, states))

    result.map { qr =>
      val retList: ListBuffer[String] = ListBuffer.empty
      if (qr.rowsAffected > 0) {
        TimeoutWarnDAO.resultRow(qr).asInstanceOf[List[KOTimeoutWarn]].map { ko =>
          retList.+=:(mapper.writeValueAsString(ko))
        }
      }

      Ok(retList.mkString("[", ",", "]"))
    }
  }

  def statsByCenter = Action.async { request =>
    val result = QueueStatDAO.exec("""select taskcenter as taskname,interstate,count(*) as counter 
         from tasks where nodetype = 0  group by taskcenter,interstate order by taskcenter,interstate
    		""", Seq.empty)

    result.map { qr =>
      val retList: ListBuffer[String] = ListBuffer.empty
      if (qr.rowsAffected > 0) {
        QueueStatDAO.resultRow(qr).asInstanceOf[List[KOQueueStat]].map { ko =>
          retList.+=:(mapper.writeValueAsString(ko))
        }
      }

      Ok(retList.mkString("[", ",", "]"))
    }
  }
}