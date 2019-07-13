package services.publix.idcookie;

import controllers.publix.workers.JatosPublix.JatosRun;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.InternalServerErrorPublixException;
import general.common.Common;
import models.common.*;
import models.common.workers.Worker;
import play.mvc.Http;
import services.publix.PublixErrorMessages;
import services.publix.idcookie.exception.IdCookieAlreadyExistsException;
import services.publix.idcookie.exception.IdCookieCollectionFullException;
import utils.common.HttpUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;

/**
 * Service class for JATOS ID cookie handling. It generates, extracts and discards ID cookies. An ID cookie is used by
 * the JATOS server to tell jatos.js about several IDs the current study run has (e.g. worker ID, study ID, study result
 * ID). This cookie is created when the study run is started and discarded when it's done.
 *
 * @author Kristian Lange (2016)
 */
@Singleton
public class IdCookieService {

    private final IdCookieAccessor idCookieAccessor;

    @Inject
    public IdCookieService(IdCookieAccessor idCookieAccessor) {
        this.idCookieAccessor = idCookieAccessor;
    }

    /**
     * Returns the IdCookie that corresponds to the given study result ID. If the cookie doesn't exist it throws a
     * BadRequestPublixException.
     */
    public IdCookieModel getIdCookie(Http.RequestHeader request, Long studyResultId)
            throws BadRequestPublixException {
        IdCookieModel idCookie = request.attrs().get(IdCookieAccessor.ID_COOKIES).get(studyResultId);
        if (idCookie == null) {
            throw new BadRequestPublixException(PublixErrorMessages.idCookieForThisStudyResultNotExists(studyResultId));
        }
        return idCookie;
    }

    /**
     * Returns true if the study assets of at least one ID cookie is equal to the given study assets. Otherwise returns
     * false.
     */
    public boolean hasOneIdCookieThisStudyAssets(Http.Request request, String studyAssets) {
        Collection<IdCookieModel> idCookies = request.attrs().get(IdCookieAccessor.ID_COOKIES).getAll();
        for (IdCookieModel idCookie : idCookies) {
            if (idCookie.getStudyAssets().equals(studyAssets)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates an ID cookie from the given parameters and sets it in the response object.
     */
    public Http.Request writeIdCookie(Http.Request request, Worker worker, Batch batch, StudyResult studyResult)
            throws InternalServerErrorPublixException {
        return writeIdCookie(request, worker, batch, studyResult, null, null);
    }

    /**
     * Generates an ID cookie from the given parameters and sets it in the response object.
     */
    public Http.Request writeIdCookie(Http.Request request, Worker worker, Batch batch, StudyResult studyResult,
            ComponentResult componentResult) throws InternalServerErrorPublixException {
        return writeIdCookie(request, worker, batch, studyResult, componentResult, null);
    }

    /**
     * Generates an ID cookie from the given parameters and sets it in the response object.
     */
    public Http.Request writeIdCookie(Http.Request request, Worker worker, Batch batch, StudyResult studyResult,
            JatosRun jatosRun) throws InternalServerErrorPublixException {
        return writeIdCookie(request, worker, batch, studyResult, null, jatosRun);
    }

    /**
     * Generates an ID cookie from the given parameters and puts it in the IdCookieCollection. Checks if there is an
     * existing ID cookie with the same study result ID and if so overwrites it. If there isn't it writes a new one. It
     * expects a free spot in the cookie collection and if not throws an InternalServerErrorPublixException (should
     * never happen). The deletion of the oldest cookie must have happened beforehand.
     */
    public Http.Request writeIdCookie(Http.Request request, Worker worker, Batch batch, StudyResult studyResult,
            ComponentResult componentResult, JatosRun jatosRun) throws InternalServerErrorPublixException {
        IdCookieCollection idCookieCollection = request.attrs().get(IdCookieAccessor.ID_COOKIES);
        try {
            String newIdCookieName;

            // Check if there is an existing IdCookie for this StudyResult
            IdCookieModel existingIdCookie = idCookieCollection.get(studyResult.getId());
            if (existingIdCookie != null) {
                newIdCookieName = existingIdCookie.getName();
            } else {
                newIdCookieName = getNewIdCookieName(idCookieCollection);
            }

            IdCookieModel newIdCookie = buildIdCookie(newIdCookieName, batch, studyResult, componentResult, worker,
                    jatosRun);

            return idCookieAccessor.write(request, newIdCookie);
        } catch (IdCookieCollectionFullException e) {
            // Should never happen since we check in front
            throw new InternalServerErrorPublixException(e.getMessage());
        }
    }

    /**
     * Generates the name for a new IdCookie: If the max number of IdCookies is reached it reuses the name of the oldest
     * IdCookie. If not it creates a new name.
     */
    private String getNewIdCookieName(IdCookieCollection idCookieCollection) throws IdCookieCollectionFullException {
        if (idCookieCollection.isFull()) {
            throw new IdCookieCollectionFullException(PublixErrorMessages.IDCOOKIE_COLLECTION_FULL);
        }
        int newIndex = idCookieCollection.getNextAvailableIdCookieIndex();
        return IdCookieModel.ID_COOKIE_NAME + "_" + newIndex;
    }

    /**
     * Builds an IdCookie from the given parameters. It accepts null values for ComponentResult and GroupResult (stored
     * in StudyResult). All others must not be null.
     */
    private IdCookieModel buildIdCookie(String name, Batch batch, StudyResult studyResult,
            ComponentResult componentResult, Worker worker, JatosRun jatosRun) {
        IdCookieModel idCookie = new IdCookieModel();
        Study study = studyResult.getStudy();

        // ComponentResult might not yet be created
        if (componentResult != null) {
            Component component = componentResult.getComponent();
            idCookie.setComponentId(component.getId());
            idCookie.setComponentResultId(componentResult.getId());
            idCookie.setComponentPosition(study.getComponentPosition(component));
        }

        // Might not have a GroupResult because it's not a group study
        GroupResult groupResult = studyResult.getActiveGroupResult();
        if (groupResult != null) {
            idCookie.setGroupResultId(groupResult.getId());
        }

        idCookie.setBatchId(batch.getId());
        idCookie.setCreationTime(System.currentTimeMillis());
        idCookie.setStudyAssets(HttpUtils.urlEncode(study.getDirName()));
        idCookie.setUrlBasePath(Common.getPlayHttpContext());
        idCookie.setName(name);
        idCookie.setStudyId(study.getId());
        idCookie.setStudyResultId(studyResult.getId());
        idCookie.setWorkerId(worker.getId());
        idCookie.setWorkerType(worker.getWorkerType());
        idCookie.setJatosRun(jatosRun);
        return idCookie;
    }

    /**
     * Discards the ID cookie if the given study result ID is equal to the one in the cookie.
     */
    public Http.Request discardIdCookie(Http.Request request, Long studyResultId)
            throws InternalServerErrorPublixException {
        try {
            return idCookieAccessor.discard(request, studyResultId);
        } catch (IdCookieAlreadyExistsException e) {
            throw new InternalServerErrorPublixException(e.getMessage());
        }
    }

    /**
     * Returns true if the max number of IdCookies have been reached and false otherwise.
     */
    public boolean maxIdCookiesReached(Http.Request request) {
        return request.attrs().get(IdCookieAccessor.ID_COOKIES).isFull();
    }

    /**
     * Checks the creation time of each IdCookie in the given IdCookieCollection and returns the oldest one. Returns
     * null if the IdCookieCollection is empty.
     */
    public IdCookieModel getOldestIdCookie(Http.Request request) {
        IdCookieCollection idCookieCollection = request.attrs().get(IdCookieAccessor.ID_COOKIES);
        Long oldest = Long.MAX_VALUE;
        IdCookieModel oldestIdCookie = null;
        for (IdCookieModel idCookie : idCookieCollection.getAll()) {
            Long creationTime = idCookie.getCreationTime();
            if (creationTime != null && creationTime < oldest) {
                oldest = creationTime;
                oldestIdCookie = idCookie;
            }
        }
        return oldestIdCookie;
    }

    /**
     * Checks the creation time of each IdCookie in the given IdCookieCollection and returns the study result ID of the
     * oldest one. Returns null if the IdCookieCollection is empty.
     */
    public Long getStudyResultIdFromOldestIdCookie(Http.Request request) throws InternalServerErrorPublixException {
        IdCookieModel oldest = getOldestIdCookie(request);
        return (oldest != null) ? oldest.getStudyResultId() : null;
    }

}
