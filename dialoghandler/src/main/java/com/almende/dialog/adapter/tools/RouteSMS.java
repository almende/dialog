package com.almende.dialog.adapter.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.http.client.utils.URIBuilder;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.AFHttpClient;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.askfast.commons.utils.PhoneNumberUtils;

public class RouteSMS {

    public static final String SMS_DELIVERY_STATUS_KEY = "SMS Delivery Status";
    public static final String SMS_STATUS_KEY = "SMS Send Status";
    private static final Logger log = Logger.getLogger(RouteSMS.class.getName());
    protected static final com.almende.dialog.Logger logger = new com.almende.dialog.Logger();

    private static final String MESSAGE_TYPE_GSM7 = "0";
    private static final String MESSAGE_TYPE_UTF8 = "8";
    private static final String MESSAGE_TYPE_BIN = "4";

    private static final String url = "http://smpp5.routesms.com:8080/bulksms/sendsms";

    private Integer type = 0;
    private String userName = "";
    private String password = "";
    private String wapPushURL = null;

    public RouteSMS(String userName, String password, Integer type, String wapPushURL) {

        this.type = type != null ? type : 0;
        this.wapPushURL = wapPushURL;
        this.userName = userName;
        this.password = password;
    }

    public int sendMessage(String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config, String accountId) throws Exception {

        HashMap<String, String> addressNameMap = new HashMap<String, String>(1);
        addressNameMap.put(to, toName);
        return broadcastMessage(message, subject, from, fromName, addressNameMap, extras, config, accountId);
    }

    public int broadcastMessage(String message, String subject, String from, String fromName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId)
        throws Exception {

        String dcs;
        if (!isGSMSeven(message)) {
            dcs = MESSAGE_TYPE_UTF8;
        }
        else {
            dcs = MESSAGE_TYPE_GSM7;
        }
        //create a RouteSMS request based on the parameters
        //add an interceptor so that send Messages is not enabled for unit tests
        if (!ServerUtils.isInUnitTestingEnvironment()) {
            AFHttpClient afHttpClient = ParallelInit.getAFHttpClient();
            URIBuilder uriBuilder = new URIBuilder(url);
            uriBuilder.addParameter("username", userName);
            uriBuilder.addParameter("password", password);
            uriBuilder.addParameter("type", type.toString());
            uriBuilder.addParameter("dlr", "1");
            String destination = "";
            for (String address : addressNameMap.keySet()) {
                destination += address + ",";
            }
            uriBuilder.addParameter("destination", destination);
            uriBuilder.addParameter("source", fromName != null && !fromName.isEmpty() ? fromName : from);
            uriBuilder.addParameter("message", message);
            if (wapPushURL != null) {
                uriBuilder.addParameter("url", wapPushURL);
            }

            String result = afHttpClient.get(uriBuilder.build().toString());
            String validateResult = isValidResult(config, result);
            if (!validateResult.equals("Successfully Sent"))
                throw new Exception(validateResult);
            log.info("Result from RouteSMS: " + result);
        }
        return countMessageParts(message, dcs);
    }

    private String isValidResult(AdapterConfig config, String resultFromRouteSMS) throws Exception {

        String returnResult = null;
        if (resultFromRouteSMS != null) {
            String[] splitResult = resultFromRouteSMS.split(",");

            for (String splitResultPerAddress : splitResult) {

                if (!splitResultPerAddress.trim().isEmpty()) {
                    String[] resultPerAddress = splitResultPerAddress.split("\\|");
                    if (resultPerAddress.length == 2) {

                        String remoteAddress = resultPerAddress[1].split(":")[0];
                        //fetch the session corresponding to the address
                        Session session = Session.getSession(config.getAdapterType(), config.getMyAddress(),
                                                             PhoneNumberUtils.formatNumber(remoteAddress, null));
                        String messageReference = null;

                        switch (resultPerAddress[0]) {
                            case "1701":
                                returnResult = "Successfully Sent";
                                messageReference = resultPerAddress[1].split(":")[1];
                                break;
                            case "1702":
                                returnResult = "1702:Invalid URL Error";
                                break;
                            case "1703":
                                returnResult = "1703:Invalid value in username or password field";
                                break;
                            case "1704":
                                returnResult = "1704:Invalid value in \"type\" field";
                                break;
                            case "1705":
                                returnResult = "1705:Invalid Message";
                                break;
                            case "1706":
                                returnResult = "1706:Invalid Destination";
                                break;
                            case "1707":
                                returnResult = "1707:Invalid Source (Sender)";
                                break;
                            case "1708":
                                returnResult = "1708:Invalid value for \"dlr\" field";
                                break;
                            case "1709":
                                returnResult = "1709:User validation failed";
                                break;
                            case "1710":
                                returnResult = "1710:Internal Error";
                                break;
                            case "1025":
                                returnResult = "1025:Insufficient Credit";
                                break;
                        }
                        //save the sms status in the session
                        if (session != null && returnResult != null) {
                            session.addExtras(SMS_STATUS_KEY, returnResult);
                            session.storeSession();
                            //save the status 
                            SMSDeliveryStatus.storeSMSRelatedData(messageReference, session.getRemoteAddress(), config,
                                                                  session.getAccountId(), session.getQuestion(),
                                                                  resultPerAddress[0], returnResult);
                        }
                    }
                }
            }
        }
        return returnResult;
    }

    public static boolean isGSMSeven(CharSequence str0) {

        if (str0 == null) {
            return true;
        }

        int len = str0.length();
        for (int i = 0; i < len; i++) {
            // get the char in this string
            char c = str0.charAt(i);
            // simple range checks for most common characters (0x20 -> 0x5F) or (0x61 -> 0x7E)
            if ((c >= ' ' && c <= '_') || (c >= 'a' && c <= '~')) {
                continue;
            }
            else {
                // 10X more efficient using a switch statement vs. a lookup table search
                switch (c) {
                    case '\u00A3': // £
                    case '\u00A5': // ¥
                    case '\u00E8': // è
                    case '\u00E9': // é
                    case '\u00F9': // ù
                    case '\u00EC': // ì
                    case '\u00F2': // ò
                    case '\u00C7': // Ç
                    case '\n': // newline
                    case '\u00D8': // Ø
                    case '\u00F8': // ø
                    case '\r': // carriage return
                    case '\u00C5': // Å
                    case '\u00E5': // å
                    case '\u0394': // Δ
                    case '\u03A6': // Φ
                    case '\u0393': // Γ
                    case '\u039B': // Λ
                    case '\u03A9': // Ω
                    case '\u03A0': // Π
                    case '\u03A8': // Ψ
                    case '\u03A3': // Σ
                    case '\u0398': // Θ
                    case '\u039E': // Ξ
                    case '\u00C6': // Æ
                    case '\u00E6': // æ
                    case '\u00DF': // ß
                    case '\u00C9': // É
                    case '\u00A4': // ¤
                    case '\u00A1': // ¡
                    case '\u00C4': // Ä
                    case '\u00D6': // Ö
                    case '\u00D1': // Ñ
                    case '\u00DC': // Ü
                    case '\u00A7': // §
                    case '\u00BF': // ¿
                    case '\u00E4': // ä
                    case '\u00F6': // ö
                    case '\u00F1': // ñ
                    case '\u00FC': // ü
                    case '\u00E0': // à
                    case '\u20AC': // €
                        continue;
                    default:
                        return false;
                }
            }
        }
        return true;
    }

    /*
     * gets the number of message parts based on the charecters in the message
     */
    public static int countMessageParts(String message) {

        String dcs;
        if (!isGSMSeven(message)) {
            dcs = MESSAGE_TYPE_UTF8;
        }
        else {
            dcs = MESSAGE_TYPE_GSM7;
        }
        return countMessageParts(message, dcs);
    }

    private static int countMessageParts(String message, String type) {

        int maxChars = 0;

        if (type.equals(MESSAGE_TYPE_GSM7)) {
            maxChars = 160;
            if (message.toCharArray().length < maxChars) // Test if concatenated message
                maxChars = 153;
        }
        else if (type.equals(MESSAGE_TYPE_UTF8)) {
            maxChars = 70;
            if (message.toCharArray().length < maxChars)
                maxChars = 67;
        }
        else if (type.equals(MESSAGE_TYPE_BIN)) {
            maxChars = 280;
            if (message.toCharArray().length < maxChars)
                maxChars = 268;
        }

        int count = Math.round((message.toCharArray().length - 1) / maxChars) + 1;
        return count;
    }
}
