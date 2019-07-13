package controllers.publix

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import batch.BatchChannelActor
import batch.BatchDispatcher.PoisonChannel
import batch.BatchDispatcherRegistry.{GetOrCreate, ItsThisOne}
import exceptions.publix.PublixException
import javax.inject.{Inject, Named, Singleton}
import models.common.workers._
import play.api.Logger
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import services.publix.idcookie.IdCookieService
import services.publix.workers._
import services.publix.{PublixUtils, StudyAuthorisation}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Abstract class that handles opening of the batch channel. It has concrete implementations for
  * each worker type.
  */
abstract class BatchChannel[A <: Worker](components: ControllerComponents,
                                         publixUtils: PublixUtils[A],
                                         studyAuthorisation: StudyAuthorisation[A])
  extends AbstractController(components) {

  private val logger: Logger = Logger(this.getClass)

  @Inject
  implicit var system: ActorSystem = _

  @Inject
  implicit var materializer: Materializer = _

  @Inject
  var idCookieService: IdCookieService = _

  @Inject
  @Named("batch-dispatcher-registry-actor")
  var batchDispatcherRegistry: ActorRef = _

  /**
    * Time to wait for an answer after asking an Akka actor
    */
  implicit val timeout: Timeout = 5.seconds

  /**
    * HTTP endpoint that opens a batch channel and returns a Akka stream Flow that will be turned
    * into WebSocket. In case of an error/problem an PublixException is thrown.
    */
  @throws(classOf[PublixException])
  def open(request: RequestHeader, studyId: Long, studyResultId: Long): Flow[Any, Nothing, _] = {
    logger.info(s".open: studyId $studyId, studyResultId $studyResultId")
    val idCookie = idCookieService.getIdCookie(request.asJava, studyResultId)
    val worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId)
    val study = publixUtils.retrieveStudy(studyId)
    val batch = publixUtils.retrieveBatch(idCookie.getBatchId)
    studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch)
    val studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId)

    // Get the BatchDispatcher that will handle this batch.
    val batchDispatcher = getOrCreateBatchDispatcher(batch.getId)
    // If this BatchDispatcher already has a batch channel for this
    // StudyResult, close the old one before opening a new one.
    closeBatchChannel(studyResult.getId, batchDispatcher)
    ActorFlow.actorRef { out => BatchChannelActor.props(out, studyResult.getId, batchDispatcher) }
  }

  /**
    * Asks the BatchDispatcherRegistry to get or create a batch dispatcher for the given ID. It
    * waits until it receives an answer. The answer is an ActorRef (to a BatchDispatcher).
    */
  private def getOrCreateBatchDispatcher(batchId: Long): ActorRef = {
    val future = batchDispatcherRegistry ? GetOrCreate(batchId)
    Await.result(future, timeout.duration).asInstanceOf[ItsThisOne].dispatcher
  }

  /**
    * Closes the batch channel that belongs to the given study result ID and is managed by the
    * given BatchDispatcher. Waits until it receives a result from the BatchDispatcher actor. It
    * returns true if the BatchChannel was managed by the BatchDispatcher and was successfully
    * removed from the BatchDispatcher, false otherwise (it was probably never managed by the
    * dispatcher).
    */
  private def closeBatchChannel(studyResultId: Long, batchDispatcher: ActorRef) = {
    val future = batchDispatcher ? PoisonChannel(studyResultId)
    Await.result(future, timeout.duration).asInstanceOf[Boolean]
  }

}

@Singleton
class JatosBatchChannel @Inject()(components: ControllerComponents,
                                  publixUtils: JatosPublixUtils,
                                  studyAuthorisation: JatosStudyAuthorisation)
  extends BatchChannel[JatosWorker](components, publixUtils, studyAuthorisation)

@Singleton
class PersonalSingleBatchChannel @Inject()(components: ControllerComponents,
                                           publixUtils: PersonalSinglePublixUtils,
                                           studyAuthorisation: PersonalSingleStudyAuthorisation)
  extends BatchChannel[PersonalSingleWorker](components, publixUtils, studyAuthorisation)

@Singleton
class PersonalMultipleBatchChannel @Inject()(components: ControllerComponents,
                                             publixUtils: PersonalMultiplePublixUtils,
                                             studyAuthorisation: PersonalMultipleStudyAuthorisation)
  extends BatchChannel[PersonalMultipleWorker](components, publixUtils, studyAuthorisation)

@Singleton
class GeneralSingleBatchChannel @Inject()(components: ControllerComponents,
                                          publixUtils: GeneralSinglePublixUtils,
                                          studyAuthorisation: GeneralSingleStudyAuthorisation)
  extends BatchChannel[GeneralSingleWorker](components, publixUtils, studyAuthorisation)

@Singleton
class GeneralMultipleBatchChannel @Inject()(components: ControllerComponents,
                                            publixUtils: GeneralMultiplePublixUtils,
                                            studyAuthorisation: GeneralMultipleStudyAuthorisation)
  extends BatchChannel[GeneralMultipleWorker](components, publixUtils, studyAuthorisation)

// Handles both MTWorker and MTSandboxWorker
@Singleton
class MTBatchChannel @Inject()(components: ControllerComponents,
                               publixUtils: MTPublixUtils,
                               studyAuthorisation: MTStudyAuthorisation)
  extends BatchChannel[MTWorker](components, publixUtils, studyAuthorisation)
