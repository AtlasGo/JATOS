package utils.common;

import exceptions.gui.common.BadRequestException;
import exceptions.gui.common.ForbiddenException;
import exceptions.gui.common.NotFoundException;
import play.Logger;
import play.Logger.ALogger;
import play.api.mvc.RequestHeader;
import play.mvc.Http;
import scala.Option;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Optional;

/**
 * Utility class for all JATOS Controllers.
 *
 * @author Kristian Lange
 */
public class HttpUtils {

    private static final ALogger LOGGER = Logger.of(HttpUtils.class);

    /**
     * Check if the request was made via Ajax or not for Scala requests.
     */
    public static boolean isAjax(RequestHeader request) {
        Option<String> headerOption = request.headers().get("X-Requested-With");
        return headerOption.isDefined() && headerOption.get().equals("XMLHttpRequest");
    }

    /**
     * Check if the request was made via Ajax or not.
     */
    public static Boolean isAjax(Http.Request request) {
        return request.header("X-Requested-With").map(v -> v.equals("XMLHttpRequest")).orElse(false);
    }

    /**
     * Returns the request's host URL without path (including the 'play.http.context')
     * or query string. It returns the URL with the proper protocol http or https.
     */
    public static URL getHostUrl(Http.Request request) {
        try {
            String protocol = getRequestsProtocol(request);
            return new URL(protocol + "://" + request.host());
        } catch (MalformedURLException e) {
            LOGGER.error(".getHostUrl: couldn't get request's host URL", e);
        }
        // Should never happen
        return null;
    }

    public static boolean isLocalhost(Http.Request request) {
        String host = request.host();
        Optional<String> referer = request.header("referer");
        boolean isHostLocalhost = host != null && (host.contains("localhost") || host.contains("127.0.0.1"));
        boolean isRefererLocalhost = referer.map(r -> r.contains("localhost") || r.contains("127.0.0.1")).orElse(false);
        return isHostLocalhost || isRefererLocalhost;
    }

    /**
     * Returns the request's protocol, either 'http' or 'https'. It determines
     * the protocol by looking at three things: 1) it checks if the HTTP header
     * 'X-Forwarded-Proto' is set and equals 'https', 2) it checks if the HTTP
     * header 'Referer' starts with https, 3) it uses Play's
     * RequestHeader.secure() method. The 'X-Forwarded-Proto' header may be set
     * by proxies/load balancers in front of JATOS. On Amazon's AWS load
     * balancer with HTTPS/SSL the 'X-Forwarded-Proto' is not set and to still
     * determine the right protocol we use the Referer as last option.
     */
    private static String getRequestsProtocol(Http.Request request) {
        boolean isXForwardedProtoHttps = request.header("X-Forwarded-Proto").map(h -> h.equals("https")).orElse(false);
        boolean isRefererProtoHttps = request.header("Referer").map(h -> h.startsWith("https")).orElse(false);
        return isXForwardedProtoHttps || isRefererProtoHttps || request.secure() ? "https" : "http";
    }

    public static String urlEncode(String str) {
        String encodedStr = "";
        try {
            encodedStr = URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // Do nothing
        }
        return encodedStr;
    }

    public static String urlDecode(String str) {
        String decodedStr = null;
        try {
            decodedStr = URLDecoder.decode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // Do nothing
        }
        return decodedStr;
    }

    /**
     * Gets the value of to the given key in request's query string and trims
     * whitespace.
     */
    public static String getQueryString(Http.Request request, String key) {
        String value = request.getQueryString(key);
        if (value != null) {
            value = value.trim();
        }
        return value;
    }

    public static int getHttpStatus(Exception e) {
        if (e instanceof ForbiddenException) return Http.Status.FORBIDDEN;
        if (e instanceof BadRequestException) return Http.Status.BAD_REQUEST;
        if (e instanceof NotFoundException) return Http.Status.NOT_FOUND;
        return Http.Status.INTERNAL_SERVER_ERROR;
    }

}
