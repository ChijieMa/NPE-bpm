package controllers

import scala.collection.mutable.ListBuffer

import scala.concurrent.ExecutionContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import play.api.mvc.Action
import play.api.mvc.Controller
import play.libs.Akka
import akka.pattern.ask
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope
import scala.concurrent.Await
import org.nights.npe.mcs.akka.Global
import java.util.concurrent.atomic.AtomicLong
import org.nights.npe.mcs.akka.QueryTasks
import scala.concurrent.duration.DurationInt
import org.nights.npe.mcs.akka.PollWorker
import com.fasterxml.jackson.databind.DeserializationFeature
import org.nights.npe.mo.SubmitStates
import scala.concurrent.Future
import org.nights.npe.po.StateContext
import org.nights.npe.po.ParentContext
import play.api.libs.json.Json
import org.nights.npe.po.DoneStateContext
import org.nights.npe.mo.TaskDone
import org.nights.npe.mo.ANewProcess
import org.nights.npe.po.ContextData
import java.util.UUID
import play.api.libs.json.Reads
import scala.collection.mutable.HashMap
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue

object QueueWorker
  extends Controller {
  import org.nights.npe.mcs.akka.JerksonHelper._

  implicit val simpleDbLookups: ExecutionContext = Akka.system.dispatchers.lookup("simple-db-lookups")

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  lazy val poller = Global.system.actorSelection("/user/poll");
  val counter = new AtomicLong(0)
  def obtainByRole(obtainer: String, role: String, center: String) = Action.async { request =>
    println("obtainByRole==" + role)
    poller.ask(QueryTasks(1, obtainer, role, obtainer))(10 seconds).map(result => {
      Ok(mapper.writeValueAsString(result))
    }) recover {
      case e: akka.pattern.AskTimeoutException => Ok("{}")
    }
  }
  def newProc(submiter: String, center: String, procdef: String) = Action { request =>
    val jsonbody = request.body.asJson.get


    val ss = Json.fromJson[ContextData](jsonbody)
    if (ss.isSuccess) {
      //ANewProcess(procInstId: String, submitter: String, procDefId: String, data: ContextData)
      println("success==" + ss)
      submitor ! ANewProcess(UUID.randomUUID().toString(), submiter, procdef, ss.get)
      Ok("{\"status\":0}")
    } else {
      println("error==" + ss)
      Ok("{\"status\":-1,\"msg\":" + mapper.writeValueAsString(ss) + "}")
    }
  }

  lazy val submitor = Global.system.actorSelection("/user/submitor");

  def submit = Action { request =>
    val jsonbody = request.body.asJson.get
    println("jsonboy==" + jsonbody)

    val ss = Json.fromJson[SubmitStates](jsonbody)

    if (ss.isSuccess) {
      println("success==" + ss)
      val sub = ss.get
      submitor ! DoneStateContext(sub.state, sub.ctxData, sub.submitter)
      Ok("{\"status\":0}")
    } else {
      println("error==" + ss)
      Ok("{\"status\":-1,\"msg\":" + ss + "}")
    }
  }
}