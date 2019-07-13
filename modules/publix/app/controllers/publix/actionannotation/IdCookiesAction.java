package controllers.publix.actionannotation;

import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.publix.idcookie.IdCookieAccessor;
import services.publix.idcookie.IdCookieCollection;
import services.publix.idcookie.exception.IdCookieAlreadyExistsException;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static controllers.publix.actionannotation.IdCookiesAction.IdCookies;

public class IdCookiesAction extends Action<IdCookies> {

    @With(IdCookiesAction.class)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IdCookies {}

    private AtomicReference<Http.Request> request = new AtomicReference<>();

    private final IdCookieAccessor idCookieAccessor;

    @Inject
    IdCookiesAction(IdCookieAccessor idCookieAccessor) {
        this.idCookieAccessor = idCookieAccessor;
    }

    public CompletionStage<Result> call(Http.Request request) {
        try {
            IdCookieCollection idCookieCollection = idCookieAccessor.extractFromCookies(request.cookies());
            request = request.addAttr(IdCookieAccessor.ID_COOKIES, idCookieCollection);
            this.request.set(request);
        } catch (IdCookieAlreadyExistsException e) {
            // Should never happen or something is seriously wrong
            return CompletableFuture.completedFuture(internalServerError(e.getMessage()));
        }

        return delegate.call(request).thenApply(result -> {
            Http.Cookie[] idCookies = idCookieAccessor.getHttpCookies(this.request.get());
            return result.withCookies(idCookies);
        });
    }

}
