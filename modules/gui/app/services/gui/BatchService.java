package services.gui;

import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.BatchDao;
import daos.common.GroupResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import models.common.Batch;
import models.common.Study;
import models.common.workers.JatosWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import models.gui.BatchProperties;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class BatchService {

	private final ResultRemover resultRemover;
	private final BatchDao batchDao;
	private final StudyDao studyDao;
	private final WorkerDao workerDao;
	private final StudyResultDao studyResultDao;
	private final GroupResultDao groupResultDao;

	@Inject
	BatchService(ResultRemover resultRemover, BatchDao batchDao,
			StudyDao studyDao, WorkerDao workerDao,
			StudyResultDao studyResultDao, GroupResultDao groupResultDao) {
		this.resultRemover = resultRemover;
		this.batchDao = batchDao;
		this.studyDao = studyDao;
		this.workerDao = workerDao;
		this.studyResultDao = studyResultDao;
		this.groupResultDao = groupResultDao;
	}

	/**
	 * Clones a Batch but does not persists
	 */
	public Batch clone(Batch batch) {
		Batch clone = new Batch();
		// Generate new UUID for clone
		clone.setUuid(UUID.randomUUID().toString());
		clone.setTitle(batch.getTitle());
		clone.setActive(batch.isActive());
		clone.setMaxActiveMembers(batch.getMaxActiveMembers());
		clone.setMaxTotalMembers(batch.getMaxTotalMembers());
		clone.setMaxTotalWorkers(batch.getMaxTotalWorkers());
		batch.getWorkerList().forEach(clone::addWorker);
		batch.getAllowedWorkerTypes().forEach(clone::addAllowedWorkerType);
		return clone;
	}

	/**
	 * Initialises Batch. Does NOT persist.
	 */
	public Batch createBatch(Batch batch, Study study) {
		initBatch(batch, study);
		batch.setStudy(study);
		return batch;
	}

	/**
	 * Create and initialises default Batch. Each Study has a default batch.
	 * Does NOT persist.
	 */
	public Batch createDefaultBatch(Study study) {
		Batch batch = new Batch();
		batch.setTitle(BatchProperties.DEFAULT_TITLE);
		initBatch(batch, study);
		batch.setStudy(study);
		return batch;
	}

	/**
	 * Creates batch, initialises it and persists it. Updates study with new
	 * batch.
	 */
	public void createAndPersistBatch(Batch batch, Study study) {
		initBatch(batch, study);
		batch.setStudy(study);
		if (!study.hasBatch(batch)) {
			study.addBatch(batch);
		}
		batchDao.create(batch);
		studyDao.update(study);
	}

	/**
	 * Add default allowed worker types and all study's Jatos worker. Generates
	 * UUID.
	 */
	private void initBatch(Batch batch, Study study) {
		if (batch.getUuid() == null) {
			batch.setUuid(UUID.randomUUID().toString());
		}
		batch.addAllowedWorkerType(JatosWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		study.getUserList().forEach(user -> batch.addWorker(user.getWorker()));
	}

	public void updateBatch(Batch batch, BatchProperties updatedBatchProps) {
		batch.setTitle(updatedBatchProps.getTitle());
		batch.setActive(updatedBatchProps.isActive());
		batch.setMaxActiveMembers(updatedBatchProps.getMaxActiveMembers());
		batch.setMaxTotalMembers(updatedBatchProps.getMaxTotalMembers());
		batch.setMaxTotalWorkers(updatedBatchProps.getMaxTotalWorkers());
		batch.getAllowedWorkerTypes().clear();
		updatedBatchProps.getAllowedWorkerTypes()
				.forEach(batch::addAllowedWorkerType);
		batchDao.update(batch);
	}

	public BatchProperties bindToProperties(Batch batch) {
		BatchProperties props = new BatchProperties();
		props.setId(batch.getId());
		props.setTitle(batch.getTitle());
		props.setActive(batch.isActive());
		props.setMaxActiveMembers(batch.getMaxActiveMembers());
		props.setMaxActiveMemberLimited(batch.getMaxActiveMembers() != null);
		props.setMaxTotalMembers(batch.getMaxTotalMembers());
		props.setMaxTotalMemberLimited(batch.getMaxTotalMembers() != null);
		props.setMaxTotalWorkerLimited(batch.getMaxTotalWorkers() != null);
		props.setMaxTotalWorkers(batch.getMaxTotalWorkers());
		batch.getAllowedWorkerTypes().forEach(props::addAllowedWorkerType);
		return props;
	}

	public Batch bindToBatch(BatchProperties props) {
		Batch batch = new Batch();
		batch.setTitle(props.getTitle());
		batch.setActive(props.isActive());
		if (props.isMaxActiveMemberLimited()) {
			batch.setMaxActiveMembers(props.getMaxActiveMembers());
		} else {
			batch.setMaxActiveMembers(null);
		}
		if (props.isMaxTotalMemberLimited()) {
			batch.setMaxTotalMembers(props.getMaxTotalMembers());
		} else {
			batch.setMaxTotalMembers(null);
		}
		if (props.isMaxTotalWorkerLimited()) {
			batch.setMaxTotalWorkers(props.getMaxTotalWorkers());
		} else {
			batch.setMaxTotalWorkers(null);
		}
		props.getAllowedWorkerTypes().forEach(batch::addAllowedWorkerType);
		return batch;
	}

	/**
	 * Removes batch, all it's StudyResults, ComponentResults, GroupResults and
	 * Workers (if they don't belong to an other batch) and persists the changes
	 * to the database.
	 */
	public void remove(Batch batch) {
		// Remove this Batch from its study
		Study study = batch.getStudy();
		study.removeBatch(batch);
		studyDao.update(study);

		// Delete all StudyResults and all ComponentResults
		studyResultDao.findAllByBatch(batch)
				.forEach(resultRemover::removeStudyResult);

		// Delete all GroupResults
		groupResultDao.findAllByBatch(batch).forEach(groupResultDao::remove);

		// Remove or update Workers of this batch
		for (Worker worker : batch.getWorkerList()) {
			removeOrUpdateJatosWorker(batch, worker);
			removeOrUpdateNonJatosWorkers(batch, worker);
		}

		batchDao.remove(batch);
	}

	private void removeOrUpdateJatosWorker(Batch batch, Worker worker) {
		// We can't check type with 'instanceof JatosWorker' because sometimes
		// Hibernate doesn't map the proper type
		if (!worker.getWorkerType().equals(JatosWorker.WORKER_TYPE)) {
			return;
		}

		// If worker is part of other batches just remove this batch
		if (worker.getBatchList().size() != 1) {
			worker.removeBatch(batch);
			workerDao.update(worker);
			return;
		}

		// This is a Hibernate issue: If this worker was a JatosWorker
		// before but its user was already deleted it's not instance of
		// JatosWorker anymore. But anyway, since the worker is only in this
		// batch, now it can be removed.
		if (!(worker instanceof JatosWorker)) {
			workerDao.remove(worker);
			return;
		}

		// Worker is only in this batch
		JatosWorker jatosWorker = (JatosWorker) worker;
		if (jatosWorker.getUser() == null) {
			// Last one in batch list and User gone -> remove worker
			workerDao.remove(worker);
			return;
		} else {
			// If the JatosWorker's User still exist don't remove
			worker.removeBatch(batch);
			workerDao.update(worker);
			return;
		}
	}

	private void removeOrUpdateNonJatosWorkers(Batch batch, Worker worker) {
		// We can't check type with 'instanceof JatosWorker' because sometimes
		// Hibernate doesn't map the proper type
		if (worker.getWorkerType().equals(JatosWorker.WORKER_TYPE)) {
			return;
		}

		if (worker.getBatchList().size() == 1) {
			// If this worker does not belong to any other batches remove it
			// from the database
			workerDao.remove(worker);
		} else {
			// If this worker belongs to other batches it can't be removed
			// from the database but this batch has to be removed from the
			// worker's batch list
			worker.removeBatch(batch);
			workerDao.update(worker);
		}
	}

}
