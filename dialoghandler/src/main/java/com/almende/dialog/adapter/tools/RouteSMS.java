package com.almende.dialog.adapter.tools;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.util.AFHttpClient;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;

public class RouteSMS {

    public static final String SMS_DELIVERY_STATUS_KEY = "SMS Delivery Status";
    public static final String SMS_STATUS_KEY = "SMS-STATUS";
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
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId,
        DDRRecord ddrRecord) throws Exception {

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
        Map<String, Session> sessionMap = DialogAgent.getSessionsFromExtras(extras);
        AFHttpClient afHttpClient = ParallelInit.getAFHttpClient();
        URIBuilder uriBuilder = new URIBuilder(url);
        uriBuilder.addParameter("username", userName);
        uriBuilder.addParameter("password", password);
        uriBuilder.addParameter("type", type.toString());
        uriBuilder.addParameter("dlr", "1");
        String destination = "";
        for (String address : addressNameMap.keySet()) {

            Session session = sessionMap != null ? sessionMap.get(address) : null;
            //check if its a mobile number, if no ignore, log, drop session and continue
            PhoneNumberType numberType = PhoneNumberUtils.getPhoneNumberType(address);
            if (!PhoneNumberType.MOBILE.equals(numberType)) {
                String errorMessage = String.format("Ignoring SMS request to: %s from: %s, as it is not of type MOBILE",
                                                    address, config.getMyAddress());
                logger.warning(config, errorMessage, session);
                //update ddr if found
                if (ddrRecord != null) {
                    ddrRecord.addStatusForAddress(address, CommunicationStatus.ERROR);
                    ddrRecord.addAdditionalInfo(address, errorMessage);
                    ddrRecord.createOrUpdate();
                }
                if (session != null) {
                    session.drop();
                    sessionMap.remove(address);
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

        boolean validSenderId = isValidSenderId(fromName);
        String result = !validSenderId ? "1707" : null; //by default assign a result
        if (!ServerUtils.isInUnitTestingEnvironment()) {
            result = afHttpClient.get(uriBuilder.build().toString()).getResponseBody();
        }
        else {
            result = result != null ? result : "1701";
            String collectiveResult = "";
            for (String address : sessionMap.keySet()) {
                collectiveResult += String.format("%s|%s:%s,", result, address, UUID.randomUUID().toString());
            }
            result = collectiveResult;
        }
        
        String validateResult = isValidResult(config, result, sessionMap);
        if (!validateResult.equalsIgnoreCase("Successfully Sent")) {
            
            throw new Exception(validateResult);
        }
        log.info("Result from RouteSMS: " + result);
        return CM.countMessageParts(message, dcs);
    }

    /**
     * Validates if the response received by RouteSMS per address.
     * 
     * @param config
     * @param resultFromRouteSMS
     * @param sessionKeyMap
     * @return
     * @throws Exception
     */
    private String isValidResult(AdapterConfig config, String resultFromRouteSMS, Map<String, Session> sessionKeyMap)
        throws Exception {

        String sendStatus = null;
        boolean isResultValid = false;

        if (resultFromRouteSMS != null) {
            String[] splitResult = resultFromRouteSMS.split(",");

            for (String splitResultPerAddress : splitResult) {

                if (!splitResultPerAddress.trim().isEmpty()) {
                    String[] resultPerAddress = splitResultPerAddress.split("\\|");
                    if (resultPerAddress.length == 2) {

                        String remoteAddress = resultPerAddress[1].split(":")[0];
                        remoteAddress = PhoneNumberUtils.formatNumber(remoteAddress, null);
                        String messageReference = null;
                        CommunicationStatus status = null;
                        if ("1701".equals(resultPerAddress[0])) {
                            sendStatus = "Successfully Sent";
                            messageReference = resultPerAddress[1].split(":")[1];
                            status = CommunicationStatus.SENT;
                            isResultValid = true;
                        }
                        else {
                            sendStatus = getSendStatus(resultPerAddress[0]);
                            isResultValid = sendStatus != null ? sendStatus.equals("Successfully Sent") : false;
                        }
                        status = status != null ? status : CommunicationStatus.ERROR;
                        //fetch the session corresponding to the address
                        Session session = sessionKeyMap != null ? sessionKeyMap.get(remoteAddress) : null;
                        if (session != null) {
                            if (isResultValid) {
                                createSMSSendData(config, sendStatus, resultPerAddress[0], remoteAddress, session,
                                                  messageReference, status);
                            }
                            else {
                                DDRRecord ddrRecord = session.getDDRRecord();
                                if(ddrRecord != null) {
                                    ddrRecord.addStatusForAddress(remoteAddress, CommunicationStatus.ERROR);
                                    ddrRecord.addAdditionalInfo(remoteAddress, sendStatus);
                                    ddrRecord.createOrUpdate();
                                }
                                session.drop();
                                sessionKeyMap.remove(remoteAddress);
                            }
                        }
                    }
                    else {
                        sendStatus = getSendStatus(resultFromRouteSMS);
                        CommunicationStatus status = "1701".equals(resultFromRouteSMS) ? CommunicationStatus.SENT
                            : CommunicationStatus.ERROR;
                        updateSMSSendData(config, sendStatus, resultFromRouteSMS, sessionKeyMap, status);
                    }
                }
                else {
                    isResultValid = false;
                }
            }
        }
        return sendStatus;
    }
    
    /** Does not create a {@link SMSDeliveryStatus} entity based on the status of the sms sent, but updates
     * the {@link DDRRecord} and the {@link Session}
     * @param config
     * @param returnResult
     * @param resultPerAddress
     * @param remoteAddress
     * @param session
     * @param messageReference
     * @param status
     * @throws Exception
     */
    private void updateSMSSendData(AdapterConfig config, String returnResult, String smsCode,
        Map<String, Session> sessionKeyMap, CommunicationStatus status) throws Exception {

        //save the sms status in the session
        if (sessionKeyMap != null && returnResult != null) {

            for (String address : sessionKeyMap.keySet()) {
                Session session = sessionKeyMap.get(address);
                if (session != null) {
                    session.addExtras(SMS_STATUS_KEY, returnResult);
                    session.storeSession();
                    //if ddr record ID is found. update it
                    if (session.getDDRRecord() != null) {
                        DDRRecord ddrRecord = session.getDDRRecord();
                        if (ddrRecord != null) {
                            ddrRecord.addStatusForAddress(address, status);
                            ddrRecord.addAdditionalInfo(address + "_" + SMS_STATUS_KEY, returnResult);
                            ddrRecord.createOrUpdate();
                        }
                    }
                    //if error. delete the session
                    if (!"Successfully Sent".equalsIgnoreCase(getSendStatus(smsCode))) {
                        session.drop();
                    }
                }
            }
        }
    }

    /** Creates a {@link SMSDeliveryStatus} entity based on the status of the sms sent. Also updates 
     * the {@link DDRRecord} and the {@link Session}
     * @param config
     * @param returnResult
     * @param resultPerAddress
     * @param remoteAddress
     * @param session
     * @param messageReference
     * @param status
     * @throws Exception
     */
    private void createSMSSendData(AdapterConfig config, String returnResult, String smsCode, String remoteAddress,
        Session session, String messageReference, CommunicationStatus status) throws Exception {

        //save the sms status in the session
        if (session != null && returnResult != null) {
            session.addExtras(SMS_STATUS_KEY, returnResult);
            session.storeSession();
            //save the status 
            SMSDeliveryStatus.storeSMSRelatedData(messageReference, session.getRemoteAddress(), config,
                                                  session.getAccountId(), session.getQuestion(), smsCode, returnResult,
                                                  session.getDdrRecordId(),
                                                  session.getAllExtras().get(AdapterConfig.ADAPTER_PROVIDER_KEY),
                                                  session);
            //if ddr record ID is found. update it
            if (session.getDDRRecord() != null) {
                DDRRecord ddrRecord = session.getDDRRecord();
                if (ddrRecord != null) {
                    ddrRecord.addStatusForAddress(remoteAddress, status);
                    ddrRecord.createOrUpdate();
                }
            }
        }
    }
    
    /**
     * Returns the sms send description based on the code
     * @param sendCode
     * @return
     */
    private String getSendStatus(String sendCode) {

        String returnResult = null;
        switch (sendCode) {
            case "1701":
                returnResult = "Successfully Sent";
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
        return returnResult;
    }
    
    /**
     * Check if the senderName is Valid or not
     * @param senderName
     * @return
     */
    private boolean isValidSenderId(String senderName) {
        boolean isValid = false;
        if(senderName != null && !senderName.isEmpty() && senderName.length() < 18) {
            if(StringUtils.isAlphanumeric(senderName) && senderName.length() < 11) {
               isValid = true; 
            }
            else if(StringUtils.isNumeric(senderName)) {
                isValid = true; 
             }
        }
        return isValid;
    }
}
