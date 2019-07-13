package controllers.publix.workers;

import controllers.publix.IPublix;
import controllers.publix.PersonalMultipleGroupChannel;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.PublixException;
import general.common.StudyLogger;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.PersonalMultipleWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.PersonalMultipleErrorMessages;
import services.publix.workers.PersonalMultiplePublixUtils;
import services.publix.workers.PersonalMultipleStudyAuthorisation;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of JATOS' public API for studies run by PersonalMultipleWorker.
 *
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultiplePublix extends Publix<PersonalMultipleWorker> implements IPublix {

    public static final String PERSONAL_MULTIPLE_WORKER_ID = "personalMultipleWorkerId";

    private static final ALogger LOGGER = Logger.of(PersonalMultiplePublix.class);

    private final PersonalMultiplePublixUtils publixUtils;
    private final PersonalMultipleStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;
    private final StudyLogger studyLogger;

    @Inject
    PersonalMultiplePublix(JPAApi jpa, PersonalMultiplePublixUtils publixUtils,
            PersonalMultipleStudyAuthorisation studyAuthorisation, ResultCreator resultCreator,
            PersonalMultipleGroupChannel groupChannel, IdCookieService idCookieService,
            PersonalMultipleErrorMessages errorMessages, StudyAssets studyAssets, JsonUtils jsonUtils,
            ComponentResultDao componentResultDao, StudyResultDao studyResultDao, StudyLogger studyLogger) {
        super(jpa, publixUtils, studyAuthorisation, groupChannel, idCookieService, errorMessages, studyAssets,
                jsonUtils, componentResultDao, studyResultDao, studyLogger);
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.resultCreator = resultCreator;
        this.studyLogger = studyLogger;
    }

    @Override
    public Result startStudy(Http.Request request, Long studyId, Long batchId) throws PublixException {
        String workerIdStr = HttpUtils.getQueryString(request, PERSONAL_MULTIPLE_WORKER_ID);
        LOGGER.info(".startStudy: studyId " + studyId + ", " + "batchId " + batchId + ", " + "workerId " + workerIdStr);
        Study study = publixUtils.retrieveStudy(studyId);
        Batch batch = publixUtils.retrieveBatchByIdOrDefault(batchId, study);
        PersonalMultipleWorker worker = publixUtils.retrieveTypedWorker(workerIdStr);
        studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);

        request = publixUtils.finishOldestStudyResult(request);
        StudyResult studyResult = resultCreator.createStudyResult(study, batch, worker);
        publixUtils.setUrlQueryParameter(request.queryString(), studyResult);
        idCookieService.writeIdCookie(request, worker, batch, studyResult);

        Component firstComponent = publixUtils.retrieveFirstActiveComponent(study);
        studyLogger.log(study, "Started study run with " + PersonalMultipleWorker.UI_WORKER_TYPE + " worker", batch,
                worker);
        return redirect(controllers.publix.routes.PublixInterceptor
                .startComponent(studyId, firstComponent.getId(), studyResult.getId()));
    }

}
