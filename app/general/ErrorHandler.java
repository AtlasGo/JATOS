package general;

import javax.inject.Inject;
import javax.inject.Provider;

import play.Configuration;
import play.Environment;
import play.Logger;
import play.Logger.ALogger;
import play.api.OptionalSourceMapper;
import play.api.routing.Router;
import play.http.DefaultHttpErrorHandler;
import play.libs.F.Promise;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import play.mvc.Results;

public class ErrorHandler extends DefaultHttpErrorHandler {

	private static final ALogger LOGGER = Logger.of(ErrorHandler.class);

	@Inject
	public ErrorHandler(Configuration configuration, Environment environment,
			OptionalSourceMapper sourceMapper, Provider<Router> routes) {
		super(configuration, environment, sourceMapper, routes);
	}

	public Promise<Result> onClientError(RequestHeader request, int statusCode,
			String message) {
		switch (statusCode) {
		case Http.Status.BAD_REQUEST:
			LOGGER.info(".onBadRequest: " + message);
			return Promise.<Result> pure(
					Results.badRequest(views.html.error.render("Bad request")));
		case Http.Status.NOT_FOUND:
			LOGGER.info(".onNotFound: Requested page \"" + request.uri()
					+ "\" couldn't be found.");
			return Promise.<Result> pure(
					Results.notFound(views.html.error.render("Requested page \""
							+ request.uri() + "\" couldn't be found.")));
		case Http.Status.FORBIDDEN:
			LOGGER.info(".onForbidden: " + message);
			return Promise.<Result> pure(Results
					.forbidden("You're not allowed to access this resource."));
		case Http.Status.REQUEST_ENTITY_TOO_LARGE:
			LOGGER.info(".onRequestEntityTooLarge: HTTP status code "
					+ statusCode + ", " + message);
			return Promise.<Result> pure(Results.status(statusCode,
					"Request entity too large: You probably tried to upload a file that is too large"));
		default:
			LOGGER.info(".onClientError: HTTP status code " + statusCode + ", "
					+ message);
			return Promise.<Result> pure(Results.status(statusCode,
					views.html.error.render("Internal JATOS error")));
		}
	}

	@Override
	public Promise<Result> onServerError(RequestHeader request,
			Throwable exception) {
		LOGGER.info(".onServerError: Internal JATOS error", exception);
		return Promise.<Result> pure(Results.internalServerError(
				views.html.error.render("Internal JATOS error")));
	}

}
