package com.almende.dialog.adapter.tools;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.apache.http.client.utils.URIBuilder;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.util.AFHttpClient;
import com.almende.dialog.util.ServerUtils;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.util.ParallelInit;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;

public class RouteSMS {

    public static final String SMS_DELIVERY_STATUS_KEY = "SMS Delivery Status";
    public static final String SMS_STATUS_KEY = "SMS Send Status";
    private static final Logger log = Logger.getLogger(RouteSMS.class.getName());
    protected static final com.almende.dialog.Logger logger = new com.almende.dialog.Logger();

    private static final String MESSAGE_TYPE_GSM7 = "0";
    private static final String MESSAGE_TYPE_UTF8 = "8";

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

    public int broadcastMessage(String message, String subject, String from, String fromName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId)
        throws Exception {

        String dcs;
        if (!CM.isGSMSeven(message)) {
            dcs = MESSAGE_TYPE_UTF8;
        }
        else {
            dcs = MESSAGE_TYPE_GSM7;
        }
        //create a RouteSMS request based on the parameters
        //add an interceptor so that send Messages is not enabled for unit tests

        //fetch the sessions
        Map<String, String> sessionKeyMap = null;
        Object sessionsObject = extras != null ? extras.get(Session.SESSION_KEY) : null;
        if (sessionsObject != null) {
            sessionKeyMap = JOM.getInstance().convertValue(sessionsObject, new TypeReference<Map<String, String>>() {
            });
        }
        AFHttpClient afHttpClient = ParallelInit.getAFHttpClient();
        URIBuilder uriBuilder = new URIBuilder(url);
        uriBuilder.addParameter("username", userName);
        uriBuilder.addParameter("password", password);
        uriBuilder.addParameter("type", type.toString());
        uriBuilder.addParameter("dlr", "1");
        String destination = "";
        for (String address : addressNameMap.keySet()) {

            Session session = sessionKeyMap != null ? Session.getSession(sessionKeyMap.get(address)) : null;
            //check if its a mobile number, if no ignore, log, drop session and continue
            PhoneNumberType numberType = PhoneNumberUtils.getPhoneNumberType(address);
            if (!PhoneNumberType.MOBILE.equals(numberType)) {
                String errorMessage = String.format("Ignoring SMS request to: %s from: %s, as it is not of type MOBILE",
                                                    address, config.getMyAddress());
                logger.warning(config, errorMessage, session);
                //update ddr if found
                String ddrRecordId = session != null ? session.getDdrRecordId() : null;
                DDRRecord ddrRecord = DDRRecord.getDDRRecord(ddrRecordId, accountId);
                if (ddrRecord != null) {
                    ddrRecord.addStatusForAddress(address, CommunicationStatus.ERROR);
                    ddrRecord.addAdditionalInfo(address, errorMessage);
                    ddrRecord.createOrUpdate();
                }

                if (session != null) {
                    session.drop();
                }
                continue;
            }
            destination += address + ",";
        }
        uriBuilder.addParameter("destination", destination);
        uriBuilder.addParameter("source", fromName != null && !fromName.isEmpty() ? fromName : from);
        uriBuilder.addParameter("message", message);
        if (wapPushURL != null) {
            uriBuilder.addParameter("url", wapPushURL);
        }

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            String result = afHttpClient.get(uriBuilder.build().toString());
            String validateResult = isValidResult(config, result, sessionKeyMap);
            if (!validateResult.equals("Successfully Sent"))
                throw new Exception(validateResult);
            log.info("Result from RouteSMS: " + result);
        }
        else {
            for (String address : destination.split(",")) {
                Session session = Session.getSession(sessionKeyMap.get(address));
                SMSDeliveryStatus.storeSMSRelatedData(UUID.randomUUID().toString(), address, config, accountId, null,
                                                      "1701", "Successfully Sent", session.getDdrRecordId(),
                                                      session.getExtras().get(AdapterConfig.ADAPTER_PROVIDER_KEY),
                                                      session.getKey());
            }
        }
        return CM.countMessageParts(message, dcs);
    }

    private String isValidResult(AdapterConfig config, String resultFromRouteSMS, Map<String, String> sessionKeyMap)
        throws Exception {

        String returnResult = null;
        if (resultFromRouteSMS != null) {
            String[] splitResult = resultFromRouteSMS.split(",");

            for (String splitResultPerAddress : splitResult) {

                if (!splitResultPerAddress.trim().isEmpty()) {
                    String[] resultPerAddress = splitResultPerAddress.split("\\|");
                    if (resultPerAddress.length == 2) {

                        String remoteAddress = resultPerAddress[1].split(":")[0];
                        remoteAddress = PhoneNumberUtils.formatNumber(remoteAddress, null);
                        //fetch the session corresponding to the address
                        Session session = sessionKeyMap != null ? Session.getSession(sessionKeyMap.get(remoteAddress))
                                                               : null;
                        String messageReference = null;
                        CommunicationStatus status = null;
                        switch (resultPerAddress[0]) {
                            case "1701":
                                returnResult = "Successfully Sent";
                                messageReference = resultPerAddress[1].split(":")[1];
                                status = CommunicationStatus.SENT;
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
                        status = status != null ? status : CommunicationStatus.ERROR;

                        //save the sms status in the session
                        if (session != null && returnResult != null) {
                            session.addExtras(SMS_STATUS_KEY, returnResult);
                            session.storeSession();
                            //save the status 
                            SMSDeliveryStatus.storeSMSRelatedData(messageReference,
                                                                  session.getRemoteAddress(),
                                                                  config,
                                                                  session.getAccountId(),
                                                                  session.getQuestion(),
                                                                  resultPerAddress[0],
                                                                  returnResult,
                                                                  session.getDdrRecordId(),
                                                                  session.getExtras()
                                                                                                  .get(AdapterConfig.ADAPTER_PROVIDER_KEY),
                                                                  session.getKey());
                            //if ddr record ID is found. update it
                            if (session.getDdrRecordId() != null) {
                                DDRRecord ddrRecord = DDRRecord.getDDRRecord(session.getDdrRecordId(),
                                                                             session.getAccountId());
                                if (ddrRecord != null) {
                                    ddrRecord.addStatusForAddress(remoteAddress, status);
                                    ddrRecord.createOrUpdate();
                                }
                            }
                        }
                    }
                }
            }
        }
        return returnResult;
    }
}
