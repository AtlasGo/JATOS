package services.gui;

import controllers.gui.Authentication;
import controllers.gui.actionannotations.AuthenticationAction;
import daos.common.UserDao;
import general.common.Common;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Http;
import utils.common.HashUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service class around authentication, Play session cookies and user session cache handling. It works together with the
 * {@link Authentication} controller and the @Authenticated annotation defined in {@link AuthenticationAction}.
 * <p>
 * If a user is authenticated (same password as stored in the database) a user session ID is generated and stored in
 * Play's session cookie and in the the user session cache. With each subsequent request this session is checked in the
 * AuthenticationAction. For authentication the user's email is turned into lower case.
 *
 * @author Kristian Lange (2017, 2019)
 */
@Singleton
public class AuthenticationService {

    private static final ALogger LOGGER = Logger.of(AuthenticationService.class);

    /**
     * Parameter name in Play's session cookie: It contains the email of the logged in user
     */
    public static final String SESSION_ID = "sessionID";

    /**
     * Parameter name in Play's session cookie: It contains the email of the logged in user
     */
    public static final String SESSION_USER_EMAIL = "userEmail";

    /**
     * Parameter name in Play's session cookie: It contains the timestamp of the login time
     */
    public static final String SESSION_LOGIN_TIME = "loginTime";

    /**
     * Parameter name in Play's session cookie: It contains a timestamp of the time of the last HTTP request done by the
     * browser with this cookie
     */
    public static final String SESSION_LAST_ACTIVITY_TIME = "lastActivityTime";

    private static final SecureRandom random = new SecureRandom();

    private final UserDao userDao;
    private final UserSessionCacheAccessor userSessionCacheAccessor;

    @Inject
    AuthenticationService(UserDao userDao, UserSessionCacheAccessor userSessionCacheAccessor) {
        this.userDao = userDao;
        this.userSessionCacheAccessor = userSessionCacheAccessor;
    }

    /**
     * Authenticates the user specified by the email with the given password.
     */
    public boolean authenticate(String email, String password) {
        email = email.toLowerCase();
        String passwordHash = HashUtils.getHashMD5(password);
        return userDao.authenticate(email, passwordHash);
    }

    /**
     * Checks the user session cache whether this user tries to login repeatedly
     */
    public boolean isRepeatedLoginAttempt(String email) {
        email = email.toLowerCase();
        userSessionCacheAccessor.addLoginAttempt(email);
        return userSessionCacheAccessor.isRepeatedLoginAttempt(email);
    }

    /**
     * Retrieves the logged-in user from Play's session. If a user is logged-in his email is stored in the Play's
     * session cookie. With the email a user can be retrieved from the database. Returns Optional and if the session
     * doesn't contains an email or if the user doesn't exists in the database, it is empty.
     * <p>
     * In most cases getLoggedInUser() is faster since it doesn't has to query the database.
     */
    public Optional<User> getLoggedInUserBySessionCookie(Http.Session sessionCookie) {
        Optional<String> email = sessionCookie.getOptional(AuthenticationService.SESSION_USER_EMAIL);
        return email.map(e -> userDao.findByEmail(e.toLowerCase()));
    }

    /**
     * Gets the logged-in user from the request attrs. It was put into the request attrs by the AuthenticationAction.
     * Therefore this method works only if you use the @Authenticated annotation at your action.
     */
    public User getLoggedInUser(Http.Request request) {
        return request.attrs().get(AuthenticationAction.LOGGED_IN_USER);
    }

    /**
     * Prepares Play's session cookie and the user session cache for the user with the given email to be logged-in. Does
     * not authenticate the user (use authenticate() for this).
     */
    public Http.Session writeSessionCookieAndUserSessionCache(Http.Session sessionCookie, String email,
            String remoteAddress) {
        email = email.toLowerCase();
        String sessionId = generateSessionId();
        userSessionCacheAccessor.setUserSessionId(email, remoteAddress, sessionId);
        return sessionCookie.adding(SESSION_ID, sessionId).adding(SESSION_USER_EMAIL, email).adding(SESSION_LOGIN_TIME,
                String.valueOf(Instant.now().toEpochMilli())).adding(SESSION_LAST_ACTIVITY_TIME,
                String.valueOf(Instant.now().toEpochMilli()));
    }

    /**
     * Used StackOverflow for this session ID generator: "This works by choosing 130 bits from a cryptographically
     * secure random bit generator, and encoding them in base-32" (http://stackoverflow.com/questions/41107)
     */
    private String generateSessionId() {
        return new BigInteger(130, random).toString(32);
    }

    /**
     * Refreshes the last activity timestamp in Play's session cookie. This is usually done with each HTTP call of the
     * user.
     */
    public Http.Session refreshLastActivityTimeInSessionCookie(Http.Session session) {
        return session.adding(SESSION_LAST_ACTIVITY_TIME, String.valueOf(Instant.now().toEpochMilli()));
    }

    /**
     * Deletes the session cookie and removes the cache entry. This is usual done during a user logout.
     */
    public void clearUserSessionCache(String email, String remoteAddress) {
        email = email.toLowerCase();
        userSessionCacheAccessor.removeUserSessionId(email, remoteAddress);
    }

    /**
     * Checks the session ID stored in Play's session cookie whether it is the same as stored in the cache during the
     * last login.
     */
    public boolean isValidSessionId(Http.Session sessionCookie, String email, String remoteAddress) {
        email = email.toLowerCase();
        String cookieUserSessionId = sessionCookie.get(SESSION_ID);
        String cachedUserSessionId = userSessionCacheAccessor.getUserSessionId(email, remoteAddress);
        return cookieUserSessionId != null && cookieUserSessionId.equals(cachedUserSessionId);
    }

    /**
     * Returns true if the session login time as saved in Play's session cookie is older than allowed.
     */
    public boolean isSessionTimeout(Http.Session sessionCookie) {
        try {
            Instant loginTime = Instant.ofEpochMilli(Long.parseLong(sessionCookie.get(SESSION_LOGIN_TIME)));
            Instant now = Instant.now();
            Instant allowedUntil = loginTime.plus(Common.getUserSessionTimeout(), ChronoUnit.MINUTES);
            return allowedUntil.isBefore(now);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            // In case of any exception: timeout
            return true;
        }
    }

    /**
     * Returns true if the session inactivity time as saved in Play's session cookie is older than allowed.
     */
    public boolean isInactivityTimeout(Http.Session sessionCookie) {
        try {
            Instant lastActivityTime = Instant.ofEpochMilli(
                    Long.parseLong(sessionCookie.get(SESSION_LAST_ACTIVITY_TIME)));
            Instant now = Instant.now();
            Instant allowedUntil = lastActivityTime.plus(Common.getUserSessionInactivity(), ChronoUnit.MINUTES);
            return allowedUntil.isBefore(now);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            // In case of any exception: timeout
            return true;
        }
    }

}
