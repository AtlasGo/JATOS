package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import controllers.gui.actionannotations.RefreshSessionCookieAction;
import controllers.gui.actionannotations.RefreshSessionCookieAction.RefreshSessionCookie;
import daos.common.BatchDao;
import daos.common.GroupResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import exceptions.gui.common.BadRequestException;
import exceptions.gui.common.ForbiddenException;
import general.gui.Messages;
import general.gui.RequestScopeMessaging;
import models.common.Batch;
import models.common.GroupResult;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import models.gui.BatchProperties;
import models.gui.BatchSession;
import models.gui.GroupSession;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.libs.F.Function3;
import play.mvc.Controller;
import play.mvc.Http.Request;
import play.mvc.Result;
import services.gui.*;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for all actions regarding batches and runs within the JATOS GUI.
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class Batches extends Controller {

    private final Checker checker;
    private final JsonUtils jsonUtils;
    private final AuthenticationService authenticationService;
    private final WorkerService workerService;
    private final BatchService batchService;
    private final GroupService groupService;
    private final BreadcrumbsService breadcrumbsService;
    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final StudyResultDao studyResultDao;
    private final GroupResultDao groupResultDao;
    private final FormFactory formFactory;

    @Inject
    Batches(Checker checker, JsonUtils jsonUtils, AuthenticationService authenticationService,
            WorkerService workerService, BatchService batchService, GroupService groupService,
            BreadcrumbsService breadcrumbsService, StudyDao studyDao, BatchDao batchDao, StudyResultDao studyResultDao,
            GroupResultDao groupResultDao, FormFactory formFactory) {
        this.checker = checker;
        this.jsonUtils = jsonUtils;
        this.authenticationService = authenticationService;
        this.workerService = workerService;
        this.batchService = batchService;
        this.groupService = groupService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.studyResultDao = studyResultDao;
        this.groupResultDao = groupResultDao;
        this.formFactory = formFactory;
    }

    /**
     * GET request to get the Worker & Batch Manager page
     */
    @Transactional
    @Authenticated
    @RefreshSessionCookie
    public Result workerAndBatchManager(Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            return redirect(routes.Studies.study(studyId)).flashing(Messages.ERROR, e.getMessage());
        }

        int allWorkersSize = study.getBatchList().stream().mapToInt(b -> b.getWorkerList().size()).sum();
        String breadcrumbs = breadcrumbsService.generateForStudy(study, BreadcrumbsService.WORKER_AND_BATCH_MANAGER);
        URL jatosURL = HttpUtils.getHostUrl(request);
        return ok(views.html.gui.workerAndBatch.workerAndBatchManager
                .render(request, loggedInUser, breadcrumbs, HttpUtils.isLocalhost(request), study, jatosURL,
                        allWorkersSize));
    }

    /**
     * Ajax GET request: Returns the Batch belonging to the given study as JSON. It includes the count of its
     * StudyResults.
     */
    @Transactional
    @Authenticated
    public Result batchById(Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        Integer resultCount = studyResultDao.countByBatch(batch);
        Integer groupCount = groupResultDao.countByBatch(batch);
        return ok(jsonUtils.getBatchByStudyForUI(batch, resultCount, groupCount));
    }

    /**
     * Ajax GET request: Returns all Batches of the given study as JSON. It includes the count of their StudyResults,
     * count of their GroupResults, and the count of their Workers.
     */
    @Transactional
    @Authenticated
    public Result batchesByStudy(Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        List<Batch> batchList = study.getBatchList();
        List<Integer> resultCountList = new ArrayList<>();
        batchList.forEach(batch -> resultCountList.add(studyResultDao.countByBatch(batch)));
        List<Integer> groupCountList = new ArrayList<>();
        batchList.forEach(batch -> groupCountList.add(groupResultDao.countByBatch(batch)));
        return ok(jsonUtils.allBatchesByStudyForUI(batchList, resultCountList, groupCountList));
    }

    /**
     * Ajax GET request: Returns a list of groups that belong to a batch as JSON
     */
    @Transactional
    @Authenticated
    public Result groupsByBatch(Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Batch batch = batchDao.findById(batchId);
        JsonNode dataAsJson;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
            dataAsJson = jsonUtils.allGroupResultsForUI(groupResultDao.findAllByBatch(batch));
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        return ok(dataAsJson);
    }

    /**
     * Ajax POST request to submit created Batch
     */
    @Transactional
    @Authenticated
    public Result submitCreated(Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        Form<BatchProperties> form = formFactory.form(BatchProperties.class).bindFromRequest(request);
        if (form.hasErrors()) {
            return badRequest(form.errorsAsJson());
        }
        BatchProperties batchProperties = form.get();
        Batch batch = batchService.bindToBatch(batchProperties);

        batchService.createAndPersistBatch(batch, study);
        return ok(batch.getId().toString());
    }

    /**
     * Ajax GET request to get the batch session data as String
     */
    @Transactional
    @Authenticated
    public Result batchSessionData(Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        BatchSession batchSession = batchService.bindToBatchSession(batch);
        return ok(jsonUtils.asJsonNode(batchSession));
    }

    /**
     * Ajax GET request to get the group session data as String
     */
    @Transactional
    @Authenticated
    public Result groupSessionData(Request request, Long studyId, Long groupResultId) {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForGroup(groupResult, study, groupResultId);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        GroupSession groupSession = groupService.bindToGroupSession(groupResult);
        return ok(jsonUtils.asJsonNode(groupSession));
    }

    /**
     * Ajax POST request to submit changed batch session data
     */
    @Transactional
    @Authenticated
    public Result submitEditedBatchSessionData(Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        Form<BatchSession> form = formFactory.form(BatchSession.class).bindFromRequest(request);
        if (form.hasErrors()) {
            return badRequest(form.errorsAsJson());
        }

        BatchSession batchSession = form.get();
        boolean success = batchService.updateBatchSession(batch.getId(), batchSession);
        if (!success) {
            return forbidden("The Batch Session has been updated since you "
                    + "loaded this page. Reload before trying to save again.");
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax POST request to submit changed group session data
     */
    @Transactional
    @Authenticated
    public Result submitEditedGroupSessionData(Request request, Long studyId, Long groupResultId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForGroup(groupResult, study, groupResultId);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        Form<GroupSession> form = formFactory.form(GroupSession.class).bindFromRequest(request);
        if (form.hasErrors()) {
            return badRequest(form.errorsAsJson());
        }

        GroupSession groupSession = form.get();
        boolean success = groupService.updateGroupSession(groupResult.getId(), groupSession);
        if (!success) {
            return forbidden("The Group Session has been updated since you "
                    + "loaded this page. Reload before trying to save again.");
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax GET request to get BatchProperties as JSON
     */
    @Transactional
    @Authenticated
    public Result properties(Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        BatchProperties batchProperties = batchService.bindToProperties(batch);
        return ok(jsonUtils.asJsonNode(batchProperties));
    }

    /**
     * Ajax POST request to submit changed BatchProperties
     */
    @Transactional
    @Authenticated
    public Result submitEditedProperties(Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Batch currentBatch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(currentBatch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        Form<BatchProperties> form = formFactory.form(BatchProperties.class).bindFromRequest(request);
        if (form.hasErrors()) {
            return badRequest(form.errorsAsJson());
        }
        BatchProperties batchProperties = form.get();
        // Have to bind ALLOWED_WORKER_TYPES from checkboxes by hand
        String[] allowedWorkerArray = request.body().asFormUrlEncoded().get(BatchProperties.ALLOWED_WORKER_TYPES);
        if (allowedWorkerArray != null) {
            Arrays.stream(allowedWorkerArray).forEach(batchProperties::addAllowedWorkerType);
        }

        batchService.updateBatch(currentBatch, batchProperties);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax POST request: Request to change the property 'active' of the given batch.
     */
    @Transactional
    @Authenticated
    public Result toggleActive(Request request, Long studyId, Long batchId, Boolean active) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        if (active != null) {
            batch.setActive(active);
            batchDao.update(batch);
        }
        return ok(jsonUtils.asJsonNode(batch.isActive()));
    }

    /**
     * Ajax POST request: Request to allow or deny a worker type in a batch.
     */
    @Transactional
    @Authenticated
    public Result toggleAllowedWorkerType(Request request, Long studyId, Long batchId, String workerType,
            Boolean allow) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        if (allow != null && workerType != null) {
            if (allow) {
                batch.addAllowedWorkerType(workerType);
            } else {
                batch.removeAllowedWorkerType(workerType);
            }
            batchDao.update(batch);
        } else {
            return badRequest();
        }
        return ok(jsonUtils.asJsonNode(batch.getAllowedWorkerTypes()));
    }

    /**
     * Ajax POST request to remove a Batch
     */
    @Transactional
    @Authenticated
    public Result remove(Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(batch, study, batchId);
            checker.checkDefaultBatch(batch);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        batchService.remove(batch);
        return ok(RequestScopeMessaging.getAsJson(request));
    }

    /**
     * Ajax POST request: Creates PersonalSingleWorkers and returns their worker IDs
     */
    @Transactional
    @Authenticated
    public Result createPersonalSingleRun(Request request, Long studyId, Long batchId) {
        Function3<String, Integer, Batch, List<? extends Worker>> createAndPersistWorker =
                workerService::createAndPersistPersonalSingleWorker;
        return createPersonalRun(request, studyId, batchId, createAndPersistWorker);
    }

    /**
     * Ajax POST request: Creates PersonalMultipleWorker and returns their worker IDs
     */
    @Transactional
    @Authenticated
    public Result createPersonalMultipleRun(Request request, Long studyId, Long batchId) {
        Function3<String, Integer, Batch, List<? extends Worker>> createAndPersistWorker =
                workerService::createAndPersistPersonalMultipleWorker;
        return createPersonalRun(request, studyId, batchId, createAndPersistWorker);
    }

    /**
     * This method creates either PersonalSingleWorker or PersonalMultipleWorker. Both workers are very similar and can
     * be created the same way (with the exception of the actual creation for which a function reference is passed).
     */
    private Result createPersonalRun(Request request, Long studyId, Long batchId,
            Function3<String, Integer, Batch, List<? extends Worker>> createAndPersistWorker) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        JsonNode json = request.body().asJson();
        String comment = json.findPath("comment").asText().trim();
        int amount = json.findPath("amount").asInt();
        List<Long> workerIdList;
        try {
            List<? extends Worker> workerList = createAndPersistWorker.apply(comment, amount, batch);
            workerIdList = workerList.stream().map(Worker::getId).collect(Collectors.toList());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Throwable e) {
            return internalServerError();
        }

        return ok(jsonUtils.asJsonNode(workerIdList));
    }

    /**
     * Ajax GET request: Returns a list of workers for a study and a batch as JSON
     */
    @Transactional
    @Authenticated
    public Result workerSetupData(Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        Map<String, Integer> studyResultCountsPerWorker = workerService.retrieveStudyResultCountsPerWorker(batch);
        JsonNode workerSetupData = jsonUtils.workerSetupData(batch, studyResultCountsPerWorker);
        return ok(workerSetupData);
    }

}
