package controllers.gui.actionannotations;

import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.gui.AuthenticationService;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletionStage;

public class RefreshSessionCookieAction extends Action<RefreshSessionCookieAction.RefreshSessionCookie> {

    @With(RefreshSessionCookieAction.class)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RefreshSessionCookie {}

    private final AuthenticationService authenticationService;

    @Inject
    RefreshSessionCookieAction(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public CompletionStage<Result> call(Http.Request request) {
        return delegate.call(request).thenApply(result -> {
            Http.Session session = authenticationService.refreshLastActivityTimeInSessionCookie(request.session());
            return result.withSession(session);
        });
    }

}
