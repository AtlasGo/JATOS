package services.gui;

import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.gui.common.BadRequestException;
import exceptions.gui.common.ForbiddenException;
import exceptions.gui.common.NotFoundException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.Worker;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Service class that mostly generates Strings from ComponentResult's or StudyResult's
 * result data. It's used by controllers or other services.
 *
 * @author Kristian Lange
 */
@Singleton
public class ResultDataExportService {

    private final Checker checker;
    private final ResultService resultService;
    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final StudyLogger studyLogger;

    @Inject
    ResultDataExportService(Checker checker, ResultService resultService,
            ComponentResultDao componentResultDao, StudyResultDao studyResultDao,
            StudyLogger studyLogger) {
        this.checker = checker;
        this.resultService = resultService;
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.studyLogger = studyLogger;
    }

    /**
     * Retrieves all StudyResults that belong to the given worker and that the
     * given user is allowed to see (means StudyResults from studies he is a
     * user of), checks them and returns all their result data in one string.
     */
    public String forWorker(User user, Worker worker)
            throws ForbiddenException, BadRequestException {
        List<StudyResult> allowedStudyResultList =
                resultService.getAllowedStudyResultList(user, worker);
        checker.checkStudyResults(allowedStudyResultList, user, false);
        String resultData = resultService.studyResultDataToString(allowedStudyResultList);
        studyLogger.logStudyResultDataExporting(allowedStudyResultList, resultData);
        return resultData;
    }

    /**
     * Retrieves all StudyResults of the given study, checks them and returns
     * all their result data in one string.
     */
    public String forStudy(User user, Study study) throws ForbiddenException, BadRequestException {
        List<StudyResult> studyResultList = studyResultDao.findAllByStudy(study);
        checker.checkStudyResults(studyResultList, user, false);
        String resultData = resultService.studyResultDataToString(studyResultList);
        studyLogger.logStudyResultDataExporting(studyResultList, resultData);
        return resultData;
    }

    /**
     * Retrieves all ComponentResults of the given component, checks them and
     * returns them all their result data in one string.
     */
    public String forComponent(User user, Component component)
            throws ForbiddenException, BadRequestException {
        List<ComponentResult> componentResultList =
                componentResultDao.findAllByComponent(component);
        checker.checkComponentResults(componentResultList, user, false);
        String resultData = resultService.componentResultDataToString(componentResultList);
        studyLogger.logComponentResultDataExporting(componentResultList, resultData);
        return resultData;
    }

    /**
     * Retrieves the StudyResults that correspond to the IDs, checks them and
     * returns all their result data in one string.
     */
    public String fromStudyResultIdList(List<Long> studyResultIdList, User user)
            throws BadRequestException, NotFoundException, ForbiddenException {
        List<StudyResult> studyResultList = resultService.getStudyResults(studyResultIdList);
        checker.checkStudyResults(studyResultList, user, false);
        String resultData = resultService.studyResultDataToString(studyResultList);
        studyLogger.logStudyResultDataExporting(studyResultList, resultData);
        return resultData;
    }

    /**
     * Retrieves the ComponentResults that correspond to the IDs, checks them
     * and returns all their result data in one string.
     */
    public String fromComponentResultIdList(List<Long> componentResultIdList, User user)
            throws BadRequestException, NotFoundException, ForbiddenException {
        List<ComponentResult> componentResultList =
                resultService.getComponentResults(componentResultIdList);
        checker.checkComponentResults(componentResultList, user, false);
        String resultData = resultService.componentResultDataToString(componentResultList);
        studyLogger.logComponentResultDataExporting(componentResultList, resultData);
        return resultData;
    }

}
