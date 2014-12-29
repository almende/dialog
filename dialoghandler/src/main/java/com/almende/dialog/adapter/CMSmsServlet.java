package com.almende.dialog.adapter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.lang.NotImplementedException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CM;
import com.almende.dialog.adapter.tools.CMStatus;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.exception.NotFoundException;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.thetransactioncompany.cors.HTTPMethod;

public class CMSmsServlet extends TextServlet {

	private static final long serialVersionUID = 408503132941968804L;
	protected static final com.almende.dialog.Logger dialogLog =  new com.almende.dialog.Logger();
	private static final String servletPath = "/_ah/sms/";
	private static final String adapterType = "CM";
	private static final String deliveryStatusPath = "/dialoghandler/sms/cm/deliveryStatus";
	
    @Override
    protected int sendMessage(String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config, String accountId) throws Exception {

        String[] tokens = config.getAccessToken().split("\\|");
        CM cm = new CM(tokens[0], tokens[1], config.getAccessTokenSecret());
        return cm.sendMessage(message, subject, from, fromName, to, toName, extras, config, accountId);
    }
	
    @Override
    protected int broadcastMessage(String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId)
        throws Exception {

        String[] tokens = config.getAccessToken().split("\\|");

        CM cm = new CM(tokens[0], tokens[1], config.getAccessTokenSecret());
        return cm.broadcastMessage(message, subject, from, senderName, addressNameMap, extras, config, accountId);
    }
    
    @Override
    public void service( HttpServletRequest req, HttpServletResponse res ) throws IOException
    {
        if ( req.getRequestURI().startsWith( deliveryStatusPath ) )
        {
            if ( req.getMethod().equals( "POST" ) )
            {
                StringBuffer jb = new StringBuffer();
                String line = null;
                try
                {
                    BufferedReader reader = req.getReader();
                    while ( ( line = reader.readLine() ) != null )
                    {
                        jb.append( line );
                    }
                    String response = handleDeliveryStatusReport( jb.toString() );
                    res.getWriter().println( response );
                }
                catch ( Exception e )
                {
                    log.severe( "POST payload retrieval failed. Message: " + e.getLocalizedMessage() );
                    return;
                }
            }
            else if ( req.getMethod().equals( "GET" ) )
            {
                try
                {
                    String responseText = "No result fetched";
                    String reference = req.getParameter("reference");
                    //check the host in the CMStatus
                    if (reference != null) {
                        String hostFromReference = CMStatus.getHostFromReference(reference);
                        log.info(String.format("Host from reference: %s and actual host: %s", hostFromReference + "?" +
                                                               req.getQueryString(), Settings.HOST));
                        if (hostFromReference != null && !ifHostNamesMatch(hostFromReference)) {
                            hostFromReference += deliveryStatusPath;
                            log.info("CM delivery status is being redirect to: " + hostFromReference);
                            hostFromReference += ("?" + (req.getQueryString() != null ? req.getQueryString() : ""));
                            responseText = forwardToHost(hostFromReference, HTTPMethod.GET, null);
                        }
                        else {
                            CMStatus cmStatus = handleDeliveryStatusReport(req.getParameter("reference"),
                                                                           req.getParameter("sent"),
                                                                           req.getParameter("received"),
                                                                           req.getParameter("to"),
                                                                           req.getParameter("statuscode"),
                                                                           req.getParameter("errorcode"),
                                                                           req.getParameter("errordescription"));
                            responseText = ServerUtils.serializeWithoutException( cmStatus );
                        }
                    }
                    res.getWriter().println(responseText);
                }
                catch ( Exception e )
                {
                    log.severe( "GET query processing failed. Message: " + e.getLocalizedMessage() );
                    return;
                }
            }
            res.setStatus(HttpServletResponse.SC_OK);
        }
        else
        {
            super.service( req, res );
        }
    }

    @Override
    protected void doPost( HttpServletRequest req, HttpServletResponse resp ) throws ServletException, IOException
    {
        super.doPost( req, resp );
    }

	@Override
	protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp)
			throws Exception {
		// TODO: Needs implementation, but service not available at CM
		return null;
	}

	@Override
	protected String getServletPath() {
		return servletPath;
	}

	@Override
	protected String getAdapterType() {
		return adapterType;
	}

	@Override
	protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {}
	
    @Override
    protected DDRRecord createDDRForIncoming(AdapterConfig adapterConfig, String accountId, String fromAddress,
        String message) throws Exception {

        // Needs implementation, but service not available at CM
        throw new NotImplementedException("Attaching cost not implemented for this Adapter");
    }

    @Override
    protected DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig, String accountId, String senderName,
        Map<String, String> toAddress, String message) throws Exception {

        //add costs with no.of messages * recipients
        return DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, accountId, senderName, toAddress,
                                                               CM.countMessageParts(message) * toAddress.size(),
                                                               message);
    }

    /**
     * handles the status report based on xml payload sent by CM. If the report
     * points to a different HOST then a log is added and a NULL is returned.
     * used by POST only. See {@link http://docs.cm.nl/http_SR.pdf} for more
     * info.
     * 
     * @param payload
     * @return
     */
    private String handleDeliveryStatusReport(String payload) {

        try {
            log.info("payload seen: " + payload);
            DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
            DocumentBuilder newDocumentBuilder = newInstance.newDocumentBuilder();
            Document parse = newDocumentBuilder.parse(new ByteArrayInputStream(payload.getBytes("UTF-8")));
            Node sentNode = parse.getElementsByTagName("MESSAGES").item(0).getAttributes().getNamedItem("SENT");
            Node receivedNode = parse.getElementsByTagName("MSG").item(0).getAttributes().getNamedItem("RECEIVED");
            Node toNode = parse.getElementsByTagName("TO").item(0);
            Node referenceNode = parse.getElementsByTagName("REFERENCE").item(0);
            Node codeNode = parse.getElementsByTagName("CODE").item(0);
            Node errorCodeNode = parse.getElementsByTagName("ERRORCODE").item(0);
            Node errorDescNode = parse.getElementsByTagName("ERRORDESCRIPTION").item(0);

            String reference = referenceNode != null ? referenceNode.getTextContent() : null;
            String sent = sentNode != null ? sentNode.getTextContent() : null;
            String received = receivedNode != null ? receivedNode.getTextContent() : null;
            String to = toNode != null ? toNode.getTextContent() : null;
            String code = codeNode != null ? codeNode.getTextContent() : null;
            String errorCode = errorCodeNode != null ? errorCodeNode.getTextContent() : null;
            String errorDescription = errorDescNode != null ? errorDescNode.getTextContent() : null;

            //check the host in the CMStatus
            if (reference != null) {
                String hostFromReference = CMStatus.getHostFromReference(reference);
                log.info(String.format("Host from reference: %s and actual host: ", hostFromReference, Settings.HOST));
                if (hostFromReference != null && !ifHostNamesMatch(hostFromReference)) {
                    log.info("CM delivery status is being redirect to: " + hostFromReference);
                    hostFromReference += deliveryStatusPath;
                    return forwardToHost(hostFromReference, HTTPMethod.POST, payload);
                }
                else {
                    CMStatus cmStatus = handleDeliveryStatusReport(reference, sent, received, to, code, errorCode,
                                                                   errorDescription);
                    return ServerUtils.serializeWithoutException(cmStatus);
                }
            }
        }
        catch (Exception e) {
            log.severe("Document parse failed. \nMessage: " + e.getLocalizedMessage());
        }
        return null;
    }
    
    /**
     * handles the status report based on string values of the parameters sent by CM. 
     * used by both POST and GET method. See {@link http://docs.cm.nl/http_SR.pdf} for more info.
     * @param reference
     * @param sent
     * @param received
     * @param to
     * @param code
     * @param errorCode
     * @param errorDescription
     * @return
     * @throws Exception
     */
    private CMStatus handleDeliveryStatusReport(String reference, String sent, String received, String to, String code,
                                                String errorCode, String errorDescription) throws Exception {

        log.info(String.format("CM SR: reference: %s, sent: %s, received: %s, to: %s, statusCode: %s errorCode: %s, errorDesc: %s",
                               reference, sent, received, to, code, errorCode, errorDescription));
        if (reference != null && !reference.isEmpty()) {
            CMStatus cmStatus = CMStatus.fetch(reference);
            to = PhoneNumberUtils.formatNumber(to, null);
            if (cmStatus != null && to != null) {
                Session session = Session.getSession(Session.getSessionKey(cmStatus.getAdapterConfig(), to));
                if (sent != null) {
                    cmStatus.setSentTimeStamp(sent);
                }
                if (received != null) {
                    cmStatus.setDeliveredTimeStamp(received);
                }
                if (to != null) {
                    if (!cmStatus.getRemoteAddresses().contains(to)) {
                        log.warning("To address dont match between entity and status callback from CM !!");
                    }
                    cmStatus.addRemoteAddress(to);
                }
                if (code != null) {
                    cmStatus.setCode(code);
                }
                if (errorCode != null) {
                    cmStatus.setErrorCode(errorCode);
                }
                if (errorDescription != null) {
                    cmStatus.setErrorDescription(errorDescription);
                }
                else if (errorCode != null && !errorCode.isEmpty()) {
                    cmStatus.setErrorDescription(erroCodeMapping(Integer.parseInt(errorCode)));
                }
                if (cmStatus.getCallback() != null && cmStatus.getCallback().startsWith("http")) {
                    Client client = ParallelInit.getClient();
                    WebResource webResource = client.resource(cmStatus.getCallback());
                    try {
                        String callbackPayload = ServerUtils.serialize(cmStatus);
                        if (ServerUtils.isInUnitTestingEnvironment()) {
                            TestServlet.logForTest(cmStatus);
                        }
                        webResource.type("text/plain").post(String.class, callbackPayload);
                        dialogLog.info(cmStatus.getAdapterConfig(), String
                                                        .format("POST request with payload %s sent to: %s",
                                                                callbackPayload, cmStatus.getCallback()), session);
                    }
                    catch (Exception ex) {
                        log.severe("Callback failed. Message: " + ex.getLocalizedMessage());
                    }
                }
                else {
                    log.info("Reference: " + reference + ". No delivered callback found.");
                    dialogLog.info(cmStatus.getAdapterConfig(), "No delivered callback found for reference: " +
                                                                reference, session);
                }
                //fetch ddr corresponding to this
                if (session != null) {
                    DDRRecord ddrRecord = DDRRecord.getDDRRecord(session.getDdrRecordId(), cmStatus.getAccountId());
                    if (ddrRecord != null) {
                        if (errorCode == null || errorCode.isEmpty()) {
                            ddrRecord.addStatusForAddress(to, CommunicationStatus.DELIVERED);
                        }
                        else {
                            ddrRecord.addStatusForAddress(to, CommunicationStatus.ERROR);
                            ddrRecord.addAdditionalInfo("ERROR", cmStatus.getErrorDescription());
                        }
                        ddrRecord.createOrUpdate();
                    }
                    else {
                        log.warning(String.format("No ddr record found for id: %s", session.getDdrRecordId()));
                    }
                    //check if session is killed. if so drop it :)
                    if (session.isKilled() && isSMSsDelivered(ddrRecord)) {
                        session.drop();
                    }
                }
                else {
                    log.warning(String.format("No session attached for cm status: %s", cmStatus.getReference()));
                }
                cmStatus.store();
            }
            else {
                log.severe(cmStatus != null ? "Invalid to address" : "No CM status found" );
            }
            return cmStatus;
        }
        else {
            log.severe("Reference code cannot be null");
            return null;
        }
    }
    
    /**
     * check if all of the SMSs to toAddresses in the ddrRecord is {@link CommunicationStatus#DELIVERED}
     * @param ddrRecord
     * @return true if ddrRecord is null or all SMS are delivered
     */
    private boolean isSMSsDelivered(DDRRecord ddrRecord) {

        if(ddrRecord == null) {
            return true;
        }
        else if(ddrRecord.getStatusPerAddress() != null) {
            int smsDeliveryCount = 0;
            for (String toAddress : ddrRecord.getStatusPerAddress().keySet()) {
                if(ddrRecord.getStatusForAddress(toAddress).equals(CommunicationStatus.DELIVERED)) {
                    smsDeliveryCount++;
                }
            }
            if(smsDeliveryCount == ddrRecord.getStatusPerAddress().size()){
                return true;
            }
        }
        return false;
    }

    /**
     * forward the CM delivery status to a different host with a POST request based on the 
     * host attached in the reference
     * @param host
     * @param post 
     * @param payload
     * @return
     */
    private String forwardToHost(String host, HTTPMethod method, String payload) {

        Client client = ParallelInit.getClient();
        WebResource webResource = client.resource(host);
        try {
            switch (method) {
                case GET:
                    return webResource.type("text/plain").get(String.class);
                case POST:
                    return webResource.type("text/plain").post(String.class, payload);
                default:
                    throw new NotFoundException(String.format("METHOD %s not implemented", method));
            }
        }
        catch (Exception e) {
            log.severe(String.format("Failed to send CM DLR status to host: %s. Message: %s", host, e.getLocalizedMessage()));
        }
        return null;
    }
    
    /**
     * checks if the given hostName contains the one that this servlet is configured for any live 
     * environement subdomains. this is to fix a repeated callback from CM where live.ask-fast.com would 
     * call api.ask-fast.com and vice-versa infinitely. 
     * @author Shravan
     * {@link Settings#HOST}
     * @param hostName
     * @return
     */
    private boolean ifHostNamesMatch(String hostName) {
        if(hostName.contains(Settings.HOST)) {
            return true;
        }
        else if(hostName.contains("api.ask-fast.com") && Settings.HOST.contains("live.ask-fast.com")) {
            return true;
        }
        else if(hostName.contains("live.ask-fast.com") && Settings.HOST.contains("api.ask-fast.com")) {
            return true;
        }
        return false;
    }
    
    /**
     * gives a mapping of the error code to the error description according to 
     * Section 4. of the http://docs.cm.nl/http_SR.pdf
     * @param errorCode
     * @return
     */
    private String erroCodeMapping( int errorCode )
        {
        String result = null;
        switch ( errorCode )
        {
            case 5:
                result = "The message has been confirmed as undelivered but no detailed information related to the failure is known.";
                break;
            case 7:
                result = "Used to indicate to the client that the message has not yet been delivered due to insufficient subscriber credit but is being retried within the network.";
                break;
            case 8:
                result = "Temporary Used when a message expired (could not be delivered within the life time of the message) within the operator SMSC but is not associated with a reason for failure. ";
                break;
            case 20:
                result = "Used when a message in its current form is undeliverable.";
                break;
            case 21:
                result = "Temporary Only occurs where the operator accepts the message before performing the subscriber credit check. If there is insufficient credit then the operator will retry the message until the subscriber tops up or the message expires. If the message expires and the last failure reason is related to credit then this error code will be used.";
                break;
            case 22:
                result = "Temporary Only occurs where the operator performs the subscriber credit check before accepting the message and rejects messages if there are insufficient funds available.";
                break;
            case 23:
                result = "Used when the message is undeliverable due to an incorrect / invalid / blacklisted / permanently barred MSISDN for this operator. This MSISDN should not be used again for message submissions to this operator.";
                break;
            case 24:
                result = "Used when a message is undeliverable because the subscriber is temporarily absent, e.g. his/her phone is switch off, he/she cannot be located on the network. ";
                break;
            case 25:
                result = "Used when the message has failed due to a temporary condition in the operator network. This could be related to the SS7 layer, SMSC or gateway. ";
                break;
            case 26:
                result = "Used when a message has failed due to a temporary phone related error, e.g. SIM card full, SME busy, memory exceeded etc. This does not mean the phone is unable to receive this type of message/content (refer to error code 27).";
                break;
            case 27:
                result = "Permanent Used when a handset is permanently incompatible or unable to receive this type of message. ";
                break;
            case 28:
                result = "Used if a message fails or is rejected due to suspicion of SPAM on the operator network. This could indicate in some geographies that the operator has no record of the mandatory MO required for an MT. ";
                break;
            case 29:
                result = "Used when this specific content is not permitted on the network / shortcode. ";
                break;
            case 30:
                result = "Used when message fails or is rejected because the subscriber has reached the predetermined spend limit for the current billing period.";
                break;
            case 31:
                result = "Used when the MSISDN is for a valid subscriber on the operator but the message fails or is rejected because the subscriber is unable to be billed, e.g. the subscriber account is suspended (either voluntarily or involuntarily), the subscriber is not enabled for bill-to-phone services, the subscriber is not eligible for bill-to-phone services, etc.";
                break;
            case 33:
                result = "Used when the subscriber cannot receive adult content because of a parental lock. ";
                break;
            case 34:
                result = "Permanent Used when the subscriber cannot receive adult content because they have previously failed the age verification process. ";
                break;
            case 35:
                result = "Temporary Used when the subscriber cannot receive adult content because they have not previously completed age verification. ";
                break;
            case 36:
                result = "Temporary Used when the subscriber cannot receive adult content because a temporary communication error prevents their status being verified on the age verification platform.";
                break;
            case 37:
                result = "The MSISDN is on the national blacklist (currently only for NL: SMS dienstenfilter)";
                break;
            default:
                break;
        }
        return result;
    }
}
