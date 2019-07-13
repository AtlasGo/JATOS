package services.publix.idcookie;

import controllers.publix.Publix;
import controllers.publix.workers.JatosPublix.JatosRun;
import general.common.Common;
import play.Logger;
import play.Logger.ALogger;
import play.libs.typedmap.TypedKey;
import play.mvc.Http;
import services.publix.PublixErrorMessages;
import services.publix.idcookie.exception.IdCookieAlreadyExistsException;
import services.publix.idcookie.exception.IdCookieCollectionFullException;
import services.publix.idcookie.exception.IdCookieMalformedException;
import utils.common.HttpUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * This class offers a simple interface to extract, log and discard IdCookies.
 * <p>
 * Internally this class accesses JATOS' ID cookies in the HTTP Request. It stores the extracted {@link IdCookieModel}
 * in a {@link IdCookieCollection}. Additionally it puts the {@link IdCookieCollection} in the request attrs for easier
 * retrieval in subsequent calls within the same Request.
 * <p>
 * Each browser can run up to IdCookieCollection.MAX_ID_COOKIES ID studies at the same time. This means that there are
 * the same number of ID cookies stored in the browser as studies are currently running (although part of them might be
 * abandoned).
 *
 * @author Kristian Lange (2016, 2019)
 */
@Singleton
public class IdCookieAccessor {

    private static final ALogger LOGGER = Logger.of(IdCookieAccessor.class);

    public static final TypedKey<IdCookieCollection> ID_COOKIES = TypedKey.create("IdCookies");

    private static final String COOKIE_EQUALS = "=";
    private static final String COOKIE_AND = "&";

    private final IdCookieSerialiser idCookieSerialiser;

    @Inject
    public IdCookieAccessor(IdCookieSerialiser idCookieSerialiser) {
        this.idCookieSerialiser = idCookieSerialiser;
    }

    /**
     * Returns the IdCookieCollection containing all IdCookies of this Request. Additionally it stores this
     * idCookieCollection in the RequestScope.
     */
    public Http.RequestHeader extract(Http.RequestHeader request) throws IdCookieAlreadyExistsException {
        IdCookieCollection idCookieCollection = extractFromCookies(request.cookies());
        request.addAttr(ID_COOKIES, idCookieCollection);
        return request;
    }

    public Http.Cookie[] getHttpCookies(Http.RequestHeader request) {
        IdCookieCollection idCookieCollection = request.attrs().get(ID_COOKIES);
        return idCookieCollection.getAll().stream().map(this::buildHttpCookie).toArray(Http.Cookie[]::new);
    }

    private Http.Cookie buildHttpCookie(IdCookieModel idCookieModel) {
        String idCookieStr = idCookieSerialiser.asCookieValueString(idCookieModel);
        return Http.Cookie.builder(idCookieModel.getName(), idCookieStr).withMaxAge(Duration.of(10000, ChronoUnit.DAYS))
                .withSecure(false).withHttpOnly(false).withPath(Common.getPlayHttpContext()).build();
    }

    /**
     * Extracts all ID cookies from all the HTTP cookies and stores them into an {@link IdCookieCollection}. If a cookie
     * is malformed it is discarded right away (removed from the Response).
     */
    public IdCookieCollection extractFromCookies(Http.Cookies cookies) throws IdCookieAlreadyExistsException {
        IdCookieCollection idCookieCollection = new IdCookieCollection();
        for (Http.Cookie cookie : cookies) {
            // Cookie names are case insensitive
            if (cookie.name().toLowerCase().startsWith(IdCookieModel.ID_COOKIE_NAME.toLowerCase())) {
                try {
                    IdCookieModel idCookie = buildIdCookie(cookie);
                    idCookieCollection.add(idCookie);
                } catch (IdCookieMalformedException e) {
                    LOGGER.warn("Deleted malformed JATOS ID cookie.");
                }
            }
        }
        return idCookieCollection;
    }

    private IdCookieModel buildIdCookie(Http.Cookie cookie) throws IdCookieMalformedException {
        IdCookieModel idCookie = new IdCookieModel();
        Map<String, String> cookieMap = getCookiesKeyValuePairs(cookie);
        idCookie.setName(cookie.name());
        idCookie.setIndex(getCookieIndex(cookie.name()));
        idCookie.setWorkerId(getValueAsLong(cookieMap, IdCookieModel.WORKER_ID, true, cookie.name()));
        idCookie.setWorkerType(getValueAsString(cookieMap, IdCookieModel.WORKER_TYPE, true, cookie.name()));
        idCookie.setBatchId(getValueAsLong(cookieMap, IdCookieModel.BATCH_ID, true, cookie.name()));
        idCookie.setGroupResultId(getValueAsLong(cookieMap, IdCookieModel.GROUP_RESULT_ID, false, cookie.name()));
        idCookie.setStudyId(getValueAsLong(cookieMap, IdCookieModel.STUDY_ID, true, cookie.name()));
        idCookie.setStudyResultId(getValueAsLong(cookieMap, IdCookieModel.STUDY_RESULT_ID, true, cookie.name()));
        idCookie.setComponentId(getValueAsLong(cookieMap, IdCookieModel.COMPONENT_ID, false, cookie.name()));
        idCookie.setComponentResultId(
                getValueAsLong(cookieMap, IdCookieModel.COMPONENT_RESULT_ID, false, cookie.name()));
        idCookie.setComponentPosition(getValueAsInt(cookieMap, IdCookieModel.COMPONENT_POSITION, false, cookie.name()));
        idCookie.setStudyAssets(getValueAsString(cookieMap, IdCookieModel.STUDY_ASSETS, true, cookie.name()));
        idCookie.setUrlBasePath(getValueAsString(cookieMap, IdCookieModel.URL_BASE_PATH, true, cookie.name()));
        idCookie.setJatosRun(valueOfJatosRun(cookieMap, cookie));
        idCookie.setCreationTime(getValueAsLong(cookieMap, IdCookieModel.CREATION_TIME, true, cookie.name()));
        return idCookie;
    }

    /**
     * Maps the IdCookie value for a JATOS run to the enum {@link JatosRun}. If the value can't be matched to an
     * instance of JatosRun then null is returned.
     */
    private JatosRun valueOfJatosRun(Map<String, String> cookieMap, Http.Cookie cookie)
            throws IdCookieMalformedException {
        try {
            return JatosRun.valueOf(getValueAsString(cookieMap, IdCookieModel.JATOS_RUN, false, cookie.name()));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /**
     * Returns the index of the ID cookie which is in the last char of it's name. If the last char is not a number than
     * an IdCookieMalformedException is thrown.
     */
    private int getCookieIndex(String name) throws IdCookieMalformedException {
        String lastChar = name.substring(name.length() - 1);
        try {
            return Integer.valueOf(lastChar);
        } catch (NumberFormatException e) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractIndexFromIdCookieName(name));

        }
    }

    /**
     * Extract and returns a Map with the given Cookie's key-value pairs.
     */
    private Map<String, String> getCookiesKeyValuePairs(Http.Cookie cookie) throws IdCookieMalformedException {
        Map<String, String> cookieKeyValuePairs = new HashMap<>();
        for (String pairStr : cookie.value().split(COOKIE_AND)) {
            addKeyValuePair(cookieKeyValuePairs, pairStr);
        }
        return cookieKeyValuePairs;
    }

    private void addKeyValuePair(Map<String, String> cookieKeyValuePairs, String pairStr)
            throws IdCookieMalformedException {
        String[] pairArray = pairStr.split(COOKIE_EQUALS);
        String key;
        String value;
        // Every pair should have 2 elements (a key and a value)
        if (pairArray.length == 0) {
            throw new IdCookieMalformedException("Couldn't extract key from ID cookie.");
        } else if (pairArray.length == 1) {
            key = pairArray[0];
            value = "";
        } else if (pairArray.length == 2) {
            key = pairArray[0];
            value = pairArray[1];
        } else {
            throw new IdCookieMalformedException("Wrong number of '&' in ID cookie.");
        }
        cookieKeyValuePairs.put(key, value);
    }

    /**
     * Searches the given map the given key and returns the corresponding value as String. Throws a
     * MalformedIdCookieException if the key doesn't exist. If strict is true and the value is "" it throws an
     * MalformedIdCookieException.
     */
    private String getValueAsString(Map<String, String> cookieMap, String key, boolean strict, String cookieName)
            throws IdCookieMalformedException {
        String valueStr;
        try {
            valueStr = HttpUtils.urlDecode(cookieMap.get(key));
        } catch (Exception e) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, key));
        }
        if (strict && valueStr.trim().isEmpty()) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, key));
        }
        return valueStr;
    }

    /**
     * Searches the given map the given key and returns the corresponding value as Long. Does some simple validation.
     *
     * @param cookieMap  Map with cookie's key-value pairs
     * @param key        Key to extract the value from
     * @param strict     If true it doesn't accept null values and throws an MalformedIdCookieException. If false it
     *                   just returns null.
     * @param cookieName Name of the cookie
     * @return Value that maps the key
     * @throws IdCookieMalformedException Throws a MalformedIdCookieException if the a cookie value is malformed.
     */
    private Long getValueAsLong(Map<String, String> cookieMap, String key, boolean strict, String cookieName)
            throws IdCookieMalformedException {
        String valueStr = cookieMap.get(key);
        if ((valueStr == null || valueStr.equals("null")) && !strict) {
            return null;
        }
        try {
            return Long.valueOf(valueStr);
        } catch (Exception e) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, key));
        }
    }

    /**
     * Searches the given map the given key and returns the corresponding value as Integer. Does some simple
     * validation.
     *
     * @param cookieMap  Map with cookie's key-value pairs
     * @param key        Key to extract the value from
     * @param strict     If true it doesn't accept null values and throws an MalformedIdCookieException. If false it
     *                   just returns null.
     * @param cookieName Name of the cookie
     * @return Value that maps the key
     * @throws IdCookieMalformedException Throws a MalformedIdCookieException if the cookie value is malformed.
     */
    private Integer getValueAsInt(Map<String, String> cookieMap, String key, boolean strict, String cookieName)
            throws IdCookieMalformedException {
        String valueStr = cookieMap.get(key);
        if ((valueStr == null || valueStr.equals("null")) && !strict) {
            return null;
        }
        try {
            return Integer.valueOf(valueStr);
        } catch (Exception e) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, key));
        }
    }

    /**
     * Discards the ID cookie that corresponds to the given study result ID. If there is no such ID cookie it does
     * nothing.
     */
    protected Http.Request discard(Http.Request request, long studyResultId) throws IdCookieAlreadyExistsException {
        IdCookieCollection idCookieCollection = request.attrs().get(ID_COOKIES);
        IdCookieModel idCookie = idCookieCollection.get(studyResultId);
        if (idCookie != null) {
            idCookieCollection.remove(idCookie);
            request = request.addAttr(ID_COOKIES, idCookieCollection);
            Publix.response().discardCookie(idCookie.getName());
        }
        return request;
    }

    /**
     * Builds Http.Cookie and puts it into Request attrs.
     */
    Http.Request write(Http.Request request, IdCookieModel newIdCookie) throws IdCookieCollectionFullException {
        IdCookieCollection idCookieCollection = request.attrs().get(ID_COOKIES);

        String cookieValue = idCookieSerialiser.asCookieValueString(newIdCookie);
        Http.Cookie.builder(newIdCookie.getName(), cookieValue).withMaxAge(Duration.of(10000, ChronoUnit.DAYS))
                .withSecure(false).withHttpOnly(false).withPath(Common.getPlayHttpContext()).build();
        idCookieCollection.put(newIdCookie);

        // Put changed idCookieCollection into Request attrs
        return request.addAttr(ID_COOKIES, idCookieCollection);
    }

}
