package controllers.publix;

import controllers.publix.actionannotation.IdCookiesAction;
import controllers.publix.actionannotation.PublixAccessLoggingAction.PublixAccessLogging;
import controllers.publix.workers.*;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.PublixException;
import models.common.workers.*;
import play.Application;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.idcookie.IdCookieModel;
import services.publix.idcookie.IdCookieService;
import utils.common.HttpUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;

import static controllers.publix.actionannotation.IdCookiesAction.*;
import static services.publix.idcookie.IdCookieAccessor.ID_COOKIES;

/**
 * Interceptor for Publix: it intercepts requests for JATOS' public API (Publix) and forwards them to one of the
 * implementations of the API (all extend Publix). Each implementation deals with different workers (e.g. workers from
 * MechTurk, Personal Multiple workers).
 * <p>
 * When a study is started the implementation to use is determined by parameters in the request's query string. Then the
 * worker type is put into JATOS' ID cookie (IdCookie) and used in subsequent requests of the same study run.
 * <p>
 * 1. Requests coming from Jatos' UI run (if clicked on run study/component button) run will be forwarded to
 * JatosPublix. They use JatosWorker.<br> 2. Requests coming from a Personal Single run will be forwarded to
 * PersonalSinglePublix. They use PersonalSingleWorker.<br> 3. Requests coming from a Personal Multiple run will be
 * forwarded to PersonalMultiplePublix. They use PersonalMultipleWorker.<br> 4. Requests coming from an General Single
 * run will be forwarded to GeneralSinglePublix. They use the GeneralSingleWorker.<br> 5. Requests coming from an
 * General Multiple run will be forwarded to GeneralMultiplePublix. They use the GeneralMultipleWorker.<br> 6. Requests
 * coming from MechTurk or MechTurk Sandbox will be forwarded to MTPublix. They use MTWorker and MTSandboxWorker.<br>
 *
 * @author Kristian Lange
 */
@Singleton
@PublixAccessLogging
public class PublixInterceptor extends Controller implements IPublix {

    private final IdCookieService idCookieService;
    private final Provider<Application> application;

    @Inject
    public PublixInterceptor(IdCookieService idCookieService, Provider<Application> application) {
        this.idCookieService = idCookieService;
        this.application = application;
    }

    @Override
    @IdCookies
    @Transactional
    public Result startStudy(Http.Request request, Long studyId, Long batchId) throws PublixException {
        Result result;
        String workerType = getWorkerTypeFromQuery(request);
        switch (workerType) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).startStudy(request, studyId, batchId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).startStudy(request, studyId, batchId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).startStudy(request, studyId, batchId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).startStudy(request, studyId, batchId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).startStudy(request, studyId, batchId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).startStudy(request, studyId, batchId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result startComponent(Http.Request request, Long studyId, Long componentId, Long studyResultId)
            throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).startComponent(request, studyId, componentId,
                        studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).startComponent(request, studyId, componentId,
                        studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).startComponent(request, studyId, componentId,
                        studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).startComponent(request, studyId, componentId,
                        studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).startComponent(request, studyId, componentId,
                        studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).startComponent(request, studyId, componentId, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result startComponentByPosition(Http.Request request, Long studyId, Integer position, Long studyResultId)
            throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).startComponentByPosition(request, studyId, position,
                        studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).startComponentByPosition(request, studyId,
                        position, studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).startComponentByPosition(request, studyId,
                        position, studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).startComponentByPosition(request, studyId,
                        position, studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).startComponentByPosition(request, studyId,
                        position, studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).startComponentByPosition(request, studyId, position,
                        studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result startNextComponent(Http.Request request, Long studyId, Long studyResultId) throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).startNextComponent(request, studyId, studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).startNextComponent(request, studyId,
                        studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).startNextComponent(request, studyId,
                        studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).startNextComponent(request, studyId,
                        studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).startNextComponent(request, studyId,
                        studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).startNextComponent(request, studyId, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result getInitData(Http.Request request, Long studyId, Long componentId, Long studyResultId)
            throws PublixException, IOException {
        Result result;
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).getInitData(request, studyId, componentId, studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).getInitData(request, studyId, componentId,
                        studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).getInitData(request, studyId, componentId,
                        studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).getInitData(request, studyId, componentId,
                        studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).getInitData(request, studyId, componentId,
                        studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).getInitData(request, studyId, componentId, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result setStudySessionData(Http.Request request, Long studyId, Long studyResultId) throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).setStudySessionData(request, studyId, studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).setStudySessionData(request, studyId,
                        studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).setStudySessionData(request, studyId,
                        studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).setStudySessionData(request, studyId,
                        studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).setStudySessionData(request, studyId,
                        studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).setStudySessionData(request, studyId, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result heartbeat(Http.Request request, Long studyId, Long studyResultId) throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).heartbeat(request, studyId, studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).heartbeat(request, studyId, studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).heartbeat(request, studyId, studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).heartbeat(request, studyId, studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).heartbeat(request, studyId, studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).heartbeat(request, studyId, studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result submitResultData(Http.Request request, Long studyId, Long componentId, Long studyResultId)
            throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).submitResultData(request, studyId, componentId,
                        studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).submitResultData(request, studyId, componentId,
                        studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).submitResultData(request, studyId, componentId,
                        studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).submitResultData(request, studyId, componentId,
                        studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).submitResultData(request, studyId, componentId,
                        studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).submitResultData(request, studyId, componentId,
                        studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result appendResultData(Http.Request request, Long studyId, Long componentId, Long studyResultId)
            throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).appendResultData(request, studyId, componentId,
                        studyResultId);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).appendResultData(request, studyId, componentId,
                        studyResultId);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).appendResultData(request, studyId, componentId,
                        studyResultId);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).appendResultData(request, studyId, componentId,
                        studyResultId);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).appendResultData(request, studyId, componentId,
                        studyResultId);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).appendResultData(request, studyId, componentId,
                        studyResultId);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result finishComponent(Http.Request request, Long studyId, Long componentId, Long studyResultId,
            Boolean successful, String errorMsg) throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).finishComponent(request, studyId, componentId,
                        studyResultId, successful, errorMsg);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).finishComponent(request, studyId, componentId,
                        studyResultId, successful, errorMsg);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).finishComponent(request, studyId, componentId,
                        studyResultId, successful, errorMsg);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).finishComponent(request, studyId, componentId,
                        studyResultId, successful, errorMsg);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).finishComponent(request, studyId, componentId,
                        studyResultId, successful, errorMsg);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).finishComponent(request, studyId, componentId, studyResultId,
                        successful, errorMsg);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result abortStudy(Http.Request request, Long studyId, Long studyResultId, String message)
            throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).abortStudy(request, studyId, studyResultId, message);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).abortStudy(request, studyId, studyResultId,
                        message);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).abortStudy(request, studyId, studyResultId,
                        message);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).abortStudy(request, studyId, studyResultId,
                        message);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).abortStudy(request, studyId, studyResultId,
                        message);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).abortStudy(request, studyId, studyResultId, message);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result finishStudy(Http.Request request, Long studyId, Long studyResultId, Boolean successful,
            String errorMsg) throws PublixException {
        Result result;
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                result = instanceOfPublix(JatosPublix.class).finishStudy(request, studyId, studyResultId, successful,
                        errorMsg);
                break;
            case PersonalSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalSinglePublix.class).finishStudy(request, studyId, studyResultId,
                        successful, errorMsg);
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(PersonalMultiplePublix.class).finishStudy(request, studyId, studyResultId,
                        successful, errorMsg);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralSinglePublix.class).finishStudy(request, studyId, studyResultId,
                        successful, errorMsg);
                break;
            case GeneralMultipleWorker.WORKER_TYPE:
                result = instanceOfPublix(GeneralMultiplePublix.class).finishStudy(request, studyId, studyResultId,
                        successful, errorMsg);
                break;
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                result = instanceOfPublix(MTPublix.class).finishStudy(request, studyId, studyResultId, successful,
                        errorMsg);
                break;
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
        return result;
    }

    @Override
    @IdCookies
    @Transactional
    public Result log(Http.Request request, Long studyId, Long componentId, Long studyResultId) throws PublixException {
        switch (getWorkerTypeFromIdCookie(request, studyResultId)) {
            case JatosWorker.WORKER_TYPE:
                return instanceOfPublix(JatosPublix.class).log(request, studyId, componentId, studyResultId);
            case PersonalSingleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalSinglePublix.class).log(request, studyId, componentId, studyResultId);
            case PersonalMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(PersonalMultiplePublix.class).log(request, studyId, componentId, studyResultId);
            case GeneralSingleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralSinglePublix.class).log(request, studyId, componentId, studyResultId);
            case GeneralMultipleWorker.WORKER_TYPE:
                return instanceOfPublix(GeneralMultiplePublix.class).log(request, studyId, componentId, studyResultId);
            // Handle MTWorker like MTSandboxWorker
            case MTSandboxWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
                return instanceOfPublix(MTPublix.class).log(request, studyId, componentId, studyResultId);
            default:
                throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
        }
    }

    /**
     * Uses Guice to create a new instance of the given class, a class that must inherit from Publix.
     */
    private <T extends Publix<?>> T instanceOfPublix(Class<T> publixClass) {
        return application.get().injector().instanceOf(publixClass);
    }

    /**
     * Checks JATOS' ID cookie for which type of worker is doing the study. Returns a String specifying the worker
     * type.
     */
    private String getWorkerTypeFromIdCookie(Http.Request request, Long studyResultId) throws PublixException {
        if (studyResultId == null) {
            throw new BadRequestPublixException("Study result doesn't exist.");
        }
        return idCookieService.getIdCookie(request, studyResultId).getWorkerType();
    }

    /**
     * Checks the request's query string which type of worker is doing the study. Returns a String specifying the worker
     * type. Before a study is started the worker type is specified via a parameter in the query string.
     */
    private String getWorkerTypeFromQuery(Http.Request request) throws BadRequestPublixException {
        // Check for JATOS worker
        String jatosWorkerId = HttpUtils.getQueryString(request, JatosPublix.JATOS_WORKER_ID);
        if (jatosWorkerId != null) {
            return JatosWorker.WORKER_TYPE;
        }
        // Check for MT worker and MT Sandbox worker
        String mtWorkerId = HttpUtils.getQueryString(request, MTPublix.MT_WORKER_ID);
        if (mtWorkerId != null) {
            return instanceOfPublix(MTPublix.class).retrieveWorkerType(request);
        }
        // Check for Personal Single Worker
        String personalSingleWorkerId = HttpUtils.getQueryString(request,
                PersonalSinglePublix.PERSONAL_SINGLE_WORKER_ID);
        if (personalSingleWorkerId != null) {
            return PersonalSingleWorker.WORKER_TYPE;
        }
        // Check for Personal Multiple Worker
        String pmWorkerId = HttpUtils.getQueryString(request, PersonalMultiplePublix.PERSONAL_MULTIPLE_WORKER_ID);
        if (pmWorkerId != null) {
            return PersonalMultipleWorker.WORKER_TYPE;
        }
        // Check for General Single Worker
        String generalSingle = HttpUtils.getQueryString(request, GeneralSinglePublix.GENERALSINGLE);
        if (generalSingle != null) {
            return GeneralSingleWorker.WORKER_TYPE;
        }
        // Check for General Multiple Worker
        String generalMultiple = HttpUtils.getQueryString(request, GeneralMultiplePublix.GENERALMULTIPLE);
        if (generalMultiple != null) {
            return GeneralMultipleWorker.WORKER_TYPE;
        }
        throw new BadRequestPublixException(PublixErrorMessages.UNKNOWN_WORKER_TYPE);
    }

}
