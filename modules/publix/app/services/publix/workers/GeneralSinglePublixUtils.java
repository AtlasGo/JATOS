package services.publix.workers;

import controllers.publix.workers.GeneralSinglePublix;
import daos.common.*;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import general.common.StudyLogger;
import group.GroupAdministration;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.Worker;
import play.mvc.Http;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * GeneralSinglePublix' implementation of PublixUtils
 *
 * @author Kristian Lange
 */
@Singleton
public class GeneralSinglePublixUtils extends PublixUtils<GeneralSingleWorker> {

    @Inject
    GeneralSinglePublixUtils(ResultCreator resultCreator, IdCookieService idCookieService,
            GroupAdministration groupAdministration, GeneralSingleErrorMessages errorMessages, StudyDao studyDao,
            StudyResultDao studyResultDao, ComponentDao componentDao, ComponentResultDao componentResultDao,
            WorkerDao workerDao, BatchDao batchDao, StudyLogger studyLogger) {
        super(resultCreator, idCookieService, groupAdministration, errorMessages, studyDao, studyResultDao,
                componentDao, componentResultDao, workerDao, batchDao, studyLogger);
    }

    @Override
    public GeneralSingleWorker retrieveTypedWorker(Long workerId) throws ForbiddenPublixException {
        Worker worker = super.retrieveWorker(workerId);
        if (!(worker instanceof GeneralSingleWorker)) {
            throw new ForbiddenPublixException(errorMessages.workerNotCorrectType(worker.getId()));
        }
        return (GeneralSingleWorker) worker;
    }

    @Override
    public Map<String, String> getNonJatosUrlQueryParameters(Http.Request request) {
        Map<String, String> queryMap = new HashMap<>();
        request.queryString().forEach((k, v) -> queryMap.put(k, v[0]));
        queryMap.remove(GeneralSinglePublix.GENERALSINGLE);
        queryMap.remove("batchId");
        queryMap.remove("pre");
        return queryMap;
    }

}
