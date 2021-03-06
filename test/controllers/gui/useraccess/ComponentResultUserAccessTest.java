package controllers.gui.useraccess;

import com.google.inject.Guice;
import com.google.inject.Injector;
import controllers.gui.routes;
import daos.common.UserDao;
import exceptions.publix.ForbiddenNonLinearFlowException;
import exceptions.publix.ForbiddenReloadException;
import general.TestHelper;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.api.mvc.Call;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.test.Helpers;
import scala.Option;
import services.gui.UserService;
import services.publix.ResultCreator;
import services.publix.workers.JatosPublixUtils;

import javax.inject.Inject;

import static play.test.Helpers.route;

/**
 * Testing controller actions of ComponentResults whether they have proper
 * access control: only the right user should be allowed to do the action. For
 * most actions only the denial of access is tested here - the actual function
 * of the action (that includes positive access) is tested in the specific test
 * class.
 * <p>
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 *
 * @author Kristian Lange (2015 - 2017)
 */
public class ComponentResultUserAccessTest {

    private Injector injector;

    @Inject
    private Application fakeApplication;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private UserDao userDao;

    @Inject
    private ResultCreator resultCreator;

    @Inject
    private JatosPublixUtils jatosPublixUtils;

    @Inject
    private UserAccessTestHelpers userAccessTestHelpers;

    @Before
    public void startApp() throws Exception {
        fakeApplication = Helpers.fakeApplication();

        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        injector = Guice.createInjector(builder.applicationModule());
        injector.injectMembers(this);

        Helpers.start(fakeApplication);
    }

    @After
    public void stopApp() throws Exception {
        // Clean up
        testHelper.removeAllStudies();

        Helpers.stop(fakeApplication);
        testHelper.removeStudyAssetsRootDir();
        testHelper.removeAllStudyLogs();
    }

    @Test
    public void callComponentResults() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.ComponentResults
                .componentResults(study.getId(), study.getComponent(1).getId(), Option.empty());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callComponentResultsRemove() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        StudyResult studyResult = createTwoComponentResults(study);
        ComponentResult componentResult = studyResult.getComponentResultList().get(0);

        Call call = controllers.gui.routes.ComponentResults.remove();

        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);

        // Logged-in user must be an user of the study to which the ComponentResult belongs that is to be deleted
        // - if not an HTTP 403 is expected
        User someUser = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla", "bla");
        String bodyText = "{\"resultIds\": [" + componentResult.getId() + "]}";
        userAccessTestHelpers.checkThatCallIsForbidden(call, Helpers.POST, someUser, bodyText, "isn't user of study");

        userAccessTestHelpers.checkAccessGranted(call, Helpers.POST, testHelper.getAdmin(), bodyText);

        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    private StudyResult createTwoComponentResults(Study study) {
        return jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            StudyResult studyResult = resultCreator
                    .createStudyResult(study, study.getDefaultBatch(), admin.getWorker());
            try {
                jatosPublixUtils.startComponent(study.getFirstComponent().get(), studyResult);
                jatosPublixUtils.startComponent(study.getFirstComponent().get(), studyResult);
            } catch (ForbiddenReloadException | ForbiddenNonLinearFlowException e) {
                e.printStackTrace();
            }
            return studyResult;
        });
    }

    @Test
    public void callComponentResultsTableDataByComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.ComponentResults
                .tableDataByComponent(study.getId(), study.getComponent(1).getId(), Option.empty());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

}
