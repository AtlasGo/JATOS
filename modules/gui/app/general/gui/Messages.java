package general.gui;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import play.mvc.Http;
import utils.common.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handling messages (success, info, warning, error) used used in JATOS' GUI views. JATOS has two similar messaging
 * services, via Play's flash and via RequestScopeMessaging. Difference: flash has only one of each kind
 * (info/warning/error/success), but it survives a redirect (according to Play's documentation, flash scope isn't
 * reliable).
 *
 * @author Kristian Lange
 */
public class Messages {

    public static final String INFO = "info";
    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
    public static final String WARNING = "warning";

    @JsonInclude(Include.NON_NULL)
    private List<String> successList;

    @JsonInclude(Include.NON_NULL)
    private List<String> infoList;

    @JsonInclude(Include.NON_NULL)
    private List<String> warningList;

    @JsonInclude(Include.NON_NULL)
    private List<String> errorList;

    public List<String> getSuccessList() {
        return successList;
    }

    public void success(String success) {
        if (success == null) return;
        if (successList == null) successList = new ArrayList<>();
        successList.add(success);
    }

    public List<String> getInfoList() {
        return infoList;
    }

    public void info(String info) {
        if (info == null) return;
        if (infoList == null) infoList = new ArrayList<>();
        infoList.add(info);
    }

    public List<String> getWarningList() {
        return warningList;
    }

    public void warning(String warning) {
        if (warning == null) return;
        if (warningList == null) warningList = new ArrayList<>();
        warningList.add(warning);
    }

    public List<String> getErrorList() {
        return errorList;
    }

    public void error(String error) {
        if (error == null) return;
        if (errorList == null) errorList = new ArrayList<>();
        errorList.add(error);
    }

    public static String getFlashAsJson(Http.Flash flash) {
        Messages messages = new Messages();
        flash.getOptional(INFO).ifPresent(messages::info);
        flash.getOptional(SUCCESS).ifPresent(messages::success);
        flash.getOptional(ERROR).ifPresent(messages::error);
        flash.getOptional(WARNING).ifPresent(messages::warning);
        return JsonUtils.asJson(messages);
    }

}
