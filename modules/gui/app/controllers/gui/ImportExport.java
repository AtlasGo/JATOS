package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.common.BadRequestException;
import exceptions.gui.common.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.common.NotFoundException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.libs.Files.TemporaryFile;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import services.gui.*;
import utils.common.HttpUtils;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller that cares for import/export of components, studies and their result data.
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class ImportExport extends Controller {

    private static final ALogger LOGGER = Logger.of(ImportExport.class);

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final AuthenticationService authenticationService;
    private final ImportExportService importExportService;
    private final ResultDataExportService resultDataExportService;
    private final IOUtils ioUtils;
    private final JsonUtils jsonUtils;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final WorkerDao workerDao;

    @Inject
    ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker, IOUtils ioUtils,
            JsonUtils jsonUtils, AuthenticationService authenticationService, ImportExportService importExportService,
            ResultDataExportService resultDataStringGenerator, StudyDao studyDao, ComponentDao componentDao,
            WorkerDao workerDao) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.jsonUtils = jsonUtils;
        this.ioUtils = ioUtils;
        this.authenticationService = authenticationService;
        this.importExportService = importExportService;
        this.resultDataExportService = resultDataStringGenerator;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.workerDao = workerDao;
    }

    /**
     * Ajax request
     * <p>
     * Checks whether this is a legitimate study import, whether the study or its directory already exists. The actual
     * import happens in importStudyConfirmed(). Returns JSON.
     */
    @Transactional
    @Authenticated
    public Result importStudy(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser(request);

        // Get file from request
        MultipartFormData<TemporaryFile> body = request.body().asMultipartFormData();
        FilePart<TemporaryFile> filePart = body.getFile(Study.STUDY);
        if (filePart == null) {
            return badRequest("File missing");
        }
        if (!Study.STUDY.equals(filePart.getKey())) {
            // If wrong key the upload comes from wrong form
            return badRequest("Uploaded file isn't intended for studies");
        }

        JsonNode responseJson;
        try {
            File file = filePart.getRef().path().toFile();
            responseJson = importExportService.importStudy(loggedInUser, file);
        } catch (Exception e) {
            importExportService.cleanupAfterStudyImport(request.session());
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }
        // Remember study assets' dir name in session
        String tmpDirName = responseJson.get(ImportExportService.TEMP_STUDY_DIR).asText();
        return ok(responseJson).addingToSession(request, ImportExportService.TEMP_STUDY_DIR, tmpDirName);
    }

    /**
     * Ajax request
     * <p>
     * Actual import of study and its study assets directory. Always subsequent of an importStudy() call.
     */
    @Transactional
    @Authenticated
    public Result importStudyConfirmed(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser(request);

        // Get confirmation: overwrite study's properties and/or study assets
        JsonNode json = request.body().asJson();
        try {
            request = importExportService.importStudyConfirmed(request, loggedInUser, json);
        } catch (Exception e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage()).removingFromSession(request,
                    ImportExportService.TEMP_STUDY_DIR);
        } finally {
            importExportService.cleanupAfterStudyImport(request.session());
        }
        return ok(RequestScopeMessaging.getAsJson(request)).removingFromSession(request,
                ImportExportService.TEMP_STUDY_DIR);
    }

    /**
     * Ajax request
     * <p>
     * Export a study. Returns a .zip file that contains the study asset directory and the study as JSON as a .jas
     * file.
     */
    @Transactional
    @Authenticated
    public Result exportStudy(Http.Request request, Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        File zipFile;
        try {
            zipFile = importExportService.createStudyExportZipFile(study);
        } catch (IOException e) {
            String errorMsg = "Export of study \"" + study.getTitle() + "\" (ID " + studyId + ") failed.";
            LOGGER.error(".exportStudy: " + errorMsg, e);
            return status(HttpUtils.getHttpStatus(e), errorMsg);
        }

        String zipFileName = ioUtils.generateFileName(study.getTitle(), IOUtils.ZIP_FILE_SUFFIX);
        return ok(zipFile).as("application/x-download").withHeader("Content-disposition",
                "attachment; filename=" + zipFileName);
    }

    /**
     * Ajax request
     * <p>
     * Export of a component. Returns a .jac file with the component in JSON.
     */
    @Transactional
    @Authenticated
    public Result exportComponent(Http.Request request, Long studyId, Long componentId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        JsonNode componentAsJson;
        try {
            componentAsJson = jsonUtils.componentAsJsonForIO(component);
        } catch (IOException e) {
            String errorMsg =
                    "Failure during export of component \"" + component.getTitle() + "\" (ID " + componentId + ")";
            return status(HttpUtils.getHttpStatus(e), errorMsg);
        }

        String filename = ioUtils.generateFileName(component.getTitle(), IOUtils.COMPONENT_FILE_SUFFIX);
        return ok(componentAsJson).as("application/x-download").withHeader("Content-disposition",
                "attachment; filename=" + filename);
    }

    /**
     * Ajax request
     * <p>
     * Checks whether this is a legitimate component import. The actual import happens in importComponentConfirmed().
     * Returns JSON with the results.
     */
    @Transactional
    @Authenticated
    public Result importComponent(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        ObjectNode json;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);

            MultipartFormData<TemporaryFile> body = request.body().asMultipartFormData();
            FilePart<TemporaryFile> filePart = body.getFile(Component.COMPONENT);
            json = importExportService.importComponent(study, filePart);
        } catch (ForbiddenException | BadRequestException | IOException e) {
            importExportService.cleanupAfterComponentImport(request.session());
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }
        // Remember component's file name
        String tmpFileName = json.get(ImportExportService.TEMP_COMPONENT_FILE).asText();
        return ok(json).addingToSession(request, ImportExportService.TEMP_COMPONENT_FILE, tmpFileName);
    }

    /**
     * Ajax request
     * <p>
     * Actual import of component.
     */
    @Transactional
    @Authenticated
    public Result importComponentConfirmed(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);

        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);

            request = importExportService.importComponentConfirmed(request, study);
        } catch (Exception e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        } finally {
            importExportService.cleanupAfterComponentImport(request.session());
        }
        return ok(RequestScopeMessaging.getAsJson(request));
    }

    /**
     * Ajax request (uses download.js on the client side)
     * <p>
     * Returns all result data of ComponentResults belonging to the given StudyResults. The StudyResults are specified
     * by their IDs in the request's body. Returns the result data as text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfStudyResults(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser(request);
        List<Long> studyResultIdList = new ArrayList<>();
        request.body().asJson().get("resultIds").forEach(node -> studyResultIdList.add(node.asLong()));
        String resultData;
        try {
            resultData = resultDataExportService.fromStudyResultIdList(studyResultIdList, loggedInUser);
        } catch (ForbiddenException | BadRequestException | NotFoundException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }
        return ok(resultData);
    }

    /**
     * Ajax request  (uses download.js on the client side)
     * <p>
     * Returns all result data of ComponentResults belonging to StudyResults belonging to the given study. Returns the
     * result data as text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfAllStudyResults(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        String resultData;
        try {
            resultData = resultDataExportService.forStudy(loggedInUser, study);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }
        return ok(resultData);
    }

    /**
     * Ajax request (uses download.js on the client side)
     * <p>
     * Returns all result data of ComponentResults. The ComponentResults are specified by their IDs in the request's
     * body. Returns the result data as text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfComponentResults(Http.Request request) {
        User loggedInUser = authenticationService.getLoggedInUser(request);
        List<Long> componentResultIdList = new ArrayList<>();
        request.body().asJson().get("resultIds").forEach(node -> componentResultIdList.add(node.asLong()));
        String resultData;
        try {
            resultData = resultDataExportService.fromComponentResultIdList(componentResultIdList, loggedInUser);
        } catch (ForbiddenException | BadRequestException | NotFoundException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }
        return ok(resultData);
    }

    /**
     * Ajax request (uses download.js on the client side)
     * <p>
     * Returns all result data of ComponentResults belonging to the given component and study. Returns the result data
     * as text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportDataOfAllComponentResults(Http.Request request, Long studyId, Long componentId) {
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Study study = studyDao.findById(studyId);
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        String resultData;
        try {
            resultData = resultDataExportService.forComponent(loggedInUser, component);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        return ok(resultData);
    }

    /**
     * Ajax request (uses download.js on the client side)
     * <p>
     * Returns all result data of ComponentResults belonging to the given worker's StudyResults. Returns the result data
     * as text, each line a result data.
     */
    @Transactional
    @Authenticated
    public Result exportAllResultDataOfWorker(Http.Request request, Long workerId) {
        Worker worker = workerDao.findById(workerId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            checker.checkWorker(worker, workerId);
        } catch (BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        String resultData;
        try {
            resultData = resultDataExportService.forWorker(loggedInUser, worker);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }
        return ok(resultData);
    }

}
