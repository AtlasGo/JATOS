package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import gui.AbstractGuiTest;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.workers.ClosedStandaloneWorker;
import models.workers.JatosWorker;
import models.workers.TesterWorker;

import org.fest.assertions.Fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import play.db.jpa.JPA;
import services.gui.MessagesStrings;
import services.gui.StudyService;
import utils.IOUtils;
import common.Global;

/**
 * Tests StudyService
 * 
 * @author Kristian Lange
 */
public class StudyServiceTest extends AbstractGuiTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	private StudyService studyService;

	@Override
	public void before() throws Exception {
		studyService = Global.INJECTOR.getInstance(StudyService.class);
		mockContext();
		// Don't know why, but we have to bind entityManager again
		JPA.bindForCurrentThread(entityManager);
	}

	@Override
	public void after() throws Exception {
		JPA.bindForCurrentThread(null);
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void checkCloneStudy() throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);
		entityManager.getTransaction().begin();
		StudyModel clone = studyService.cloneStudy(study, admin);
		entityManager.getTransaction().commit();

		StudyModel cloneInDb = studyDao.findByUuid(clone.getUuid());

		// Equal
		assertThat(cloneInDb.getAllowedWorkerList()).containsOnly(
				JatosWorker.WORKER_TYPE, ClosedStandaloneWorker.WORKER_TYPE,
				TesterWorker.WORKER_TYPE);
		assertThat(cloneInDb.getComponentList().size()).isEqualTo(
				study.getComponentList().size());
		assertThat(cloneInDb.getFirstComponent().getTitle()).isEqualTo(
				study.getFirstComponent().getTitle());
		assertThat(cloneInDb.getLastComponent().getTitle()).isEqualTo(
				study.getLastComponent().getTitle());
		assertThat(cloneInDb.getDate()).isEqualTo(study.getDate());
		assertThat(cloneInDb.getDescription())
				.isEqualTo(study.getDescription());
		assertThat(cloneInDb.getJsonData()).isEqualTo(study.getJsonData());
		assertThat(cloneInDb.getMemberList()).containsOnly(admin);
		assertThat(cloneInDb.getTitle()).isEqualTo(study.getTitle());

		// Not equal
		assertThat(cloneInDb.isLocked()).isFalse();
		assertThat(cloneInDb.getId()).isNotEqualTo(study.getId());
		assertThat(cloneInDb.getId()).isPositive();
		assertThat(cloneInDb.getDirName()).isEqualTo(
				study.getDirName() + "_clone");
		assertThat(cloneInDb.getUuid()).isNotEqualTo(study.getUuid());
		assertThat(cloneInDb.getUuid()).isNotEmpty();

		assertThat(IOUtils.checkStudyAssetsDirExists(cloneInDb.getDirName()))
				.isTrue();

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	@Test
	public void checkExchangeMembers() throws NoSuchAlgorithmException,
			IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		UserModel userBla = createAndPersistUser("bla@bla.com", "Bla", "bla");
		createAndPersistUser("blu@blu.com", "Blu", "blu");

		entityManager.getTransaction().begin();
		try {
			String[] userList = { "admin", "bla@bla.com" };
			studyService.exchangeMembers(study, userList);
		} catch (BadRequestException e) {
			Fail.fail();
		}
		entityManager.getTransaction().commit();

		StudyModel studyInDb = studyDao.findByUuid(study.getUuid());
		assertThat(studyInDb.getMemberList()).containsOnly(userBla, admin);

		// Empty user list
		try {
			String[] userList = {};
			studyService.exchangeMembers(study, userList);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.STUDY_AT_LEAST_ONE_MEMBER);
		}

		// Not existent user
		try {
			String[] userList = { "not_exist", "admin" };
			studyService.exchangeMembers(study, userList);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.userNotExist("not_exist"));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkUpdateStudy() throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		StudyModel updatedStudy = studyService.cloneStudy(study, admin);
		updatedStudy.removeAllowedWorker(ClosedStandaloneWorker.WORKER_TYPE);
		updatedStudy.removeAllowedWorker(TesterWorker.WORKER_TYPE);
		updatedStudy.getComponentList().remove(0);
		updatedStudy.getLastComponent().setTitle("Changed title");
		updatedStudy.setDescription("Changed description");
		updatedStudy.setJsonData("{}");
		updatedStudy.setTitle("Changed Title");
		updatedStudy.setUuid("changed uuid");
		updatedStudy.getMemberList().remove(admin);
		long studyId = study.getId();

		entityManager.getTransaction().begin();
		studyService.updateStudy(study, updatedStudy);
		entityManager.getTransaction().commit();

		// Changed
		assertThat(study.getTitle()).isEqualTo(updatedStudy.getTitle());
		assertThat(study.getDescription()).isEqualTo(
				updatedStudy.getDescription());
		assertThat(study.getJsonData()).isEqualTo(updatedStudy.getJsonData());
		assertThat(study.getAllowedWorkerList()).containsOnly(
				JatosWorker.WORKER_TYPE);

		// Unchanged
		assertThat(study.getComponentList().size() == 8).isTrue();
		assertThat(study.getComponent(1).getTitle()).isEqualTo("Hello World");
		assertThat(study.getLastComponent().getTitle())
				.isEqualTo("Quit button");
		assertThat(study.getId()).isEqualTo(studyId);
		assertThat(study.getMemberList().contains(admin)).isTrue();
		assertThat(study.getUuid()).isEqualTo(
				"5c85bd82-0258-45c6-934a-97ecc1ad6617");

		// Clean-up
		removeStudy(study);
		removeStudy(updatedStudy);
	}

	@Test
	public void testCheckStudyLocked() throws NoSuchAlgorithmException,
			IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		try {
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException e) {
			Fail.fail();
		}

		study.setLocked(true);
		try {
			studyService.checkStudyLocked(study);
			Fail.fail();
		} catch (ForbiddenException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyLocked(study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void testCheckStandardForStudy() throws NoSuchAlgorithmException,
			IOException {
		try {
			studyService.checkStandardForStudy(null, 1l, admin);
			Fail.fail();
		} catch (ForbiddenException e) {
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyNotExist(1l));
		}

		StudyModel study = importExampleStudy();
		addStudy(study);
		try {
			studyService.checkStandardForStudy(study, study.getId(), admin);
		} catch (ForbiddenException e) {
			Fail.fail();
		} catch (BadRequestException e) {
			Fail.fail();
		}

		study.getMemberList().remove(admin);
		try {
			studyService.checkStandardForStudy(study, study.getId(), admin);
			Fail.fail();
		} catch (ForbiddenException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyNotMember(admin.getName(),
							admin.getEmail(), study.getId(), study.getTitle()));
		} catch (BadRequestException e) {
			Fail.fail();
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkChangeComponentPosition() throws NoSuchAlgorithmException,
			IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		// First component + down -> second
		ComponentModel component = study.getFirstComponent();
		try {
			entityManager.getTransaction().begin();
			studyService.changeComponentPosition(
					StudyService.COMPONENT_POSITION_DOWN, study, component);
			entityManager.getTransaction().commit();
		} catch (BadRequestException e) {
			Fail.fail();
		}
		assertThat(study.getComponent(2)).isEqualTo(component);

		// Second component + up -> first
		try {
			entityManager.getTransaction().begin();
			studyService.changeComponentPosition(
					StudyService.COMPONENT_POSITION_UP, study, component);
			entityManager.getTransaction().commit();
		} catch (BadRequestException e) {
			Fail.fail();
		}
		assertThat(study.getComponent(1)).isEqualTo(component);

		// First component + up -> still first
		try {
			entityManager.getTransaction().begin();
			studyService.changeComponentPosition(
					StudyService.COMPONENT_POSITION_UP, study, component);
			entityManager.getTransaction().commit();
		} catch (BadRequestException e) {
			Fail.fail();
		}
		assertThat(study.getComponent(1)).isEqualTo(component);

		// Last component + down -> still last
		component = study.getLastComponent();
		try {
			entityManager.getTransaction().begin();
			studyService.changeComponentPosition(
					StudyService.COMPONENT_POSITION_DOWN, study, component);
			entityManager.getTransaction().commit();
		} catch (BadRequestException e) {
			Fail.fail();
		}
		assertThat(study.getLastComponent()).isEqualTo(component);

		// Last component + down -> still last
		try {
			studyService.changeComponentPosition("bla", study, component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.studyReorderUnknownDirection("bla",
							study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkBindStudyFromRequest() {
		Map<String, String[]> formMap = new HashMap<String, String[]>();
		String[] titleArray = { "This is a title" };
		formMap.put(StudyModel.TITLE, titleArray);
		String[] descArray = { "This is a description" };
		formMap.put(StudyModel.DESCRIPTION, descArray);
		String[] dirNameArray = { "dir_name" };
		formMap.put(StudyModel.DIRNAME, dirNameArray);
		String[] jsonArray = { "{}" };
		formMap.put(StudyModel.JSON_DATA, jsonArray);
		String[] allowedWorkerArray = { JatosWorker.WORKER_TYPE };
		formMap.put(StudyModel.ALLOWED_WORKER_LIST, allowedWorkerArray);

		StudyModel study = studyService.bindStudyFromRequest(formMap);
		assertThat(study.getTitle()).isEqualTo("This is a title");
		assertThat(study.getDescription()).isEqualTo("This is a description");
		assertThat(study.getDirName()).isEqualTo("dir_name");
		assertThat(study.getJsonData()).isEqualTo("{ }");
		assertThat(study.getAllowedWorkerList()).containsOnly(
				JatosWorker.WORKER_TYPE);
	}

	@Test
	public void checkRenameStudyAssetsDir() throws NoSuchAlgorithmException,
			IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		String oldDirName = study.getDirName();

		entityManager.getTransaction().begin();
		studyService.renameStudyAssetsDir(study, "changed_dirname");
		entityManager.getTransaction().commit();

		assertThat(study.getDirName()).isEqualTo("changed_dirname");
		assertThat(IOUtils.checkStudyAssetsDirExists("changed_dirname"))
				.isTrue();
		assertThat(IOUtils.checkStudyAssetsDirExists(oldDirName)).isFalse();
		
		// Clean-up
		removeStudy(study);
	}

}