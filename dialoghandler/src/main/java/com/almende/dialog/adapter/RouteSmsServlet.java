package com.almende.dialog.adapter;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.NotImplementedException;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.joda.time.DateTimeZone;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CM;
import com.almende.dialog.adapter.tools.RouteSMS;
import com.almende.dialog.adapter.tools.SMSDeliveryStatus;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.exception.NotFoundException;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.util.AFHttpClient;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.ParallelInit;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.thetransactioncompany.cors.HTTPMethod;

public class RouteSmsServlet extends TextServlet {

    private static final long serialVersionUID = 408503132941968804L;
    protected static final com.almende.dialog.Logger dialogLog = new com.almende.dialog.Logger();
    private static final String servletPath = "/_ah/sms/";
    private static final String deliveryStatusPath = "/dialoghandler/sms/route-sms/deliveryStatus";

    @Override
    protected int sendMessage(String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config, String accountId, DDRRecord ddrRecord) throws Exception {

        HashMap<String, String> addressNameMap = new HashMap<String, String>(1);
        addressNameMap.put(to, toName);
        return broadcastMessage(message, subject, from, fromName, addressNameMap, extras, config, accountId, ddrRecord);
    }

    @Override
    protected int broadcastMessage(String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId,
        DDRRecord ddrRecord) throws Exception {

        RouteSMS routeSMS = new RouteSMS(config.getAccessToken(), config.getAccessTokenSecret(), null, null);
        return routeSMS.broadcastMessage(message, subject, from, senderName, addressNameMap, extras, config, accountId,
                                         ddrRecord);
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {

        if (req.getRequestURI().startsWith(deliveryStatusPath)) {
            if (req.getMethod().equals("POST")) {
                try {
                    String responseText = "No result fetched";
                    String requestData = ServerUtils.getRequestData(req);
                    String thisHost = "http://" + Settings.HOST + "?" + requestData;
                    List<NameValuePair> nameValuePairs = URLEncodedUtils.parse(new URI(thisHost), "UTF-8");
                    String messageId = null;
                    String source = null;
                    String destination = null;
                    String status = null;
                    String sentDate = null;
                    String doneDate = null;

                    for (NameValuePair nameValuePair : nameValuePairs) {
                        String name = nameValuePair.getName();
                        switch (name) {
                            case "messageid":
                                messageId = nameValuePair.getValue();
                                break;
                            case "source":
                                source = nameValuePair.getValue();
                                break;
                            case "destination":
                                destination = nameValuePair.getValue();
                                break;
                            case "status":
                                status = nameValuePair.getValue();
                                break;
                            case "sentdate":
                                sentDate = nameValuePair.getValue();
                                break;
                            case "donedate":
                                doneDate = nameValuePair.getValue();
                                break;
                            default:
                                break;
                        }
                    }
                    //check the host in the CMStatus
                    if (messageId != null) {
                        //fetch the session
                        String host = SMSDeliveryStatus.getHostFromReference(messageId);
                        log.info(String.format("Host from reference: %s and actual host: %s", host, Settings.HOST));
                        if (host != null && !ifHostNamesMatch(host)) {
                            host += deliveryStatusPath;
                            log.info("Route-SMS delivery status is being redirect to: " + host);
                            host += ("?" + (req.getQueryString() != null ? req.getQueryString() : ""));
                            responseText = forwardToHost(host, HTTPMethod.POST, requestData);
                        }
                        else {
                            SMSDeliveryStatus routeSMSStatus = handleDeliveryStatusReport(messageId, sentDate,
                                                                                          doneDate, destination,
                                                                                          source, status);
                            if (routeSMSStatus == null) {
                                responseText = "No Route-SMS status entity found for messageId: " + messageId;
                            }
                            else {
                                responseText = ServerUtils.serializeWithoutException(routeSMSStatus);
                            }
                        }
                    }
                    res.getWriter().println(responseText);
                }
                catch (Exception e) {
                    log.severe("GET query processing failed. Message: " + e.getLocalizedMessage());
                    return;
                }
            }
            res.setStatus(HttpServletResponse.SC_OK);
        }
        else {
            super.service(req, res);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        super.doPost(req, resp);
    }

    @Override
    protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        // TODO: Needs implementation, but service not available at CM
        return null;
    }

    @Override
    protected String getServletPath() {

        return servletPath;
    }

    @Override
    protected String getAdapterType() {

        return AdapterAgent.ADAPTER_TYPE_SMS;
    }

    @Override
    protected void doErrorPost(HttpServletRequest req, HttpServletResponse res) throws IOException {

    }

    @Override
    protected DDRRecord createDDRForIncoming(AdapterConfig adapterConfig, String accountId, String fromAddress,
        String message, Session session) throws Exception {

        // Needs implementation, but service not available at CM
        throw new NotImplementedException("Attaching cost not implemented for this Adapter");
    }

    @Override
    protected DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig, String accountId, String senderName,
        Map<String, String> toAddress, String message, Map<String, Session> sessionKeyMap) throws Exception {

        //add costs with no.of messages * recipients
        return DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, accountId, senderName, toAddress,
                                                               CM.countMessageParts(message) * toAddress.size(),
                                                               message, sessionKeyMap);
    }

    /**
     * Handles the status report based on string values of the parameters sent
     * by CM. used by both POST and GET method. See {@link http
     * ://docs.cm.nl/http_SR.pdf} for more info.
     * 
     * @param messageId
     * @param sent
     * @param received
     * @param to
     * @param from
     * @param statusCode
     * @return
     * @throws Exception
     */
    private SMSDeliveryStatus handleDeliveryStatusReport(String messageId, String sent, String received, String to,
        String from, String statusCode) throws Exception {

        log.info(String.format("RouteSMS Servlet SR: reference: %s, sent: %s, received: %s, to: %s, statusCode: %s",
                               messageId, sent, received, to, statusCode));
        if (messageId != null && !messageId.isEmpty()) {
            SMSDeliveryStatus routeSMSStatus = SMSDeliveryStatus.fetch(messageId);
            to = PhoneNumberUtils.formatNumber(to, null);
            if (routeSMSStatus != null && to != null) {
                Session session = Session.getSessionByInternalKey(Session.getInternalSessionKey(routeSMSStatus
                                                .getAdapterConfig(), to));
                if (sent != null) {
                    routeSMSStatus.setSentTimeStamp(String.valueOf(TimeUtils
                                                    .getTimeWithFormat(sent, "yyyy-mm-dd hh:mm:ss",
                                                                       DateTimeZone.forID("Asia/Kolkata"), null)
                                                    .getMillis()));
                }
                if (received != null) {
                    routeSMSStatus.setDeliveredTimeStamp(String.valueOf(TimeUtils
                                                    .getTimeWithFormat(received, "yyyy-mm-dd hh:mm:ss",
                                                                       DateTimeZone.forID("Asia/Kolkata"), null)
                                                    .getMillis()));
                }
                if (to != null) {
                    routeSMSStatus.setRemoteAddress(to);
                }
                if (statusCode != null) {
                    routeSMSStatus.setDescription(statusCode);
                }
                if (routeSMSStatus.getCallback() != null && routeSMSStatus.getCallback().startsWith("http")) {
                    AFHttpClient client = ParallelInit.getAFHttpClient();
                    try {
                        String callbackPayload = ServerUtils.serialize(routeSMSStatus);
                        client.post(callbackPayload, routeSMSStatus.getCallback());
                        if (ServerUtils.isInUnitTestingEnvironment()) {
                            TestServlet.logForTest(getAdapterType(), routeSMSStatus);
                        }
                    }
                    catch (Exception ex) {
                        log.severe("Callback failed. Message: " + ex.getLocalizedMessage());
                    }
                }
                else {
                    log.info("Reference: " + messageId + ". No delivered callback found.");
                }
                //fetch ddr corresponding to this
                DDRRecord ddrRecord = DDRRecord.getDDRRecord(routeSMSStatus.getDdrRecordId(),
                                                             routeSMSStatus.getAccountId());
                if (ddrRecord != null) {
                    if (isErrorInDelivery(statusCode)) {
                        ddrRecord.addStatusForAddress(to, CommunicationStatus.ERROR);
                        ddrRecord.addAdditionalInfo(to, "ERROR: " + routeSMSStatus.getDescription());
                    }
                    else if (statusCode.equalsIgnoreCase("DELIVRD")) {
                        ddrRecord.addStatusForAddress(to, CommunicationStatus.DELIVERED);
                    }
                    else {
                        ddrRecord.addStatusForAddress(to, CommunicationStatus.SENT);
                    }
                    ddrRecord.createOrUpdate();
                }
                else {
                    log.warning(String.format("No ddr record found for id: %s", routeSMSStatus.getDdrRecordId()));
                }
                //check if session is killed. if so drop it :)
                if (session.isKilled() && isSMSsDelivered(ddrRecord)) {
                    session.drop();
                }
                routeSMSStatus.store();
            }
            else {
                log.severe(routeSMSStatus != null ? "Invalid to address" : "No CM status found");
            }
            return routeSMSStatus;
        }
        else {
            log.severe("Reference code cannot be null");
            return null;
        }
    }

    /**
     * check if all of the SMSs to toAddresses in the ddrRecord is
     * {@link CommunicationStatus#DELIVERED}
     * 
     * @param ddrRecord
     * @return true if ddrRecord is null or all SMS are delivered
     */
    private boolean isSMSsDelivered(DDRRecord ddrRecord) {

        if (ddrRecord == null) {
            return true;
        }
        else if (ddrRecord.getStatusPerAddress() != null) {
            int smsDeliveryCount = 0;
            for (String toAddress : ddrRecord.getStatusPerAddress().keySet()) {
                if (ddrRecord.getStatusForAddress(toAddress).equals(CommunicationStatus.DELIVERED)) {
                    smsDeliveryCount++;
                }
            }
            if (smsDeliveryCount == ddrRecord.getStatusPerAddress().size()) {
                return true;
            }
        }
        return false;
    }

    /**
     * forward the CM delivery status to a different host with a POST request
     * based on the host attached in the reference
     * 
     * @param host
     * @param post
     * @param payload
     * @return
     */
    private String forwardToHost(String host, HTTPMethod method, String payload) {

        try {
            AFHttpClient client = ParallelInit.getAFHttpClient();
            switch (method) {
                case GET:
                    return client.get(host).getResponseBody();
                case POST:
                    return client.post(payload, host).getResponseBody();
                default:
                    throw new NotFoundException(String.format("METHOD %s not implemented", method));
            }
        }
        catch (Exception e) {
            log.severe(String.format("Failed to send CM DLR status to host: %s. Message: %s", host,
                                     e.getLocalizedMessage()));
        }
        return null;
    }

    /**
     * checks if the given hostName contains the one that this servlet is
     * configured for any live environement subdomains. this is to fix a
     * repeated callback from CM where live.ask-fast.com would call
     * api.ask-fast.com and vice-versa infinitely.
     * 
     * @author Shravan {@link Settings#HOST}
     * @param hostName
     * @return
     */
    private boolean ifHostNamesMatch(String hostName) {

        if (hostName.contains(Settings.HOST)) {
            return true;
        }
        else if (hostName.contains("api.ask-fast.com") && Settings.HOST.contains("live.ask-fast.com")) {
            return true;
        }
        else if (hostName.contains("live.ask-fast.com") && Settings.HOST.contains("api.ask-fast.com")) {
            return true;
        }
        return false;
    }

    /**
     * Specific for RouteSMS, returns a true if the delivery code is erronous.
     * 
     * @param smsStatus
     * @return
     */
    public static boolean isErrorInDelivery(String smsStatus) {

        switch (smsStatus) {
            case "UNKNOWN":
            case "EXPIRED":
            case "DELETED":
            case "UNDELIV":
            case "REJECTD":
                return true;
            case "ACKED":
            case "ENROUTE":
            case "DELIVRD":
            case "ACCEPTED":
                return false;
            default:
                break;
        }
        return true;
    }

    @Override
    protected String getProviderType() {

        return AdapterProviders.ROUTE_SMS.toString();
    }
}
