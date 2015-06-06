package publix;

import static org.fest.assertions.Assertions.assertThat;
import gui.AbstractTest;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.StudyModel;
import models.StudyResult;
import models.StudyResult.StudyState;
import models.workers.Worker;

import org.fest.assertions.Fail;
import org.junit.Test;

import persistance.StudyResultDao;
import persistance.workers.WorkerDao;
import publix.exceptions.PublixException;
import publix.services.PublixErrorMessages;
import publix.services.PublixUtils;
import common.Global;

/**
 * @author Kristian Lange
 */
public abstract class PublixUtilsTest<T extends Worker> extends AbstractTest {

	protected WorkerDao workerDao;
	protected StudyResultDao studyResultDao;
	protected PublixUtils<T> publixUtils;
	protected PublixErrorMessages errorMessages;

	@Override
	public void before() throws Exception {
		workerDao = Global.INJECTOR.getInstance(WorkerDao.class);
		studyResultDao = Global.INJECTOR.getInstance(StudyResultDao.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void checkRetrieveWorker() throws NoSuchAlgorithmException,
			IOException, PublixException {
		// Worker ID is null
		try {
			publixUtils.retrieveWorker(null);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.NO_WORKERID_IN_SESSION);
		}

		// Worker ID malformed
		try {
			publixUtils.retrieveWorker("foo");
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.workerNotExist("foo"));
		}

		// Worker doesn't exist
		try {
			publixUtils.retrieveWorker("2");
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.workerNotExist("2"));
		}
	}
	
	protected void addWorker(Worker worker) {
		entityManager.getTransaction().begin();
		workerDao.create(worker);
		entityManager.getTransaction().commit();
	}
	
	protected void addStudyResult(StudyModel study, Worker worker,
			StudyState state) {
		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study, worker);
		studyResult.setStudyState(state);
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(worker);
		entityManager.getTransaction().commit();
	}
	
}
