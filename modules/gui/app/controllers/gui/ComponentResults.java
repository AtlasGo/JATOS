package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import controllers.gui.actionannotations.RefreshSessionCookieAction;
import controllers.gui.actionannotations.RefreshSessionCookieAction.RefreshSessionCookie;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import exceptions.gui.common.BadRequestException;
import exceptions.gui.common.ForbiddenException;
import exceptions.gui.common.NotFoundException;
import general.gui.Messages;
import general.gui.RequestScopeMessaging;
import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.User;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.AuthenticationService;
import services.gui.BreadcrumbsService;
import services.gui.Checker;
import services.gui.ResultRemover;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Controller that deals with requests regarding ComponentResult.
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class ComponentResults extends Controller {

    private final Checker checker;
    private final AuthenticationService authenticationService;
    private final BreadcrumbsService breadcrumbsService;
    private final ResultRemover resultRemover;
    private final JsonUtils jsonUtils;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final ComponentResultDao componentResultDao;

    @Inject
    ComponentResults(Checker checker, AuthenticationService authenticationService,
            BreadcrumbsService breadcrumbsService, ResultRemover resultRemover, JsonUtils jsonUtils, StudyDao studyDao,
            ComponentDao componentDao, ComponentResultDao componentResultDao) {
        this.checker = checker;
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.resultRemover = resultRemover;
        this.jsonUtils = jsonUtils;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.componentResultDao = componentResultDao;
    }

    /**
     * Shows a view with all component results of a component of a study.
     */
    @Transactional
    @Authenticated
    @RefreshSessionCookie
    public Result componentResults(Http.Request request, Long studyId, Long componentId, String errorMsg,
            int httpStatus) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            return redirect(routes.Studies.study(studyId)).flashing(Messages.ERROR, e.getMessage());
        }

        request = RequestScopeMessaging.error(request, errorMsg);
        String breadcrumbs = breadcrumbsService.generateForComponent(study, component, BreadcrumbsService.RESULTS);
        return status(httpStatus, views.html.gui.result.componentResults
                .render(request, loggedInUser, breadcrumbs, HttpUtils.isLocalhost(request), study, component));
    }

    @Transactional
    @Authenticated
    @RefreshSessionCookie
    public Result componentResults(Http.Request request, Long studyId, Long componentId, String errorMsg) {
        return componentResults(request, studyId, componentId, errorMsg, Http.Status.OK);
    }

    @Transactional
    @Authenticated
    @RefreshSessionCookie
    public Result componentResults(Http.Request request, Long studyId, Long componentId) {
        return componentResults(request, studyId, componentId, null, Http.Status.OK);
    }

    /**
     * Ajax DELETE request
     * <p>
     * Removes all ComponentResults specified in the parameter. The parameter is a comma separated list of of
     * ComponentResult IDs as a String.
     */
    @Transactional
    @Authenticated
    public Result remove(Http.Request request, String componentResultIds) {
        User loggedInUser = authenticationService.getLoggedInUser(request);
        try {
            // Permission check is done in service for each result individually
            resultRemover.removeComponentResults(componentResultIds, loggedInUser);
        } catch (ForbiddenException | BadRequestException | NotFoundException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax request
     * <p>
     * Removes all ComponentResults of the given component and study.
     */
    @Transactional
    @Authenticated
    public Result removeAllOfComponent(Http.Request request, Long studyId, Long componentId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);

            resultRemover.removeAllComponentResults(component, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax request
     * <p>
     * Returns all ComponentResults as JSON for a given component.
     */
    @Transactional
    @Authenticated
    public Result tableDataByComponent(Http.Request request, Long studyId, Long componentId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser(request);
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            return status(HttpUtils.getHttpStatus(e), e.getMessage());
        }

        List<ComponentResult> componentResultList = componentResultDao.findAllByComponent(component);
        JsonNode dataAsJson = jsonUtils.allComponentResultsForUI(componentResultList);
        return ok(dataAsJson);
    }

}
