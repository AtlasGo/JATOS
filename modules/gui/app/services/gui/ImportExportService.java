package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.gui.common.BadRequestException;
import exceptions.gui.common.ForbiddenException;
import general.gui.RequestScopeMessaging;
import models.common.Component;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.api.Application;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import utils.common.IOUtils;
import utils.common.JsonUtils;
import utils.common.ZipUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static play.libs.Files.TemporaryFile;

/**
 * Service class for JATOS Controllers (not Publix).
 *
 * @author Kristian Lange
 */
@Singleton
public class ImportExportService {

    private static final ALogger LOGGER = Logger.of(ImportExportService.class);

    public static final String TEMP_STUDY_DIR = "tempStudyDir";
    public static final String TEMP_COMPONENT_FILE = "tempComponentFile";

    private final Application app;
    private final Checker checker;
    private final StudyService studyService;
    private final ComponentService componentService;
    private final JsonUtils jsonUtils;
    private final IOUtils ioUtils;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;

    @Inject
    ImportExportService(Application app, Checker checker, StudyService studyService, ComponentService componentService,
            JsonUtils jsonUtils, IOUtils ioUtils, StudyDao studyDao, ComponentDao componentDao) {
        this.app = app;
        this.checker = checker;
        this.studyService = studyService;
        this.componentService = componentService;
        this.jsonUtils = jsonUtils;
        this.ioUtils = ioUtils;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
    }

    public ObjectNode importComponent(Study study, FilePart<TemporaryFile> filePart) throws IOException {
        if (filePart == null) throw new IOException("File missing");

        File file = filePart.getRef().path().toFile();

        // If wrong key the upload comes from the wrong form
        if (!filePart.getKey().equals(Component.COMPONENT)) {
            throw new IOException("Uploaded file isn't intended for components");
        }

        Component uploadedComponent = unmarshalComponent(file);
        boolean componentExists = componentDao.findByUuid(uploadedComponent.getUuid(), study).isPresent();

        // Move uploaded component file to Java's tmp folder
        Path source = file.toPath();
        Path target = getTempComponentFile(file.getName()).toPath();
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

        // Create JSON response
        ObjectNode objectNode = Json.mapper().createObjectNode();
        objectNode.put("componentExists", componentExists);
        objectNode.put("componentTitle", uploadedComponent.getTitle());
        objectNode.put(TEMP_COMPONENT_FILE, file.getName());
        return objectNode;
    }

    public Http.Request importComponentConfirmed(Http.Request request, Study study) throws IOException {
        String unzippedStudyDirName = request.session().getOptional(TEMP_COMPONENT_FILE).orElseThrow(() -> {
            LOGGER.error(".importStudyConfirmed: " + "missing component file name in session");
            return new IOException("Import of component failed");
        });
        File componentFile = new File(IOUtils.TMP_DIR, unzippedStudyDirName);
        Component uploadedComponent = unmarshalComponent(componentFile);
        Optional<Component> currentComponent = componentDao.findByUuid(uploadedComponent.getUuid(), study);
        if (currentComponent.isPresent()) {
            componentService.updateProperties(currentComponent.get(), uploadedComponent);
            request = RequestScopeMessaging.success(request,
                    "Properties of component \"" + uploadedComponent.getTitle() + "\" (ID " + currentComponent.get()
                            .getId() + ") were overwritten.");
        } else {
            componentService.createAndPersistComponent(study, uploadedComponent);
            request = RequestScopeMessaging.success(request,
                    "New component \"" + uploadedComponent.getTitle() + "\" (ID " + uploadedComponent.getId()
                            + ") imported.");
        }
        return request;
    }

    public void cleanupAfterComponentImport(Http.Session session) {
        session.getOptional(ImportExportService.TEMP_COMPONENT_FILE).ifPresent(tempFileName -> {
            File componentFile = new File(IOUtils.TMP_DIR, tempFileName);
            componentFile.delete();
        });
    }

    /**
     * Import a uploaded study: there are 5 possible cases: (udir - name of uploaded study asset dir, cdir - name of
     * current study asset dir)
     * <p>
     * 1) study exists - udir exists - udir == cdir : ask confirmation to overwrite study and/or dir 2) study exists -
     * udir exists - udir != cdir : ask confirmation to overwrite study and/or (dir && rename to cdir) 3) study exists -
     * !udir exists : shouldn't happen, ask confirmation to overwrite study 4) !study exists - udir exists : ask to
     * rename dir (generate new dir name) 5) !study exists - !udir exists : new study - write both
     */
    public ObjectNode importStudy(User loggedInUser, File file) throws IOException, ForbiddenException {
        File tempUnzippedStudyDir = unzipUploadedStudyFile(file);
        Study uploadedStudy = unmarshalStudy(tempUnzippedStudyDir, false);

        Optional<Study> currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());
        boolean uploadedDirExists = ioUtils.checkStudyAssetsDirExists(uploadedStudy.getDirName());
        if (currentStudy.isPresent() && !currentStudy.get().hasUser(loggedInUser)) {
            throw new ForbiddenException("The study \"" + currentStudy.get().getTitle() + "\" you're trying "
                    + "to upload already exists but you aren't a user of it. You can always import this study in "
                    + "another JATOS instance (e.g. <a href=\"http://www.jatos.org/Installation.html\">"
                    + "in your local instance</a>), clone it there, export it, " + "and import it here again.");
        }

        // Create JSON response
        ObjectNode responseJson = Json.mapper().createObjectNode();
        responseJson.put("studyExists", currentStudy.isPresent());
        if (currentStudy.isPresent()) {
            responseJson.put("currentStudyTitle", currentStudy.get().getTitle());
            responseJson.put("currentStudyUuid", currentStudy.get().getUuid());
            responseJson.put("currentDirName", currentStudy.get().getDirName());
        }
        responseJson.put("uploadedStudyTitle", uploadedStudy.getTitle());
        responseJson.put("uploadedStudyUuid", uploadedStudy.getUuid());
        responseJson.put("uploadedDirName", uploadedStudy.getDirName());
        responseJson.put("uploadedDirExists", uploadedDirExists);
        responseJson.put(TEMP_STUDY_DIR, tempUnzippedStudyDir.getName());
        if (!currentStudy.isPresent() && uploadedDirExists) {
            String newDirName = ioUtils.findNonExistingStudyAssetsDirName(uploadedStudy.getDirName());
            responseJson.put("newDirName", newDirName);
        }
        return responseJson;
    }

    public Http.Request importStudyConfirmed(Http.Request request, User loggedInUser, JsonNode json)
            throws IOException, ForbiddenException, BadRequestException {
        if (json == null || json.findPath("overwriteStudysProperties") == null || json.findPath("overwriteStudysDir")
                == null) {
            LOGGER.error(".importStudyConfirmed: " + "JSON is malformed");
            throw new IOException("Import of study failed");
        }
        Boolean overwriteStudysProperties = json.findPath("overwriteStudysProperties").asBoolean();
        Boolean overwriteStudysDir = json.findPath("overwriteStudysDir").asBoolean();
        boolean keepCurrentDirName = json.findPath("keepCurrentDirName").booleanValue();
        boolean renameDir = json.findPath("renameDir").booleanValue();

        String unzippedStudyDirName = request.session().getOptional(TEMP_STUDY_DIR).orElseThrow(() -> {
            LOGGER.error(".importStudyConfirmed: " + "missing unzipped study directory name in session");
            return new IOException("Import of study failed");
        });
        File tempUnzippedStudyDir = new File(IOUtils.TMP_DIR, unzippedStudyDirName);
        Study uploadedStudy = unmarshalStudy(tempUnzippedStudyDir, true);
        Optional<Study> currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());

        // 1) study exists  -  udir exists - udir == cdir
        // 2) study exists  -  udir exists - udir != cdir
        // 3) study exists  - !udir exists
        if (currentStudy.isPresent()) {
            return overwriteExistingStudy(request, loggedInUser, overwriteStudysProperties, overwriteStudysDir,
                    keepCurrentDirName, tempUnzippedStudyDir, uploadedStudy, currentStudy.get());
        }

        // 4) !study exists -  udir exists
        // 5) !study exists - !udir exists
        if (overwriteStudysProperties && overwriteStudysDir) {
            boolean uploadedDirExists = ioUtils.checkStudyAssetsDirExists(uploadedStudy.getDirName());
            if (uploadedDirExists && !renameDir) return request;
            if (renameDir) {
                String newDirName = ioUtils.findNonExistingStudyAssetsDirName(uploadedStudy.getDirName());
                uploadedStudy.setDirName(newDirName);
            }
            return importNewStudy(request, loggedInUser, tempUnzippedStudyDir, uploadedStudy);
        }

        return request;
    }

    public void cleanupAfterStudyImport(Http.Session session) {
        session.getOptional(TEMP_STUDY_DIR).ifPresent(unzippedStudyDirName -> {
            File tempUnzippedStudyDir = new File(IOUtils.TMP_DIR, unzippedStudyDirName);
            tempUnzippedStudyDir.delete();
        });
    }

    private Http.Request overwriteExistingStudy(Http.Request request, User loggedInUser, boolean overwriteStudysProperties,
            boolean overwriteStudysDir, boolean keepCurrentDirName, File tempUnzippedStudyDir, Study uploadedStudy,
            Study currentStudy) throws IOException, ForbiddenException, BadRequestException {
        checker.checkStandardForStudy(currentStudy, currentStudy.getId(), loggedInUser);
        checker.checkStudyLocked(currentStudy);

        if (overwriteStudysDir) {
            String dirName = keepCurrentDirName ? currentStudy.getDirName() : uploadedStudy.getDirName();
            request = moveStudyAssetsDir(request, tempUnzippedStudyDir, currentStudy, dirName);
            request = RequestScopeMessaging.success(request,
                    "Assets \"" + dirName + "\" of study \"" + currentStudy.getTitle() + "\" (ID " + currentStudy
                            .getId() + ") were overwritten.");
        }

        if (overwriteStudysProperties) {
            if (keepCurrentDirName || !overwriteStudysDir) {
                studyService.updateStudyWithoutDirName(currentStudy, uploadedStudy);
            } else {
                studyService.updateStudy(currentStudy, uploadedStudy);
            }
            updateStudysComponents(currentStudy, uploadedStudy);
            request = RequestScopeMessaging.success(request,
                    "Properties of of study \"" + currentStudy.getTitle() + "\" (ID " + currentStudy.getId()
                            + ") were overwritten.");
        }
        return request;
    }

    private Http.Request importNewStudy(Http.Request request, User loggedInUser, File tempUnzippedStudyDir, Study importedStudy)
            throws IOException {
        request = moveStudyAssetsDir(request, tempUnzippedStudyDir, null, importedStudy.getDirName());
        studyService.createAndPersistStudy(loggedInUser, importedStudy);
        return RequestScopeMessaging.success(request,
                "Newly imported study \"" + importedStudy.getTitle() + "\" (ID " + importedStudy.getId()
                        + ") with study assets \"" + importedStudy.getDirName() + "\"");
    }

    public File createStudyExportZipFile(Study study) throws IOException {
        String studyFileName = ioUtils.generateFileName(study.getTitle());
        String studyFileSuffix = "." + IOUtils.STUDY_FILE_SUFFIX;
        File studyAsJsonFile = File.createTempFile(studyFileName, studyFileSuffix);
        studyAsJsonFile.deleteOnExit();
        jsonUtils.studyAsJsonForIO(study, studyAsJsonFile);
        String studyAssetsDirPath = ioUtils.generateStudyAssetsPath(study.getDirName());
        File zipFile = ZipUtil.zipStudy(studyAssetsDirPath, study.getDirName(), studyAsJsonFile.getAbsolutePath());
        studyAsJsonFile.delete();
        return zipFile;
    }

    /**
     * Update the components of the current study with the one of the imported study.
     */
    private void updateStudysComponents(Study currentStudy, Study updatedStudy) {
        // Clear list and rebuild it from updated study
        List<Component> currentComponentList = new ArrayList<>(currentStudy.getComponentList());
        currentStudy.getComponentList().clear();

        for (Component updatedComponent : updatedStudy.getComponentList()) {
            Component currentComponent = null;
            // Find both matching components with the same UUID
            for (Component tempComponent : currentComponentList) {
                if (tempComponent.getUuid().equals(updatedComponent.getUuid())) {
                    currentComponent = tempComponent;
                    break;
                }
            }
            if (currentComponent != null) {
                componentService.updateProperties(currentComponent, updatedComponent);
                currentStudy.addComponent(currentComponent);
                currentComponentList.remove(currentComponent);
            } else {
                // If the updated component doesn't exist in the current study
                // add it.
                componentService.createAndPersistComponent(currentStudy, updatedComponent);
            }
        }

        // Check whether any component from the current study are left that
        // aren't in the updated study. Add them to the end of the list and
        // put them into inactive (we don't remove them, because they could be
        // associated with results)
        for (Component currentComponent : currentComponentList) {
            currentComponent.setActive(false);
            currentStudy.addComponent(currentComponent);
        }

        studyDao.update(currentStudy);
    }

    /**
     * Deletes current study assets' dir and moves imported study assets' dir from Java's temp dir to study assets root
     * dir
     */
    private Http.Request moveStudyAssetsDir(Http.Request request, File unzippedStudyDir, Study currentStudy,
            String studyAssetsDirName) throws IOException {
        if (currentStudy != null) {
            ioUtils.removeStudyAssetsDir(currentStudy.getDirName());
        }

        File[] dirArray = ioUtils.findDirectories(unzippedStudyDir);
        if (dirArray.length == 0) {
            // If a study assets dir is missing, create a new one.
            ioUtils.createStudyAssetsDir(studyAssetsDirName);
            request = RequestScopeMessaging.warning(request, "There is no directory in the ZIP file - new study assets created.");
        } else if (dirArray.length == 1) {
            File studyAssetsDir = dirArray[0];
            ioUtils.moveStudyAssetsDir(studyAssetsDir, studyAssetsDirName);
        } else {
            throw new IOException("There are more than one directory in the ZIP file.");
        }
        return request;
    }

    /**
     * Get component's File object. Name is stored in session. Discard session variable afterwards.
     */
    private File getTempComponentFile(String tempComponentFileName) {
        if (tempComponentFileName == null || tempComponentFileName.trim().isEmpty()) {
            return null;
        }
        return new File(IOUtils.TMP_DIR, tempComponentFileName);
    }

    private File unzipUploadedStudyFile(File file) throws IOException {
        File destDir;
        try {
            destDir = new File(IOUtils.TMP_DIR, "JatosImport_" + UUID.randomUUID().toString());
            ZipUtil.unzip(file, destDir);
        } catch (IOException e) {
            LOGGER.warn(".unzipUploadedStudyFile: unzipping failed", e);
            throw new IOException("Import of study failed");
        }
        return destDir;
    }

    private Component unmarshalComponent(File file) throws IOException {
        UploadUnmarshaller<Component> uploadUnmarshaller = app.injector().instanceOf(ComponentUploadUnmarshaller.class);
        Component component = uploadUnmarshaller.unmarshalling(file);
        try {
            componentService.validate(component);
        } catch (ValidationException e) {
            throw new IOException(e);
        }
        return component;
    }

    private Study unmarshalStudy(File tempDir, boolean deleteAfterwards) throws IOException {
        File[] studyFileList = ioUtils.findFiles(tempDir, "", IOUtils.STUDY_FILE_SUFFIX);
        if (studyFileList.length != 1) {
            throw new IOException("Study is invalid");
        }
        File studyFile = studyFileList[0];

        UploadUnmarshaller<Study> uploadUnmarshaller = app.injector().instanceOf(StudyUploadUnmarshaller.class);
        Study study = uploadUnmarshaller.unmarshalling(studyFile);

        try {
            studyService.validate(study);
        } catch (ValidationException e) {
            throw new IOException(e);
        }

        if (deleteAfterwards) {
            studyFile.delete();
        }
        return study;
    }

}
