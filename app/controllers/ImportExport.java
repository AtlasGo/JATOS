package controllers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import play.mvc.Security;
import services.ErrorMessages;
import services.IOUtils;
import services.JsonUtils;
import services.JsonUtils.UploadUnmarshaller;
import services.PersistanceUtils;
import services.ZipUtil;

import com.fasterxml.jackson.databind.JsonNode;

import exceptions.ResultException;

/**
 * Cares for import/export of components and studies.
 * 
 * @author Kristian Lange
 */
@Security.Authenticated(Secured.class)
public class ImportExport extends Controller {

	public static final String STUDYS_DIR_CONFIRM = "studysDirConfirm";
	public static final String STUDYS_PROPERTIES_CONFIRM = "studysPropertiesConfirm";
	private static final String SESSION_UNZIPPED_STUDY_DIR = "tempStudyAssetsDir";
	private static final String SESSION_TEMP_COMPONENT_FILE = "tempComponentFile";
	private static final String CLASS_NAME = ImportExport.class.getSimpleName();

	/**
	 * HTTP Ajax request<br>
	 * Checks whether this is a legitimate study import, whether the study or
	 * its directory already exists. The actual import happens in
	 * importStudyConfirmed().
	 */
	@Transactional
	public static Result importStudy() throws ResultException {
		Logger.info(CLASS_NAME + ".importStudy: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		File tempUnzippedStudyDir = unzipUploadedFile();
		StudyModel uploadedStudy = unmarshalStudy(tempUnzippedStudyDir, false);

		// Remember study assets' dir name
		session(SESSION_UNZIPPED_STUDY_DIR, tempUnzippedStudyDir.getName());

		StudyModel currentStudy = StudyModel
				.findByUuid(uploadedStudy.getUuid());
		boolean studyExists = currentStudy != null;
		boolean dirExists = IOUtils.checkStudyAssetsDirExists(uploadedStudy
				.getDirName());
		if (studyExists && !currentStudy.hasMember(loggedInUser)) {
			String errorMsg = ErrorMessages.studyImportNotMember(currentStudy
					.getTitle());
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.FORBIDDEN);
		}
		if (dirExists
				&& (currentStudy == null || !currentStudy.getDirName().equals(
						uploadedStudy.getDirName()))) {
			String errorMsg = ErrorMessages
					.studyAssetsDirExistsBelongsToDifferentStudy(uploadedStudy
							.getDirName());
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.FORBIDDEN);
		}

		// Create response
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("studyExists", studyExists);
		response.put("studyTitle", uploadedStudy.getTitle());
		response.put("dirExists", dirExists);
		response.put("dirPath", uploadedStudy.getDirName());
		String asJson = JsonUtils.asJson(response);
		return ok(asJson);
	}

	/**
	 * HTTP Ajax request<br>
	 * Actual import of study and its study assets directory
	 */
	@Transactional
	public static Result importStudyConfirmed() throws ResultException {
		Logger.info(CLASS_NAME + ".importStudyConfirmed: "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		// Get confirmation: overwrite study's properties and/or study assets
		JsonNode json = request().body().asJson();
		if (json == null) {
			String errorMsg = ErrorMessages.IMPORT_OF_STUDY_FAILED;
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		Boolean studysPropertiesConfirm = json.findPath(
				STUDYS_PROPERTIES_CONFIRM).asBoolean();
		Boolean studysDirConfirm = json.findPath(STUDYS_DIR_CONFIRM)
				.asBoolean();
		if (studysPropertiesConfirm == null || studysDirConfirm == null) {
			String errorMsg = ErrorMessages.IMPORT_OF_STUDY_FAILED;
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.BAD_REQUEST);
		}

		File tempUnzippedStudyDir = getUnzippedStudyDir();
		StudyModel importedStudy = unmarshalStudy(tempUnzippedStudyDir, true);
		StudyModel currentStudy = StudyModel
				.findByUuid(importedStudy.getUuid());

		boolean studyExists = (currentStudy != null);
		if (studyExists) {
			ControllerUtils.checkStandardForStudy(currentStudy,
					currentStudy.getId(), loggedInUser);
			ControllerUtils.checkStudyLocked(currentStudy);
			if (studysDirConfirm) {
				if (studysPropertiesConfirm) {
					moveStudyAssetsDir(tempUnzippedStudyDir, currentStudy,
							importedStudy.getDirName(), loggedInUser);
				} else {
					// If we don't overwrite the properties, don't use the
					// updated study assets' dir name
					moveStudyAssetsDir(tempUnzippedStudyDir, currentStudy,
							currentStudy.getDirName(), loggedInUser);
				}
			}
			if (studysPropertiesConfirm) {
				if (studysDirConfirm) {
					PersistanceUtils.updateStudysProperties(currentStudy,
							importedStudy);
				} else {
					// If we don't overwrite the current study dir with the
					// uploaded one, don't change the study dir name in the
					// properties
					PersistanceUtils.updateStudysPropertiesWODirName(
							currentStudy, importedStudy);
				}
				PersistanceUtils.updateStudysComponents(currentStudy,
						importedStudy);
			}
			// TODO simplify if command; unify return command
			tempUnzippedStudyDir.delete();
			return ok(currentStudy.getId().toString());
		} else {
			moveStudyAssetsDir(tempUnzippedStudyDir, null,
					importedStudy.getDirName(), loggedInUser);
			PersistanceUtils.addStudy(importedStudy, loggedInUser);
			tempUnzippedStudyDir.delete();
			return ok(importedStudy.getId().toString());
		}
	}

	/**
	 * Get unzipped study dir File object stored in Java's temp directory. Name
	 * is stored in session. Discard session variable afterwards.
	 */
	private static File getUnzippedStudyDir() throws ResultException {
		String unzippedStudyDirName = session(SESSION_UNZIPPED_STUDY_DIR);
		session().remove(SESSION_UNZIPPED_STUDY_DIR);
		if (unzippedStudyDirName == null || unzippedStudyDirName.isEmpty()) {
			String errorMsg = ErrorMessages.IMPORT_OF_STUDY_FAILED;
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		File unzippedStudyDir = new File(System.getProperty("java.io.tmpdir"),
				unzippedStudyDirName);
		return unzippedStudyDir;
	}

	/**
	 * Deletes current study assets' dir and moves imported study assets' dir
	 * from Java's temp dir to study assets root dir
	 */
	private static void moveStudyAssetsDir(File unzippedStudyDir,
			StudyModel currentStudy, String studyAssetsDirName,
			UserModel loggedInUser) throws ResultException {
		try {
			if (currentStudy != null) {
				IOUtils.removeStudyAssetsDir(currentStudy.getDirName());
			}

			File[] dirArray = IOUtils.findDirectories(unzippedStudyDir);
			if (dirArray.length == 0) {
				// If a study assets dir is missing, create a new one.
				IOUtils.createStudyAssetsDir(studyAssetsDirName);
				// TODO send warning message
			} else if (dirArray.length == 1) {
				File studyAssetsDir = dirArray[0];
				IOUtils.moveStudyAssetsDir(studyAssetsDir, studyAssetsDirName);
			} else {
				String errorMsg = ErrorMessages.MORE_THAN_ONE_DIR_IN_ZIP;
				ControllerUtils.throwHomeResultException(errorMsg,
						Http.Status.BAD_REQUEST);
			}
		} catch (IOException e) {
			String errorMsg = "Study not imported: " + e.getMessage();
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
	}

	private static StudyModel unmarshalStudy(File tempDir,
			boolean deleteAfterwards) throws ResultException {
		File[] studyFileList = IOUtils.findFiles(tempDir, "",
				IOUtils.STUDY_FILE_SUFFIX);
		if (studyFileList.length != 1) {
			String errorMsg = ErrorMessages.STUDY_INVALID;
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		File studyFile = studyFileList[0];
		UploadUnmarshaller uploadUnmarshaller = new UploadUnmarshaller();
		StudyModel study = uploadUnmarshaller.unmarshalling(studyFile,
				StudyModel.class);
		if (study == null) {
			ControllerUtils.throwHomeResultException(
					uploadUnmarshaller.getErrorMsg(), Http.Status.BAD_REQUEST);
		}
		if (study.validate() != null) {
			String errorMsg = ErrorMessages.STUDY_INVALID;
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		if (deleteAfterwards) {
			studyFile.delete();
		}
		return study;
	}

	private static File unzipUploadedFile() throws ResultException {
		// Get file from request
		MultipartFormData mfd = request().body().asMultipartFormData();
		List<FilePart> filePartList = mfd.getFiles();
		if (filePartList.isEmpty()) {
			String errorMsg = ErrorMessages.FILE_MISSING;
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		FilePart filePart = filePartList.get(0);
		if (!filePart.getKey().equals(StudyModel.STUDY)) {
			// If wrong key the upload comes from wrong form
			String errorMsg = ErrorMessages.NO_STUDY_UPLOAD;
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.BAD_REQUEST);
		}

		File tempDir = null;
		try {
			tempDir = ZipUtil.unzip(filePart.getFile());
		} catch (IOException e1) {
			String errorMsg = ErrorMessages.IMPORT_OF_STUDY_FAILED;
			ControllerUtils.throwHomeResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return tempDir;
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result exportStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".exportStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME);
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		File zipFile = null;
		try {
			File studyAsJsonFile = File.createTempFile(
					IOUtils.generateFileName(study.getTitle()), "."
							+ IOUtils.STUDY_FILE_SUFFIX);
			studyAsJsonFile.deleteOnExit();
			JsonUtils.asJsonForIO(study, studyAsJsonFile);
			String studyAssetsDirPath = IOUtils.generateStudyAssetsPath(study
					.getDirName());
			zipFile = ZipUtil.zipStudy(studyAssetsDirPath, study.getDirName(),
					studyAsJsonFile.getAbsolutePath());
			studyAsJsonFile.delete();
		} catch (IOException e) {
			String errorMsg = ErrorMessages.studyExportFailure(studyId);
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}

		String zipFileName = IOUtils.generateFileName(study.getTitle(),
				IOUtils.ZIP_FILE_SUFFIX);
		response().setContentType("application/x-download");
		response().setHeader("Content-disposition",
				"attachment; filename=" + zipFileName);
		// Set cookie for jQuery.fileDownload plugin
		response().setCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME,
				ControllerUtils.JQDOWNLOAD_COOKIE_CONTENT);
		return ok(zipFile);
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result exportComponent(Long studyId, Long componentId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".exportComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME);
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ComponentModel component = ComponentModel.findById(componentId);
		ControllerUtils.checkStandardForComponents(studyId, componentId, study,
				loggedInUser, component);

		String componentAsJson = null;
		try {
			componentAsJson = JsonUtils.asJsonForIO(component);
		} catch (IOException e) {
			String errorMsg = ErrorMessages.componentExportFailure(componentId);
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}

		response().setContentType("application/x-download");
		String filename = IOUtils.generateFileName(component.getTitle(),
				IOUtils.COMPONENT_FILE_SUFFIX);
		response().setHeader("Content-disposition",
				"attachment; filename=" + filename);
		// Set cookie for jQuery.fileDownload plugin
		response().setCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME,
				ControllerUtils.JQDOWNLOAD_COOKIE_CONTENT);
		return ok(componentAsJson);
	}

	/**
	 * HTTP Ajax request<br>
	 * Checks whether this is a legitimate component import. The actual import
	 * happens in importComponentConfirmed().
	 */
	@Transactional
	public static Result importComponent(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".importComponent: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		MultipartFormData mfd = request().body().asMultipartFormData();
		List<FilePart> filePartList = mfd.getFiles();
		FilePart filePart = filePartList.get(0);
		// If wrong key the upload comes from the wrong form
		if (!filePart.getKey().equals(ComponentModel.COMPONENT)) {
			String errorMsg = ErrorMessages.NO_COMPONENT_UPLOAD;
			ControllerUtils.throwStudiesResultException(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}

		ComponentModel uploadedComponent = unmarshalComponent(
				filePart.getFile(), study);

		// Remember component's file name
		session(SESSION_TEMP_COMPONENT_FILE, filePart.getFile().getName());

		boolean componentExists = ComponentModel.findByUuid(uploadedComponent
				.getUuid()) != null;

		// Create response
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("componentExists", componentExists);
		response.put("componentTitle", uploadedComponent.getTitle());
		String asJson = JsonUtils.asJson(response);
		return ok(asJson);
	}

	/**
	 * HTTP Ajax request<br>
	 * Actual import of component
	 */
	@Transactional
	public static Result importComponentConfirmed(Long studyId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".importComponentConfirmed: " + "studyId "
				+ studyId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		ControllerUtils.checkStudyLocked(study);

		File componentFile = getTempComponentFile(study);
		ComponentModel uploadedComponent = unmarshalComponent(componentFile,
				study);
		ComponentModel currentComponent = ComponentModel
				.findByUuid(uploadedComponent.getUuid());
		boolean componentExists = (currentComponent != null);
		if (componentExists) {
			PersistanceUtils.updateComponentsProperties(currentComponent,
					uploadedComponent);
		} else {
			PersistanceUtils.addComponent(study, uploadedComponent);
		}
		return ok();
	}

	/**
	 * Get component's File object. Name is stored in session. Discard session
	 * variable afterwards.
	 */
	private static File getTempComponentFile(StudyModel study)
			throws ResultException {
		String tempComponentFileName = session(SESSION_TEMP_COMPONENT_FILE);
		session().remove(SESSION_TEMP_COMPONENT_FILE);
		if (tempComponentFileName == null || tempComponentFileName.isEmpty()) {
			String errorMsg = ErrorMessages.IMPORT_OF_COMPONENT_FAILED;
			ControllerUtils.throwStudiesResultException(errorMsg,
					Http.Status.BAD_REQUEST, study.getId());
		}
		File tempComponentFile = new File(System.getProperty("java.io.tmpdir"),
				tempComponentFileName);
		return tempComponentFile;
	}

	private static ComponentModel unmarshalComponent(File file, StudyModel study)
			throws ResultException {
		ComponentModel component = new UploadUnmarshaller().unmarshalling(file,
				ComponentModel.class);
		if (component == null) {
			String errorMsg = ErrorMessages.NO_COMPONENT_UPLOAD;
			ControllerUtils.throwStudiesResultException(errorMsg,
					Http.Status.BAD_REQUEST, study.getId());
		}
		if (component.validate() != null) {
			String errorMsg = ErrorMessages.COMPONENT_INVALID;
			ControllerUtils.throwStudiesResultException(errorMsg,
					Http.Status.BAD_REQUEST, study.getId());
		}
		return component;
	}

}
