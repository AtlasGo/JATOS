package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import controllers.gui.actionannotations.RefreshSessionCookieAction.RefreshSessionCookie;
import daos.common.BatchDao;
import daos.common.GroupResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.JatosGuiException;
import exceptions.gui.common.BadRequestException;
import exceptions.gui.common.ForbiddenException;
import exceptions.gui.common.NotFoundException;
import models.common.*;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import models.common.workers.Worker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http.Request;
import play.mvc.Result;
import services.gui.*;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Set;

/**
 * Controller for actions around StudyResults in the JATOS GUI.
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class StudyResults extends Controller {

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final JsonUtils jsonUtils;
    private final AuthenticationService authenticationService;
    private final BreadcrumbsService breadcrumbsService;
    private final ResultRemover resultRemover;
    private final ResultService resultService;
    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final GroupResultDao groupResultDao;
    private final WorkerDao workerDao;
    private final StudyResultDao studyResultDao;

    @Inject
    StudyResults(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
            AuthenticationService authenticationService, BreadcrumbsService breadcrumbsService,
            ResultRemover resultRemover, ResultService resultService, StudyDao studyDao, BatchDao batchDao,
            GroupResultDao groupResultDao, JsonUtils jsonUtils, WorkerDao workerDao, StudyResultDao studyResultDao) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.resultRemover = resultRemover;
        this.resultService = resultService;
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.groupResultDao = groupResultDao;
        this.jsonUtils = jsonUtils;
        this.workerDao = workerDao;
        this.studyResultDao = studyResultDao;
    }

    /**
     * Shows view with all StudyResults of a study.
     */
    @Transactional
    @Authenticated
    @RefreshSessionCookie
    public Result studysStudyResults(Request request, Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(request, e, study.getId());
        }

        String breadcrumbs = breadcrumbsService.generateForStudy(study, BreadcrumbsService.RESULTS);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByStudy(study.getId()).url();
        return ok(views.html.gui.result.studyResults
                .render(request, loggedInUser, breadcrumbs, HttpUtils.isLocalhost(request), study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a batch.
     */
    @Transactional
    @Authenticated
    @RefreshSessionCookie
    public Result batchesStudyResults(Request request, Long studyId, Long batchId, String workerType)
            throws JatosGuiException {
        Batch batch = batchDao.findById(batchId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(request, e, study.getId());
        }

        String breadcrumbsTitle = (workerType == null) ?
                BreadcrumbsService.RESULTS :
                BreadcrumbsService.RESULTS + " of " + Worker.getUIWorkerType(workerType) + " workers";
        String breadcrumbs = breadcrumbsService.generateForBatch(study, batch, breadcrumbsTitle);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByBatch(study.getId(), batch.getId(), workerType)
                .url();
        return ok(views.html.gui.result.studyResults
                .render(request, loggedInUser, breadcrumbs, HttpUtils.isLocalhost(request), study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a group.
     */
    @Transactional
    @Authenticated
    @RefreshSessionCookie
    public Result groupsStudyResults(Request request, Long studyId, Long groupId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForGroup(groupResult, study, groupId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(request, e, study.getId());
        }

        String breadcrumbsTitle = BreadcrumbsService.RESULTS;
        String breadcrumbs = breadcrumbsService.generateForGroup(study, groupResult.getBatch(), groupResult,
                breadcrumbsTitle);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByGroup(study.getId(), groupResult.getId()).url();
        return ok(views.html.gui.result.studyResults
                .render(request, loggedInUser, breadcrumbs, HttpUtils.isLocalhost(request), study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a worker.
     */
    @Transactional
    @Authenticated
    @RefreshSessionCookie
    public Result workersStudyResults(Request request, Long workerId) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Worker worker = workerDao.findById(workerId);
        try {
            checker.checkWorker(worker, workerId);
        } catch (BadRequestException e) {
            jatosGuiExceptionThrower.throwRedirect(request, e, controllers.gui.routes.Home.home(),
                    HttpUtils.isAjax(request));
        }

        String breadcrumbs = breadcrumbsService.generateForWorker(worker, BreadcrumbsService.RESULTS);
        return ok(views.html.gui.result.workersStudyResults
                .render(request, loggedInUser, breadcrumbs, HttpUtils.isLocalhost(request), worker));
    }

    /**
     * Ajax request
     * <p>
     * Removes all StudyResults specified in the parameter. The parameter is a comma separated list of of StudyResults
     * IDs as a String. Removing a StudyResult always removes it's ComponentResults.
     */
    @Transactional
    @Authenticated
    public Result remove(Request request, String studyResultIds) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            resultRemover.removeStudyResults(studyResultIds, loggedInUser);
        } catch (ForbiddenException | BadRequestException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax request
     * <p>
     * Removes all StudyResults of the given study.
     */
    @Transactional
    @Authenticated
    public Result removeAllOfStudy(Request request, Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(request, e, study.getId());
        }

        try {
            resultRemover.removeAllStudyResults(study, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax request
     * <p>
     * Removes all StudyResults that belong to the given worker and the logged-in user is allowed to delete (only if
     * he's a user of the study).
     */
    @Transactional
    @Authenticated
    public Result removeAllOfWorker(Request request, Long workerId) throws JatosGuiException {
        Worker worker = workerDao.findById(workerId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkWorker(worker, workerId);
        } catch (BadRequestException e) {
            jatosGuiExceptionThrower.throwRedirect(request, e, controllers.gui.routes.Home.home(),
                    HttpUtils.isAjax(request));
        }

        try {
            resultRemover.removeAllStudyResults(worker, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax request: Returns all StudyResults of a study in JSON format.
     */
    @Transactional
    @Authenticated
    public Result tableDataByStudy(Request request, Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        JsonNode dataAsJson = null;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            dataAsJson = jsonUtils.allStudyResultsForUI(studyResultDao.findAllByStudy(study));
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(dataAsJson);
    }

    /**
     * Ajax request: Returns all StudyResults of a batch in JSON format. As an additional parameter the worker type can
     * be specified and the results will only be of this type.
     */
    @Transactional
    @Authenticated
    public Result tableDataByBatch(Request request, Long studyId, Long batchId, String workerType)
            throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        JsonNode dataAsJson = null;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
            List<StudyResult> studyResultList = (workerType == null) ?
                    studyResultDao.findAllByBatch(batch) :
                    studyResultDao.findAllByBatchAndWorkerType(batch, workerType);
            // If worker type is MT then add MTSandbox on top
            if (MTWorker.WORKER_TYPE.equals(workerType)) {
                studyResultList.addAll(studyResultDao.findAllByBatchAndWorkerType(batch, MTSandboxWorker.WORKER_TYPE));
            }
            dataAsJson = jsonUtils.allStudyResultsForUI(studyResultList);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(dataAsJson);
    }

    /**
     * Ajax request: Returns all StudyResults of a group in JSON format.
     */
    @Transactional
    @Authenticated
    public Result tableDataByGroup(Request request, Long studyId, Long groupResultId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        JsonNode dataAsJson = null;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForGroup(groupResult, study, groupResultId);
            Set<StudyResult> studyResultList = groupResult.getActiveMemberList();
            studyResultList.addAll(groupResult.getHistoryMemberList());
            dataAsJson = jsonUtils.allStudyResultsForUI(studyResultList);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(dataAsJson);
    }

    /**
     * Ajax request: Returns all StudyResults belonging to a worker as JSON.
     */
    @Transactional
    @Authenticated
    public Result tableDataByWorker(Request request, Long workerId) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Worker worker = workerDao.findById(workerId);
        try {
            checker.checkWorker(worker, workerId);
        } catch (BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        List<StudyResult> allowedStudyResultList = resultService.getAllowedStudyResultList(loggedInUser, worker);
        JsonNode dataAsJson = jsonUtils.allStudyResultsForUI(allowedStudyResultList);
        return ok(dataAsJson);
    }

}
