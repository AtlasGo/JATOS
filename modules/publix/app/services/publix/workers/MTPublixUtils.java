package services.publix.workers;

import controllers.publix.workers.MTPublix;
import daos.common.*;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import general.common.StudyLogger;
import group.GroupAdministration;
import models.common.workers.MTWorker;
import models.common.workers.Worker;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

/**
 * MTPublix' implementation of PublixUtils (studies started via MTurk).
 *
 * @author Kristian Lange
 */
@Singleton
public class MTPublixUtils extends PublixUtils<MTWorker> {

    @Inject
    MTPublixUtils(ResultCreator resultCreator, IdCookieService idCookieService, GroupAdministration groupAdministration,
            MTErrorMessages errorMessages, StudyDao studyDao, StudyResultDao studyResultDao, ComponentDao componentDao,
            ComponentResultDao componentResultDao, WorkerDao workerDao, BatchDao batchDao, StudyLogger studyLogger) {
        super(resultCreator, idCookieService, groupAdministration, errorMessages, studyDao, studyResultDao,
                componentDao, componentResultDao, workerDao, batchDao, studyLogger);
    }

    @Override
    public MTWorker retrieveTypedWorker(Long workerId) throws ForbiddenPublixException {
        Worker worker = super.retrieveWorker(workerId);
        if (!(worker instanceof MTWorker)) {
            throw new ForbiddenPublixException(errorMessages.workerNotCorrectType(worker.getId()));
        }
        return (MTWorker) worker;
    }

    @Override
    public Map<String, String[]> getNonJatosUrlQueryParameters(Map<String, String[]> queryParameters) {
        queryParameters.remove(MTPublix.ASSIGNMENT_ID);
        queryParameters.remove("hitId");
        queryParameters.remove("turkSubmitTo");
        queryParameters.remove("batchId");
        return queryParameters;
    }

}
