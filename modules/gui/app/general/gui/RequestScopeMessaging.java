package general.gui;

import play.libs.typedmap.TypedKey;
import play.mvc.Http;
import utils.common.JsonUtils;

/**
 * Passes on messages (info/warning/error/success) to the view. Uses request attrs.
 *
 * @author Kristian Lange
 */
public class RequestScopeMessaging {

    private static final TypedKey<Messages> MESSAGES = TypedKey.create("messages");

    public static String getAsJson(Http.Request request) {
        Messages messages = null;
        if (request.attrs().containsKey(MESSAGES)) {
            messages = request.attrs().get(MESSAGES);
        }
        return JsonUtils.asJson(messages);
    }

    private static Http.Request getAttrsWithMessages(Http.Request request) {
        if (!request.attrs().containsKey(MESSAGES)) {
            Messages messages = new Messages();
            return request.addAttr(MESSAGES, messages);
        } else {
            return request;
        }
    }

    public static Http.Request error(Http.Request request, String msg) {
        if (msg != null) {
            request = getAttrsWithMessages(request);
            request.attrs().get(MESSAGES).error(msg);
        }
        return request;
    }

    public static Http.Request info(Http.Request request, String msg) {
        if (msg != null) {
            request = getAttrsWithMessages(request);
            request.attrs().get(MESSAGES).info(msg);
        }
        return request;
    }

    public static Http.Request warning(Http.Request request, String msg) {
        if (msg != null) {
            request = getAttrsWithMessages(request);
            request.attrs().get(MESSAGES).warning(msg);
        }
        return request;
    }

    public static Http.Request success(Http.Request request, String msg) {
        if (msg != null) {
            request = getAttrsWithMessages(request);
            request.attrs().get(MESSAGES).success(msg);
        }
        return request;
    }

}
