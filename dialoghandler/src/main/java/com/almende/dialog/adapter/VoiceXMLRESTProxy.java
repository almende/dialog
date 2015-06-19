package com.almende.dialog.adapter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.znerd.xmlenc.XMLOutputter;
import com.almende.dialog.LogLevel;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.accounts.Recording;
import com.almende.dialog.adapter.tools.Broadsoft;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.aws.AWSClient;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.QuestionEventRunner;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.ParallelInit;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.Language;
import com.askfast.commons.entity.TTSInfo;
import com.askfast.commons.entity.TTSInfo.TTSProvider;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;

@Path("/vxml/")
public class VoiceXMLRESTProxy {

    protected static final Logger log = Logger.getLogger(VoiceXMLRESTProxy.class.getName());
    protected static final com.almende.dialog.Logger dialogLog = new com.almende.dialog.Logger();
    private static final int LOOP_DETECTION = 10;
    private static final String DTMFGRAMMAR = "dtmf2hash";
    private static final String PLAY_TRIAL_AUDIO_KEY = "playTrialAccountAudio";
    private static final int MAX_RETRIES = 1;
    protected String TIMEOUT_URL = "timeout";
    protected String UPLOAD_URL = "upload";
    protected String EXCEPTION_URL = "exception";
    @SuppressWarnings( "unused" )
    private String host = "";

    public static void killSession(Session session) {

        AdapterConfig config = session.getAdapterConfig();
        if (config != null) {
            Broadsoft bs = new Broadsoft(config);
            bs.endCall(session.getExternalSession());
        }
    }

    /**
     * Triggers a call from broadsoft to the given address
     * 
     * @param address
     * @param dialogIdOrURL
     * @param config
     * @param accountId
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String dial(String address, String dialogIdOrUrl, AdapterConfig config, String accountId,
        String bearerToken) throws Exception {

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(address, "");
        HashMap<String, String> result = dial(addressNameMap, dialogIdOrUrl, config, accountId, bearerToken);
        return result != null && !result.isEmpty() ? result.values().iterator().next() : null;
    }

    /**
     * Initiates a call to all the numbers in the addressNameMap and returns a
     * Map of <adress, SessionKey>
     * 
     * @param addressNameMap
     *            Map with address (e.g. phonenumber or email) as Key and name
     *            as value. The name is useful for email and not used for SMS
     *            etc
     * @param dialogIdOrUrl
     *            if a String with leading "http" is found its considered as a
     *            url. Else a Dialog of this id is tried t The URL on which a
     *            GET HTTPRequest is performed and expected a question JSON
     * @param config
     *            the adapterConfig which is used to perform this broadcast
     * @param accountId
     *            AccoundId initiating this broadcast. All costs are applied to
     *            this accountId
     * @param bearerToken
     *            Used to check if the account has enough credits to make a
     *            referral
     * @return
     * @throws Exception
     */
    public static HashMap<String, String> dial(Map<String, String> addressNameMap, String dialogIdOrUrl,
        AdapterConfig config, String accountId, String bearerToken) throws Exception {

        HashMap<String, String> resultSessionMap = new HashMap<String, String>();
        // If it is a broadcast don't provide the remote address because it is deceiving.
        String loadAddress = null;
        Session session = null;
        if (addressNameMap == null || addressNameMap.isEmpty()) {
            throw new Exception("No address given. Error in call request");
        }
        else if (addressNameMap.size() == 1) {
            loadAddress = addressNameMap.keySet().iterator().next();
            loadAddress = PhoneNumberUtils.formatNumber(loadAddress, null);
        }
        //create a session for the first remote address
        String firstRemoteAddress = loadAddress != null ? new String(loadAddress) : new String(addressNameMap.keySet()
                                        .iterator().next());
        firstRemoteAddress = PhoneNumberUtils.formatNumber(firstRemoteAddress, null);
        session = Session.getOrCreateSession(config, firstRemoteAddress);
        session.setAccountId(accountId);
        session.killed = false;
        session.setDirection("outbound");
        session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
        session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.BROADSOFT.toString());
        session.setAdapterID(config.getConfigId());
        session.setAccountId(accountId);
        session.addExtras(DialogAgent.BEARER_TOKEN_KEY, bearerToken);
        session.storeSession();

        //fetch the url
        String url = Dialog.getDialogURL(dialogIdOrUrl, accountId, session);

        session = session.reload();
        session.setStartUrl(url);
        session.setRemoteAddress(firstRemoteAddress);
        session.storeSession();
        
        //create a ddr record
        DDRRecord ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(config, accountId, firstRemoteAddress, 1,
                                                                              url, session.getKey());
        
        //fetch the question
        Question question = Question.fromURL(url, config.getConfigId(), loadAddress,
                                             ddrRecord != null ? ddrRecord.getId() : null,
                                             session != null ? session.getKey() : null);
        if (question != null) {

            for (String address : addressNameMap.keySet()) {

                String formattedAddress = PhoneNumberUtils.formatNumber(address, PhoneNumberFormat.E164);
                if (formattedAddress != null) {

                    //ignore the address for which the session is already created.
                    if (!formattedAddress.equals(session.getRemoteAddress())) {

                        //avoid multiple calls to be made to the same number, from the same adapter. 
                        session = Session.getSessionByInternalKey(config.getAdapterType(), config.getMyAddress(),
                                                                  formattedAddress);
                        if (session != null) {
                            String responseMessage = checkIfCallAlreadyInSession(formattedAddress, config, session);
                            if (responseMessage != null) {
                                resultSessionMap.put(formattedAddress, responseMessage);
                                continue;
                            }
                            else {
                                //recreate a fresh session
                                session.drop();
                                session = Session.createSession(config, formattedAddress);
                            }
                        }
                        else {
                            session = Session.createSession(config, formattedAddress);
                        }
                    }
                    session.killed = false;
                    session.setStartUrl(url);
                    session.setDirection("outbound");
                    session.setRemoteAddress(formattedAddress);
                    session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
                    session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.BROADSOFT.toString());
                    session.setAdapterID(config.getConfigId());
                    session.setQuestion(question);
                    session.setAccountId(accountId);
                    session.addExtras(DialogAgent.BEARER_TOKEN_KEY, bearerToken);
                    session.setDdrRecordId(ddrRecord != null ? ddrRecord.getId(): null);
                    session.storeSession();
                    
                    String extSession = "";
                    Broadsoft bs = new Broadsoft(config);
                    String subscriptiion = bs.startSubscription();
                    log.info(String.format("Calling subscription complete. Message: %s. Starting call.. ",
                                           subscriptiion));
                    extSession = bs.startCall(formattedAddress + "@outbound", session);
                    if (extSession == null) {
                        String errorMessage = String.format("Call not started between %s and %s. Error in requesting adapter provider",
                                                            config.getMyAddress(), formattedAddress);
                        log.severe(errorMessage);
                        session.drop();
                        resultSessionMap.put(address, errorMessage);
                        continue;
                    }
                    session.setExternalSession(extSession);
                    session.storeSession();
                    resultSessionMap.put(formattedAddress, session.getKey());
                }
                else {
                    resultSessionMap.put(address, "Invalid address");
                    log.severe(String.format("To address is invalid: %s. Ignoring.. ", address));
                }
            }
        }
        else {
            log.severe(String.format("Question not fetched from: %s. Request for outbound call rejected ",
                                     dialogIdOrUrl));
            dialogLog.log(LogLevel.SEVERE, session.getAdapterConfig(),
                          String.format("Question not fetched from: %s. Request for outbound call rejected ",
                                        dialogIdOrUrl), session);
        }
        if(ddrRecord != null) {
            ddrRecord.setSessionKeysFromMap(resultSessionMap);
            ddrRecord.createOrUpdate();
        }
        return resultSessionMap;
    }

    public static ArrayList<String> getActiveCalls(AdapterConfig config) {

        Broadsoft bs = new Broadsoft(config);
        return bs.getActiveCalls();
    }

    public static ArrayList<String> getActiveCallsInfo(AdapterConfig config) {

        Broadsoft bs = new Broadsoft(config);
        return bs.getActiveCallsInfo();
    }

    public static boolean killActiveCalls(AdapterConfig config) {

        Broadsoft bs = new Broadsoft(config);
        return bs.killActiveCalls();
    }

    @Path("dtmf2hash")
    @GET
    @Produces("application/srgs+xml")
    public Response getDTMF2Hash(@QueryParam("minlength") String minLength, @QueryParam("maxlength") String maxLength) {

        minLength = (minLength != null && !minLength.isEmpty()) ? minLength : "1";
        maxLength = (maxLength != null && !maxLength.isEmpty()) ? maxLength : "";
        String repeat = minLength.equals(maxLength) ? minLength : (minLength + "-" + maxLength);
        String result = "<?xml version=\"1.0\"?> " +
                        "<grammar mode=\"dtmf\" version=\"1.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.w3.org/2001/06/grammar http://www.w3.org/TR/speech-grammar/grammar.xsd\" xmlns=\"http://www.w3.org/2001/06/grammar\"  root=\"untilHash\" > " +
                        "<rule id=\"digit\"> " + "<one-of> " + "<item> 0 </item> " + "<item> 1 </item> " +
                        "<item> 2 </item> " + "<item> 3 </item> " + "<item> 4 </item> " + "<item> 5 </item> " +
                        "<item> 6 </item> " + "<item> 7 </item> " + "<item> 8 </item> " + "<item> 9 </item> " +
                        "<item> * </item> " + "</one-of> " + "</rule> " + "<rule id=\"untilHash\" scope=\"public\"> " +
                        "<one-of> " + "<item repeat=\"" + repeat + "\"><ruleref uri=\"#digit\"/></item> " +
                        "<item> # </item> " + "</one-of> " + "</rule> " + "</grammar> ";
        return Response.ok(result).build();
    }

    /**
     * This is the common entry point for all calls (both inbound and outbound)
     * when a connection is established. This is called in by the provider and
     * is configured in the default.ccxml
     * 
     * @param direction
     *            The direction in the call has been triggered.
     * @param remoteID
     *            The number to which an outbound call is done to, or an inbound
     *            call is received from. Even for an anonymous call this field
     *            is always populated.
     * @param externalRemoteID
     *            This is to make sure we distinguish anonymous calls. This
     *            field is empty when it is an anonymous call.
     * @param localID
     *            The number bought from the provider
     * @param ui
     * @return
     */
    @Path("new")
    @GET
    @Produces("application/voicexml")
    public Response getNewDialog(@QueryParam("direction") String direction, @QueryParam("remoteID") String remoteID,
        @QueryParam("externalRemoteID") String externalRemoteID, @QueryParam("localID") String localID,
        @Context UriInfo ui) {

        log.info("call started:" + direction + ":" + remoteID + ":" + localID);
        this.host = ui.getBaseUri().toString().replace(":80/", "/");
        Map<String, String> extraParams = new HashMap<String, String>();

        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, localID);
        String formattedRemoteId = remoteID;
        //format the remote number
        formattedRemoteId = PhoneNumberUtils.formatNumber(remoteID.split("@")[0], PhoneNumberFormat.E164);
        externalRemoteID = PhoneNumberUtils.formatNumber(externalRemoteID.split("@")[0], PhoneNumberFormat.E164);

        if (formattedRemoteId == null) {
            log.severe(String.format("RemoveId address is invalid: %s. Ignoring.. ", remoteID));
            return Response.ok().build();
        }

        //get or create a session based on the remoteId that is always populated.  
        String internalSessionKey = AdapterAgent.ADAPTER_TYPE_CALL + "|" + localID + "|" + formattedRemoteId;
        Session session = Session.getSessionByInternalKey(internalSessionKey);
        String url = "";
        DDRRecord ddrRecord = null;
        
        if (session != null && direction.equalsIgnoreCase("outbound")) {
            try {
                url = Dialog.getDialogURL(session.getStartUrl(), session.getAccountId(), session);
            }
            catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                dialogLog.log(LogLevel.WARNING, config,
                              String.format("Dialog url encoding failed. Error: %s ", e.toString()), session);
            }
            ddrRecord = session.getDDRRecord();
        }
        else if (direction.equals("inbound")) {
            //create a session for incoming only. Flush any existing one
            if (session != null) {
                session.drop();
            }
            //check if an existing session exists
            session = Session.getSessionByInternalKey(config.getAdapterType(), config.getMyAddress(), formattedRemoteId);
            if (session != null) {
                String responseMessage = checkIfCallAlreadyInSession(formattedRemoteId, config, session);
                if(responseMessage == null) {
                    session.drop();
                }
            }
            session = Session.createSession(config, formattedRemoteId);
            session.setAccountId(config.getOwner());
            session.setRemoteAddress(externalRemoteID);
            session.storeSession();
            url = config.getURLForInboundScenario(session);
            try {
                ddrRecord = DDRUtils.createDDRRecordOnIncomingCommunication(config, config.getOwner(),
                                                                            config.getMyAddress(), url,
                                                                            session.getKey());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            Broadsoft bs = new Broadsoft(config);
            bs.startSubscription();
        }
        
        Question question = null;
        if (session != null) {
        
            session.setStartUrl(url);
            session.setDirection(direction);
            session.setRemoteAddress(externalRemoteID);
            session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
            session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.BROADSOFT.toString());
            session.setAdapterID(config.getConfigId());
            session.storeSession();
            session.setDdrRecordId(ddrRecord != null ? ddrRecord.getId() : null);
            session.storeSession();
            
            question = session.getQuestion();
            if (question == null) {
                question = Question.fromURL(url, externalRemoteID, config.getMyAddress(), session.getDdrRecordId(),
                                            session.getKey(), extraParams);
            }
            if (!ServerUtils.isValidBearerToken(session, config, dialogLog)) {
                TTSInfo ttsInfo = ServerUtils.getTTSInfoFromSession(question, session);
                ttsInfo.setProvider(TTSProvider.VOICE_RSS);
                String insufficientCreditMessage = ServerUtils.getInsufficientMessage(ttsInfo.getLanguage());
                String ttsurl = ServerUtils.getTTSURL(ttsInfo, insufficientCreditMessage, session);
                return Response.ok(renderExitQuestion(Arrays.asList(ttsurl), session.getKey())).build();
            }
        }
        else {
            log.severe(String.format("Session not found for internalKey: %s", internalSessionKey));
            return null;
        }

        // Check if we were able to load a question
        if (question == null) {
            //If not load a default error message
            question = Question.getError(config.getPreferred_language());
        }
        session.setQuestion(question);
        session.storeSession();
        log.info("Current session info: " + ServerUtils.serializeWithoutException(session));

        if (session.getQuestion() != null) {
            //play trial account audio if the account is trial
            if (config.getAccountType() != null && config.getAccountType().equals(AccountType.TRIAL)) {
                session.addExtras(PLAY_TRIAL_AUDIO_KEY, "true");
            }
            return handleQuestion(question, config, externalRemoteID, session != null ? session.getKey() : null);
        }
        else {
            return Response.ok().build();
        }
    }

    @Path("answer")
    @GET
    @Produces("application/voicexml+xml")
    public Response answer(@QueryParam("questionId") String question_id, @QueryParam("answerId") String answer_id,
        @QueryParam("answerInput") String answer_input, @QueryParam("sessionKey") String sessionKey,
        @QueryParam("callStatus") String callStatus, @Context UriInfo ui) {

        try {
            answer_input = answer_input != null ? URLDecoder.decode(answer_input, "UTF-8") : answer_input;
        }
        catch (UnsupportedEncodingException e) {
            log.warning(String.format("Answer input decode failed for: %s", answer_input));
        }
        this.host = ui.getBaseUri().toString().replace(":80", "");
        String reply = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form><block><exit/></block></form></vxml>";
        Session session = Session.getSession(sessionKey);
        if (session != null) {
            
            Question question = session.getQuestion();
            if (question != null) {
                String responder = session.getRemoteAddress();
                
                // after referral processing
                if(callStatus!=null) {
                    
                    if ("completed".equals(callStatus)) {

                        //check if this is the parent session to a linked child session due to a redirect
                        List<Session> linkedChildSessions = session.getLinkedChildSession();
                        boolean isConnected = false;
                        for(Session linkedChildSession : linkedChildSessions) {
                            if(linkedChildSession.isCallConnected()) {
                                isConnected = true;
                                break;
                            }
                        }
                        //if the linked child session is found and not pickedup. trigger the next question
                        if (!isConnected) {

                            session.addExtras("requester", session.getLocalAddress());
                            Question noAnswerQuestion = session.getQuestion().event("timeout", "Call rejected",
                                                                                    session.getPublicExtras(),
                                                                                    responder, session);
                            return handleQuestion(noAnswerQuestion, session.getAdapterConfig(), responder, sessionKey);
                        }
                    }
                    //if call is rejected. call the hangup event
                    else {

                        session.addExtras("requester", session.getLocalAddress());
                        Question noAnswerQuestion = session.getQuestion().event("timeout", "Call rejected",
                                                                                session.getPublicExtras(), responder,
                                                                                session);
                        return handleQuestion(noAnswerQuestion, session.getAdapterConfig(), responder, sessionKey);
                    }
                }
                
                // normal answer processing
                if (session.killed) {
                    log.warning("session is killed");
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
                String answerForQuestion = question.getQuestion_expandedtext(session.getKey());
                boolean isExit = false;
                if ("exit".equalsIgnoreCase(question.getType())) {
                    isExit = true;
                }
                question = question.answer(responder, session.getAdapterConfig().getConfigId(), answer_id,
                                           answer_input, session);
                //reload the session
                session = Session.getSession(sessionKey);
                session.setQuestion(question);
                session.storeSession();
                //check if ddr is in session. save the answer in the ddr
                if (session.getDDRRecord() != null) {
                    try {
                        DDRRecord ddrRecord = session.getDDRRecord();
                        if (ddrRecord != null) {
                            ddrRecord.addAdditionalInfo(DDRRecord.ANSWER_INPUT_KEY + ":" + answerForQuestion,
                                                        answer_input);
                            ddrRecord.createOrUpdateWithLog(session);
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                //the answered event is triggered if there are no next requests to process and the previous question 
                //was not an exit question (which would also give a null question on question.answer())
                if (question == null && !isExit) {
                    answered(session.getDirection(), session.getRemoteAddress(), session.getLocalAddress(),
                             session.getKey());
                }
                return handleQuestion(question, session.getAdapterConfig(), responder, sessionKey);
            }
            else {
                log.warning("No question found in session!");
            }
        }
        else {
            log.warning("No session found for: " + sessionKey);
            dialogLog.severe(null, "No session found!", session);
        }
        return Response.ok(reply).build();
    }
    
    @Path("preconnect")
    @GET
    @Produces("application/voicexml")
    public Response preconnect(@QueryParam("remoteID") String remoteID, @QueryParam("localID") String localID,
        @QueryParam("redirectID") String redirectID, @Context UriInfo ui) {

        String reply = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form><block><exit/></block></form></vxml>";
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, localID);
        String formattedAddress = PhoneNumberUtils.formatNumber(redirectID, PhoneNumberFormat.E164);

        String internalSessionKey = AdapterAgent.ADAPTER_TYPE_CALL + "|" + localID + "|" + formattedAddress;
        Session session = Session.getSessionByInternalKey(internalSessionKey);
        if (session != null) {
            if (session.getQuestion() != null) {
                Question question = session.getQuestion();
                String responder = session.getRemoteAddress();

                if (session.killed) {
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
                dialogLog.log(LogLevel.INFO,
                              session.getAdapterConfig(),
                              String.format("Wrong answer received from: %s for question: %s", responder,
                                            question.getQuestion_expandedtext(session.getKey())), session);

                HashMap<String, String> extras = new HashMap<String, String>();
                extras.put("sessionKey", session.getKey());
                extras.put("requester", session.getLocalAddress());
                question = question.event("preconnect", "preconnect event", extras, responder, session);
                // If there is no preconnect the isCallPickedUp is never set
                if (question == null) {
                    session.setCallPickedUpStatus(true);
                    session.storeSession();
                    answered(session.getDirection(), formattedAddress, localID, session.getKey());
                }
                //reload the session
                session = Session.getSession(session.getKey());
                session.setQuestion(question);
                session.storeSession();
                return handleQuestion(question, config, formattedAddress, session.getKey());
            }
            else {
                session.setCallPickedUpStatus(true);
                session.storeSession();
                answered(session.getDirection(), formattedAddress, localID, session.getKey());
            }
        }
        else {
            log.warning("No session found for: " + internalSessionKey);
        }
        return Response.ok(reply).build();
    }
    
    @Path("connected")
    @GET
    @Produces("application/voicexml")
    public Response connected(@QueryParam("remoteID") String remoteID, @QueryParam("localID") String localID,
                                        @QueryParam("redirectID") String redirectID, @Context UriInfo ui) {
        String result = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form><block><exit/></block></form></vxml>";
        String formattedAddress = PhoneNumberUtils.formatNumber(redirectID, PhoneNumberFormat.E164);
        //String formattedRemoteAddress = PhoneNumberUtils.formatNumber(remoteID, PhoneNumberFormat.E164);
        
        String internalSessionKey = AdapterAgent.ADAPTER_TYPE_CALL + "|" + localID + "|" + formattedAddress;
        Session session = Session.getSessionByInternalKey(internalSessionKey);
        session.setCallConnectedStatus( true );
        session.storeSession();
        return Response.ok(result).build();
    }

    @Path("timeout")
    @GET
    @Produces("application/voicexml+xml")
    public Response timeout(@QueryParam("questionId") String question_id, @QueryParam("sessionKey") String sessionKey)
        throws Exception {

        String reply = "<vxml><exit/></vxml>";
        Session session = Session.getSession(sessionKey);
        if (session != null) {
            Question question = session.getQuestion();
            String responder = session.getRemoteAddress();
            if (session.killed) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            dialogLog.log(LogLevel.INFO,
                          session.getAdapterConfig(),
                          String.format("Timeout from: %s for question: %s", responder,
                                        question.getQuestion_expandedtext(session.getKey())), session);
            HashMap<String, Object> extras = new HashMap<String, Object>();
            extras.put("sessionKey", sessionKey);
            extras.put("requester", session.getLocalAddress());
            question = question.event("timeout", "No answer received", extras, responder, session);
            session.setQuestion(question);
            if (question != null) {
                String retryLimit = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.RETRY_LIMIT);
                retryLimit = retryLimit != null ? retryLimit : String.valueOf(Question.DEFAULT_MAX_QUESTION_LOAD);
                Integer retryCount = session.getRetryCount();
                retryCount = retryCount != null ? retryCount : 0;
                if (retryCount < Integer.parseInt(retryLimit)) {
                    session.setRetryCount(++retryCount);
                }
                else {
                    //hangup so set question to null
                    question = null;
                }
            }
            else {
                log.warning("No question found for this session :" + sessionKey);
            }
            session.storeSession();
            return handleQuestion(question, session.getAdapterConfig(), responder, sessionKey);
        }
        else {
            log.warning("Strange that no session is found for: " + sessionKey);
        }
        return Response.ok(reply).build();
    }

    @Path("exception")
    @GET
    @Produces("application/voicexml+xml")
    public Response
        exception(@QueryParam("questionId") String question_id, @QueryParam("sessionKey") String sessionKey) {

        String reply = "<vxml><exit/></vxml>";
        Session session = Session.getSession(sessionKey);
        if (session != null && session.getQuestion() != null) {
            Question question = session.getQuestion();
            String responder = session.getRemoteAddress();

            if (session.killed) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            dialogLog.log(LogLevel.INFO,
                          session.getAdapterConfig(),
                          String.format("Wrong answer received from: %s for question: %s", responder,
                                        question.getQuestion_expandedtext(session.getKey())), session);

            HashMap<String, String> extras = new HashMap<String, String>();
            extras.put("sessionKey", sessionKey);
            extras.put("requester", session.getLocalAddress());
            question = question.event("exception", "Wrong answer received", extras, responder, session);
            //reload the session
            session = Session.getSession(sessionKey);
            session.setQuestion(question);
            session.storeSession();
            return handleQuestion(question, session.getAdapterConfig(), responder, sessionKey);
        }
        return Response.ok(reply).build();
    }

    /**
     * hang up a call based on the session.
     * 
     * @param session
     *            if null, doesnt trigger an hangup event. Also expects a
     *            question to be there in this session, or atleast a startURL
     *            from where the question can be fetched.
     * @return
     * @throws Exception
     */
    public Response hangup(Session session) throws Exception {

        if (session != null) {
            log.info("call hangup with:" + session.getDirection() + ":" + session.getRemoteAddress() + ":" +
                     session.getLocalAddress());
            if (session.getQuestion() == null) {
                Question question = Question.fromURL(session.getStartUrl(), session.getRemoteAddress(),
                                                     session.getLocalAddress(), session.getDdrRecordId(),
                                                     session.getKey(), new HashMap<String, String>());
                session.setQuestion(question);
            }
            if (session.getQuestion() != null && !isEventTriggered("hangup", session, false)) {

                HashMap<String, Object> timeMap = getTimeMap(session.getStartTimestamp(), session.getAnswerTimestamp(),
                                                             session.getReleaseTimestamp());
                timeMap.put("referredCalledId", session.getAllExtras().get("referredCalledId"));
                timeMap.put("sessionKey", session.getKey());
                if (session.getAllExtras() != null && !session.getAllExtras().isEmpty()) {
                    timeMap.putAll(session.getPublicExtras());
                }
                Response hangupResponse = handleQuestion(null, session.getAdapterConfig(), session.getRemoteAddress(),
                                                         session.getKey());
                timeMap.put("requester", session.getLocalAddress());
                session.getQuestion().event("hangup", "Hangup", timeMap, session.getRemoteAddress(), session);
                return hangupResponse;
            }
            else {
                log.info("no question received");
            }
        }
        return Response.ok("").build();
    }

    /**
     * used to trigger answered event unlike
     * {@link VoiceXMLRESTProxy#answer(String, String, String, String, UriInfo)}
     * 
     * @return
     * @throws Exception
     */
    public Response answered(String direction, String remoteID, String localID, String sessionKey) {

        log.info("call answered with:" + direction + "_" + remoteID + "_" + localID);
        Session session = Session.getSession(sessionKey);
        //for direction = transfer (redirect event), json should not be null        
        //make sure that the answered call is not triggered twice
        if (session != null && session.getQuestion() != null && !isEventTriggered("answered", session, true)) {
            String responder = session.getRemoteAddress();
            String referredCalledId = session.getAllExtras().get("referredCalledId");
            //HashMap<String, Object> timeMap = getTimeMap(startTime, answerTime, null);
            HashMap<String, Object> timeMap = new HashMap<String, Object>();
            timeMap.put("referredCalledId", referredCalledId);
            timeMap.put("sessionKey", sessionKey);
            if (session.getParentSessionKey() != null) {
                timeMap.put(Session.PARENT_SESSION_KEY, session.getParentSessionKey());
            }
            timeMap.put("requester", session.getLocalAddress());
            QuestionEventRunner questionEventRunner = new QuestionEventRunner(session.getQuestion(), "answered",
                                                                              "Answered", responder, timeMap, session);
            Thread questionEventRunnerThread = new Thread(questionEventRunner);
            questionEventRunnerThread.start();
            dialogLog.log(LogLevel.INFO, session.getAdapterConfig(),
                          String.format("Call from: %s answered by: %s", session.getLocalAddress(), responder), session);
        }
        return Response.ok("").build();
    }

    @Path("cc")
    @POST
    public Response receiveCCMessage(String xml) {

        log.info("Received cc: " + xml);
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document dom = db.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
            Node subscriberId = dom.getElementsByTagName("subscriberId").item(0);
            Node subscriptionIdNode = dom.getElementsByTagName("subscriptionId").item(0);
            String subscriptionId = subscriptionIdNode.getTextContent();

            AdapterConfig config = AdapterConfig.findAdapterConfigByUsername(subscriberId.getTextContent());

            if (!config.getXsiSubscription().equals(subscriptionId)) {
                log.warning("Ignoring because of subscriptionId: " + subscriptionId + " doesn't match :" +
                            config.getXsiSubscription());
                return Response.ok().build();
            }

            Node eventData = dom.getElementsByTagName("eventData").item(0);
            // check if incall event
            if (eventData.getChildNodes().getLength() > 1) {
                Node call = eventData.getChildNodes().item(1);
                Node personality = null;
                Node callState = null;
                Node remoteParty = null;
                Node releaseCause = null;
                Node answerTime = null;
                Node releaseTime = null;
                Node startTime = null;

                for (int i = 0; i < call.getChildNodes().getLength(); i++) {
                    Node node = call.getChildNodes().item(i);
                    if (node.getNodeName().equals("personality")) {
                        personality = node;
                    }
                    else if (node.getNodeName().equals("callState")) {
                        callState = node;
                    }
                    else if (node.getNodeName().equals("remoteParty")) {
                        remoteParty = node;
                    }
                    else if (node.getNodeName().equals("releaseCause")) {
                        releaseCause = node;
                    }
                    else if (node.getNodeName().equals("startTime")) {
                        startTime = node;
                    }
                    else if (node.getNodeName().equals("answerTime")) {
                        answerTime = node;
                    }
                    else if (node.getNodeName().equals("releaseTime")) {
                        releaseTime = node;
                    }
                }

                if (callState != null && callState.getNodeName().equals("callState")) {

                    // Check if call
                    if (callState.getTextContent().equals("Released") || callState.getTextContent().equals("Active")) {
                        String startTimeString = startTime != null ? startTime.getTextContent() : null;
                        String answerTimeString = answerTime != null ? answerTime.getTextContent() : null;
                        String releaseTimeString = releaseTime != null ? releaseTime.getTextContent() : null;

                        // Check if a sip or network call
                        String type = "";
                        String address = "";
                        String fullAddress = "";
                        for (int i = 0; i < remoteParty.getChildNodes().getLength(); i++) {
                            Node rpChild = remoteParty.getChildNodes().item(i);
                            if (rpChild.getNodeName().equals("address")) {
                                address = rpChild.getTextContent();
                            }
                            else if (rpChild.getNodeName().equals("userId")) {
                                String sipAddress = rpChild.getTextContent();
                                sipAddress = sipAddress.replace( "@ask.ask.voipit.nl", "" );
                                if(PhoneNumberUtils.getPhoneNumberType( sipAddress ) != PhoneNumberType.UNKNOWN) {
                                    address = sipAddress;
                                }
                            } 
                            else if (rpChild.getNodeName().equals("callType")) {
                                type = rpChild.getTextContent();
                            }
                        }

                        fullAddress = new String(address);
                        // Check if session can be matched to call
                        if (type.equals("Network") || type.equals("Group") || type.equals("Unknown")) {

                            address = address.replace("tel:", "").replace("sip:", "");
                            address = URLDecoder.decode(address, "UTF-8");
                            log.info("Going to format phone number: " + address);
                            String[] addressArray = address.split("@");
                            address = PhoneNumberUtils.formatNumber(addressArray[0], null);
                            String formattedAddress = address != null ? new String(address) : addressArray[0];
                            if (address != null) {
                                if (addressArray.length > 1) {
                                    address += "@" + addressArray[1];
                                }

                                String sessionKey = config.getAdapterType() + "|" + config.getMyAddress() + "|" +
                                                    formattedAddress;
                                
                                Session session = Session.getSessionByInternalKey(sessionKey);
                                if (session != null) {

                                    log.info("Session key: " + sessionKey);
                                    String direction = "inbound";
                                    if (personality.getTextContent().equals("Originator") &&
                                        !address.contains("outbound")) {
                                        //address += "@outbound";
                                        direction = "transfer";
                                        log.info("Transfer detected????");

                                        //when the receiver hangs up, an active callstate is also triggered. 
                                        // but the releaseCause is also set to Temporarily Unavailable
                                        if (callState.getTextContent().equals("Active")) {
                                            if (releaseCause == null ||
                                                (releaseCause != null &&
                                                 !releaseCause.getTextContent().equalsIgnoreCase("Temporarily Unavailable") 
                                                 && !releaseCause.getTextContent().equalsIgnoreCase("User Not Found"))) {

                                                session.setDirection(direction);
                                                session.setAnswerTimestamp(answerTimeString);
                                                session.setStartTimestamp(startTimeString);
                                                /*if (session.getQuestion() == null) {
                                                    
                                                    Question questionFromIncomingCall = Session
                                                                                    .getQuestionFromDifferentSession(config.getConfigId(),
                                                                                                                     "inbound",
                                                                                                                     "referredCalledId",
                                                                                                                     session.getRemoteAddress());
                                                    if (questionFromIncomingCall != null) {
                                                        session.setQuestion(questionFromIncomingCall);
                                                        session.storeSession();
                                                    }
                                                }*/
                                                session.storeSession();
                                                answered(direction, address, config.getMyAddress(), session.getKey());
                                            }
                                            //a reject from the remote user. initiate a hangup event
                                            //                                        else{
                                            //                                            //drop the session first and then call hangup
                                            //                                            return hangup(session);
                                            //                                        }
                                        }
                                    }
                                    else if (personality.getTextContent().equals("Originator")) {
                                        log.info("Outbound detected?????");
                                        direction = "outbound";
                                    }
                                    else if (personality.getTextContent().equals("Click-to-Dial")) {
                                        log.info("CTD hangup detected?????");
                                        direction = "outbound";

                                        //TODO: move this to internal mechanism to check if call is started!
                                        if (releaseCause.getTextContent().equals("Server Failure") ||
                                            releaseCause.getTextContent().equals("Request Failure")) {

                                            log.severe("Need to restart the call!!!! ReleaseCause: " +
                                                       releaseCause.getTextContent());

                                            int retry = session.getRetryCount() != null ? session.getRetryCount() : 0;
                                            if (retry < MAX_RETRIES) {

                                                Broadsoft bs = new Broadsoft(config);
                                                String extSession = bs.startCall(address, session);
                                                log.info("Restarted call extSession: " + extSession);
                                                retry++;
                                                session.setRetryCount(retry);
                                            }
                                            else {
                                                // TODO: Send mail to support!!!
                                                log.severe("Retries failed!!!");
                                            }
                                        }
                                    }
                                    //store or update the session
                                    session.storeSession();

                                    if (callState.getTextContent().equals("Released")) {
                                        boolean callReleased = false;

                                        if (session != null && direction != "transfer" &&
                                            !personality.getTextContent().equals("Terminator") &&
                                            fullAddress.startsWith("tel:")) {
                                            log.info("SESSSION FOUND!! SEND HANGUP!!!");
                                            callReleased = true;
                                        }
                                        else {
                                            if (personality.getTextContent().equals("Originator") &&
                                                fullAddress.startsWith("sip:")) {

                                                log.info("Probably a disconnect of a sip. not calling hangup event");
                                            }
                                            else if (personality.getTextContent().equals("Originator") &&
                                                     fullAddress.startsWith("tel:")) {
                                                log.info("Probably a disconnect of a redirect. call hangup event");
                                                callReleased = true;
                                            }
                                            else if (personality.getTextContent().equals("Terminator")) {
                                                log.info("No session for this inbound?????");
                                                callReleased = true;
                                            }
                                            else {
                                                log.info("What the hell was this?????");
                                                log.info("Session already ended?");
                                            }
                                        }
                                        //update session with call timings
                                        if (callReleased) {
                                            //sometimes answerTimeStamp is only given in the ACTIVE ccxml
//                                            String answerTimestamp = session.getAnswerTimestamp();
//                                            answerTimeString = (answerTimestamp != null && answerTimeString == null) ? answerTimestamp
//                                                                                                                    : answerTimeString;
                                            session.setAnswerTimestamp(answerTimeString);
                                            session.setStartTimestamp(startTimeString);
                                            session.setReleaseTimestamp(releaseTimeString);
                                            session.setDirection(direction);
                                            session.setRemoteAddress(address);
                                            session.setLocalAddress(config.getMyAddress());
                                            session.storeSession();
                                            log.info(String.format("Call ended. session updated: %s",
                                                                   ServerUtils.serialize(session)));
                                            //flush the keys if ddrProcessing was successful
                                            if (DDRUtils.stopDDRCosts(session.getKey())) {
                                                Thread.sleep( 1000 ); // Wait one seconds to process the rest of the dialog
                                                session.drop();
                                            }
                                            hangup(session);
                                        }
                                    }
                                }
                            }
                            else {
                                log.severe(String.format("Address is invalid: %s. Ignoring.. ", addressArray[0]));
                            }
                        }
                        else {
                            log.warning("Can't handle hangup of type: " + type + " (yet)");
                        }
                    }
                }
            }
            else {
                Node eventName = dom.getElementsByTagName("eventName").item(0);
                log.info("EventName: " + eventName.getTextContent());
                if (eventName != null && eventName.getTextContent().equals("SubscriptionTerminatedEvent")) {

                    Broadsoft bs = new Broadsoft(config);
                    String newId = bs.restartSubscription(subscriptionId);
                    if (newId != null) {
                        log.info("Start a new dialog");
                    }
                }
                log.info("Received a subscription update!");
            }
        }
        catch (Exception e) {
            log.severe("Something failed: " + e.getMessage());
            e.printStackTrace();
        }
        return Response.ok("").build();
    }

    /**
     * endpoint for tts functionality
     * 
     * @param textForSpeech
     *            actually text that has to be spoken
     * @param language
     *            format "language-country" check the full link at {@link http
     *            ://www.voicerss.org/api/documentation.aspx VoiceRSS}
     * @param contentType
     *            file format
     * @param speed
     *            -10 to 10
     * @param format
     *            audio formats
     * @param req
     * @param resp
     */
    @GET
    @Path("tts/{textForSpeech}")
    public Response redirectToSpeechEngine(@PathParam("textForSpeech") String textForSpeech,
        @QueryParam("hl") @DefaultValue("nl-nl") String language,
        @QueryParam("c") @DefaultValue("wav") String contentType, @QueryParam("r") @DefaultValue("0") String speed,
        @QueryParam("f") @DefaultValue("8khz_8bit_mono") String format, @Context HttpServletRequest req,
        @Context HttpServletResponse resp) throws IOException, URISyntaxException {

        TTSInfo ttsInfo = new TTSInfo();
        ttsInfo.setCodec(contentType);
        ttsInfo.setFormat(format);
        ttsInfo.setLanguage(Language.getByValue(language));
        ttsInfo.setSpeed(speed);
        String ttsURL = ServerUtils.getTTSURL(ttsInfo, textForSpeech, null);
        return Response.seeOther(new URI(ttsURL)).build();
    }

    /**
     * simple endpoint for repeating a question based on its session and
     * question id
     * 
     * @param sessionKey
     * @param questionId
     * @return
     * @throws Exception
     */
    @GET
    @Path("retry")
    public Response retryQuestion(@QueryParam("sessionKey") String sessionKey) throws Exception {

        Session session = Session.getSession(sessionKey);
        if (session != null && session.getQuestion() != null) {
            return handleQuestion(session.getQuestion(), session.getAdapterConfig(), session.getRemoteAddress(),
                                  sessionKey);
        }
        return Response.ok("<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form><block><exit/></block></form></vxml>")
                                        .build();
    }
    
    @POST
    @Path("upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/voicexml+xml")
    public Response doUpload(@QueryParam("questionId") String question_id, @QueryParam("sessionKey") String sessionKey,
                         @Context UriInfo ui, @Context HttpServletRequest req, @Context HttpServletResponse res)
                    throws URISyntaxException {
        
        this.host = ui.getBaseUri().toString().replace(":80", "");
        
        String reply = "<vxml><exit/></vxml>";
        Session session = Session.getSession(sessionKey);
        if (session != null) {
            String answer_input = storeAudioFile(req, session.getAccountId(), session.getDdrRecordId(), session.getAdapterID());
            Question question = session.getQuestion();
            if (question != null) {
                String responder = session.getRemoteAddress();
                if (session.killed) {
                    log.warning("session is killed");
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
                if (question.getType() != null && !question.getType().equalsIgnoreCase("comment")) {
                    dialogLog.log(LogLevel.INFO,
                                  session.getAdapterConfig(),
                                  String.format("Answer input: %s from: %s to question: %s", answer_input,
                                                session.getRemoteAddress(),
                                                question.getQuestion_expandedtext(session.getKey())), session);
                }
                
                String answerForQuestion = question.getQuestion_expandedtext(session.getKey());
                // If the recording is empty end the call.
                if(answer_input!=null) {
                    question = question.answer(responder, session.getAdapterConfig().getConfigId(), null, answer_input,
                                               session);
                } else {
                    question = null;
                }
                //reload the session
                session = Session.getSession(sessionKey);
                if(session!=null) {
                    session.setQuestion(question);
                    session.storeSession();
                    //check if ddr is in session. save the answer in the ddr
                    if (session.getDdrRecordId() != null) {
                        try {
                            DDRRecord ddrRecord = DDRRecord.getDDRRecord(session.getDdrRecordId(), session.getAccountId());
                            if (ddrRecord != null) {
                                ddrRecord.addAdditionalInfo(DDRRecord.ANSWER_INPUT_KEY + ":" + answerForQuestion,
                                                            answer_input);
                                ddrRecord.createOrUpdateWithLog(session);
                            }
                        }
                        catch (Exception e) {
                        }
                    }
                    return handleQuestion(question, session.getAdapterConfig(), responder, sessionKey);
                }
            }
            else {
                log.warning("No question found in session!");
            }
        }
        else {
            log.warning("No session found for: " + sessionKey);
            dialogLog.severe(null, "No session found!", session);
        }
        return Response.ok(reply).build();
        
        
        
    }

    public class Return {

        ArrayList<String> prompts;
        Question question;

        public Return(ArrayList<String> prompts, Question question) {

            this.prompts = prompts;
            this.question = question;
        }
    }

    public Return formQuestion(Question question, String adapterID, String address, String ddrRecordId,
        String sessionKey) {

        ArrayList<String> prompts = new ArrayList<String>();
        for (int count = 0; count <= LOOP_DETECTION; count++) {
            if (question == null)
                break;
            log.info("Going to form question of type: " + question.getType());
            if (question.getType() == null) {
                question = null;
                break;
            }
            String preferred_language = question.getPreferred_language();
            question.setPreferred_language(preferred_language);
            String qText = question.getQuestion_text();

            if (qText != null && !qText.equals(""))
                prompts.add(qText);

            if (question.getType().equalsIgnoreCase("closed")) {
                for (Answer ans : question.getAnswers()) {
                    String answer = ans.getAnswer_text();
                    if (answer != null && !answer.equals(""))
                        prompts.add(answer);
                }
                break; //Jump from forloop
            }
            else if (question.getType().equalsIgnoreCase("comment")) {
                // If it is a comment directly read the next question, because we can append the prompts.
                //question = question.answer( null, adapterID, null, null, sessionKey );
                break;
            }
            else if (question.getType().equalsIgnoreCase("referral")) {
                if (question.getUrl() != null && question.getUrl().size() == 1 && !question.getUrl().get(0).startsWith("tel:")) {
                    question = Question.fromURL(question.getUrl().get(0), adapterID, address, ddrRecordId, sessionKey);
                    //question = question.answer(null, null, null);
                    //					break;
                }
                else {
                    // Break out because we are going to reconnect
                    break;
                }
            }
            else {
                break; //Jump from forloop (open questions, etc.)
            }
        }
        return new Return(prompts, question);
    }

    protected String renderComment(Question question, ArrayList<String> prompts, String sessionKey) {

        String redirectTimeoutProperty = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT);
        //assign a default timeout if one is not specified
        String redirectTimeout = redirectTimeoutProperty != null ? redirectTimeoutProperty : "40s";
        if (!redirectTimeout.endsWith("s")) {
            log.warning("Redirect timeout must be end with 's'. E.g. 40s. Found: " + redirectTimeout);
            redirectTimeout += "s";
        }

        String redirectTypeProperty = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TYPE);
        String redirectType = redirectTypeProperty != null ? redirectTypeProperty.toLowerCase() : "bridge";
        if (!redirectType.equals("blind") && !redirectType.equals("bridge")) {
            log.warning("Redirect must be blind or bridge. Found: " + redirectTimeout);
            redirectTypeProperty = "bridge";
        }

        StringWriter sw = new StringWriter();
        try {
            XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
            outputter.declaration();
            outputter.startTag("vxml");
            outputter.attribute("version", "2.1");
            outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
            outputter.startTag("form");
            if (question != null && question.getType().equalsIgnoreCase("referral")) {

                String address = question.getUrl().get(0);
                if (DDRUtils.validateAddressAndUpdateDDRIfInvalid(address, sessionKey)) {
                    
                    outputter.startTag("transfer");
                    outputter.attribute("name", "thisCall");
                    outputter.attribute("dest", question.getUrl().get(0));
                    if (redirectType.equals("bridge")) {
                        outputter.attribute("bridge", "true");
                    }
                    else {
                        outputter.attribute("bridge", "false");
                    }
                    outputter.attribute("connecttimeout", redirectTimeout);
                    for (String prompt : prompts) {
                        outputter.startTag("prompt");
                        outputter.startTag("audio");
                        outputter.attribute("src", prompt);
                        outputter.endTag(); // prompt
                        outputter.endTag(); // audio
                    }
                    outputter.startTag("filled");
                    outputter.startTag("if");
                    outputter.attribute("cond", "thisCall=='unknown'");
                    outputter.startTag("goto");
                    outputter.attribute("next", getAnswerUrl() + "?questionId=" + question.getQuestion_id() +
                        "&sessionKey=" + URLEncoder.encode(sessionKey, "UTF-8") + "&callStatus=completed");
                    outputter.endTag(); // goto

                    outputter.startTag("else");
                    outputter.endTag(); // else 
                    outputter.startTag("goto");
                    outputter.attribute("expr", "'" + getAnswerUrl() + "?questionId=" + question.getQuestion_id() +
                        "&sessionKey=" + URLEncoder.encode(sessionKey, "UTF-8") + "&callStatus=' + thisCall");
                    outputter.endTag(); // goto
                    outputter.endTag(); // if
                    outputter.endTag(); // filled
                    outputter.endTag(); // transfer
                }
            }
            else {
                outputter.startTag("block");
                for (String prompt : prompts) {
                    outputter.startTag("prompt");
                    outputter.startTag("audio");
                    outputter.attribute("src", prompt);
                    outputter.endTag();
                    outputter.endTag();
                }
                if (question != null) {
                    outputter.startTag("goto");
                    outputter.attribute("next", getAnswerUrl() + "?questionId=" + question.getQuestion_id() +
                                                "&sessionKey=" + URLEncoder.encode(sessionKey, "UTF-8"));
                    outputter.endTag();
                }
                outputter.endTag();
            }

            outputter.endTag();
            outputter.endTag();
            outputter.endDocument();
        }
        catch (Exception e) {
            log.severe("Exception in creating question XML: " + e.toString());
            e.printStackTrace();
        }
        return sw.toString();
    }

    private String renderClosedQuestion(Question question, ArrayList<String> prompts, String sessionKey) {

        ArrayList<Answer> answers = question.getAnswers();

        StringWriter sw = new StringWriter();
        try {
            XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
            outputter.declaration();
            outputter.startTag("vxml");
            outputter.attribute("version", "2.1");
            outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");

            //remove the termchar operator when # is found in the answer
            for (Answer answer : answers) {
                if (answers.size() > 11 ||
                    (answer.getAnswer_text() != null && answer.getAnswer_text().contains("dtmfKey://"))) {
                    outputter.startTag("property");
                    outputter.attribute("name", "termchar");
                    outputter.attribute("value", "");
                    outputter.endTag();
                    break;
                }
            }
            String noAnswerTimeout = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT);
            //assign a default timeout if one is not specified
            noAnswerTimeout = noAnswerTimeout != null ? noAnswerTimeout : "10s";
            if (!noAnswerTimeout.endsWith("s")) {
                log.warning("No answer timeout must end with 's'. E.g. 10s. Found: " + noAnswerTimeout);
                noAnswerTimeout += "s";
            }
            outputter.startTag("property");
            outputter.attribute("name", "timeout");
            outputter.attribute("value", noAnswerTimeout);
            outputter.endTag();
            outputter.startTag("menu");
            for (String prompt : prompts) {
                outputter.startTag("prompt");
                outputter.startTag("audio");
                outputter.attribute("src", prompt);
                outputter.endTag();
                outputter.endTag();
            }
            for (int cnt = 0; cnt < answers.size(); cnt++) {
                Integer dtmf = cnt + 1;
                String dtmfValue = dtmf.toString();
                if (answers.get(cnt).getAnswer_text() != null &&
                    answers.get(cnt).getAnswer_text().startsWith("dtmfKey://")) {
                    dtmfValue = answers.get(cnt).getAnswer_text().replace("dtmfKey://", "").trim();
                }
                else {
                    if (dtmf == 10) { // 10 translates into 0
                        dtmfValue = "0";
                    }
                    else if (dtmf == 11) {
                        dtmfValue = "*";
                    }
                    else if (dtmf == 12) {
                        dtmfValue = "#";
                    }
                    else if (dtmf > 12) {
                        break;
                    }
                }
                outputter.startTag("choice");
                outputter.attribute("dtmf", dtmfValue);
                outputter.attribute("next",
                                    getAnswerUrl() + "?questionId=" + question.getQuestion_id() + "&answerId=" +
                                                                    answers.get(cnt).getAnswer_id() + "&answerInput=" +
                                                                    URLEncoder.encode(dtmfValue, "UTF-8") +
                                                                    "&sessionKey=" +
                                                                    URLEncoder.encode(sessionKey, "UTF-8"));
                outputter.endTag();
            }
            outputter.startTag("noinput");
            outputter.startTag("goto");
            outputter.attribute("next", TIMEOUT_URL + "?questionId=" + question.getQuestion_id() + "&sessionKey=" +
                                        URLEncoder.encode(sessionKey, "UTF-8"));
            outputter.endTag();
            outputter.endTag();
            outputter.startTag("nomatch");
            outputter.startTag("goto");
            outputter.attribute("next", getAnswerUrl() + "?questionId=" + question.getQuestion_id() +
                                        "&answerId=-1&sessionKey=" + URLEncoder.encode(sessionKey, "UTF-8"));
            outputter.endTag();
            outputter.endTag();
            outputter.endTag();
            outputter.endTag();
            outputter.endDocument();
        }
        catch (Exception e) {
            log.severe("Exception in creating question XML: " + e.toString());
        }
        return sw.toString();
    }

    protected String renderOpenQuestion(Question question, ArrayList<String> prompts, String sessionKey) {

        StringWriter sw = new StringWriter();
        try {
            XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
            outputter.declaration();
            outputter.startTag("vxml");
            outputter.attribute("version", "2.1");
            outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");

            // Check if media property type equals audio
            // if so record audio message, if not record dtmf input
            String typeProperty = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TYPE);
            if (typeProperty != null && typeProperty.equalsIgnoreCase("audio")) {
                renderVoiceMailQuestion(question, prompts, sessionKey, outputter);
            }
            else {
                //see if a dtmf length is defined in the question
                String dtmfMinLength = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                      MediaPropertyKey.ANSWER_INPUT_MIN_LENGTH);
                dtmfMinLength = dtmfMinLength != null ? dtmfMinLength : "";
                String dtmfMaxLength = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                      MediaPropertyKey.ANSWER_INPUT_MAX_LENGTH);
                dtmfMaxLength = dtmfMaxLength != null ? dtmfMaxLength : "";
                String noAnswerTimeout = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT);
                String retryLimit = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.RETRY_LIMIT);
                retryLimit = retryLimit != null ? retryLimit : String.valueOf(Question.DEFAULT_MAX_QUESTION_LOAD);
                //assign a default timeout if one is not specified
                noAnswerTimeout = noAnswerTimeout != null ? noAnswerTimeout : "5s";
                if (!noAnswerTimeout.endsWith("s")) {
                    log.warning("No answer timeout must end with 's'. E.g. 10s. Found: " + noAnswerTimeout);
                    noAnswerTimeout += "s";
                }
                outputter.startTag("var");
                outputter.attribute("name", "answerInput");
                outputter.endTag();
                outputter.startTag("var");
                outputter.attribute("name", "questionId");
                outputter.attribute("expr", "'" + question.getQuestion_id() + "'");
                outputter.endTag();
                outputter.startTag("var");
                outputter.attribute("name", "sessionKey");
                outputter.attribute("expr", "'" + sessionKey + "'");
                outputter.endTag();
                outputter.startTag("form");
                outputter.startTag("property");
                outputter.attribute("name", "timeout");
                outputter.attribute("value", noAnswerTimeout);
                outputter.endTag();
                outputter.startTag("field");
                outputter.attribute("name", "answer");
                outputter.startTag("grammar");
                outputter.attribute("mode", "dtmf");
                outputter.attribute("src", DTMFGRAMMAR + "?minlength=" + dtmfMinLength + "&maxlength=" + dtmfMaxLength);
                outputter.attribute("type", "application/srgs+xml");
                outputter.endTag();
                for (String prompt : prompts) {
                    outputter.startTag("prompt");
                    outputter.startTag("audio");
                    outputter.attribute("src", prompt);
                    outputter.endTag();
                    outputter.endTag();
                }
                outputter.startTag("noinput");
                outputter.startTag("goto");
                if (question.getEventCallback("timeout") != null) {
                    outputter.attribute("next", "timeout?questionId=" + question.getQuestion_id() + "&sessionKey=" +
                                                URLEncoder.encode(sessionKey, "UTF-8"));
                }
                else {
                    Integer retryCount = Question.getRetryCount(sessionKey);
                    if (retryCount < Integer.parseInt(retryLimit)) {
                        outputter.attribute("next", "retry?questionId=" + question.getQuestion_id() + "&sessionKey=" +
                                                    URLEncoder.encode(sessionKey, "UTF-8"));
                        //                                        Question.updateRetryCount( sessionKey );
                    }
                    else {
                        outputter.attribute("next", "retry?questionId=" + question.getQuestion_id());
                        //                                        Question.flushRetryCount( sessionKey );
                    }
                }
                outputter.endTag();
                outputter.endTag();
                outputter.startTag("filled");
                outputter.startTag("assign");
                outputter.attribute("name", "answerInput");
                outputter.attribute("expr", "answer$.utterance.replace(' ','','g')");
                outputter.endTag();
                outputter.startTag("submit");
                outputter.attribute("next", getAnswerUrl());
                outputter.attribute("namelist", "answerInput questionId sessionKey");
                outputter.endTag();
                outputter.startTag("clear");
                outputter.attribute("namelist", "answerInput answer");
                outputter.endTag();
                outputter.endTag();
                outputter.endTag();
                outputter.endTag();
            }
            outputter.endTag();
            outputter.endDocument();
        }
        catch (Exception e) {
            e.printStackTrace();
            log.severe("Exception in creating open question XML: " + e.toString());
        }
        return sw.toString();
    }

    /**
     * renders/updates the xml for recording an audio and posts it to the user
     * on the callback
     * 
     * @param question
     * @param prompts
     * @param sessionKey
     * @param outputter
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    protected void renderVoiceMailQuestion(Question question, ArrayList<String> prompts, String sessionKey,
        XMLOutputter outputter) throws IOException, UnsupportedEncodingException {

        //assign a default voice mail length if one is not specified
        String voiceMessageLengthProperty = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                           MediaPropertyKey.VOICE_MESSAGE_LENGTH);
        voiceMessageLengthProperty = voiceMessageLengthProperty != null ? voiceMessageLengthProperty : "15s";
        if (!voiceMessageLengthProperty.endsWith("s")) {
            log.warning("Voicemail length must be end with 's'. E.g. 40s. Found: " + voiceMessageLengthProperty);
            voiceMessageLengthProperty += "s";
        }

        String dtmfTerm = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.DTMF_TERMINATE);
        dtmfTerm = dtmfTerm != null ? dtmfTerm : "true";
        String voiceMailBeep = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.VOICE_MESSAGE_BEEP);
        voiceMailBeep = voiceMailBeep != null ? voiceMailBeep : "true";

        // Fetch the upload url
        //String host = this.host.replace("rest/", "");
        /*String uuid = UUID.randomUUID().toString();
        String filename = uuid + ".wav";
        String storedAudiofile = host + "download/" + filename;

        MyBlobStore store = new MyBlobStore();
        String uploadURL = store.createUploadUrl(filename, "/dialoghandler/rest/download/audio.vxml");*/

        outputter.startTag("form");
        outputter.attribute("id", "ComposeMessage");
        outputter.startTag("record");
        outputter.attribute("name", "file");
        outputter.attribute("beep", voiceMailBeep);
        outputter.attribute("maxtime", voiceMessageLengthProperty);
        outputter.attribute("dtmfterm", dtmfTerm);
        //outputter.attribute("finalsilence", "3s");
        for (String prompt : prompts) {
            outputter.startTag("prompt");
            outputter.attribute("timeout", "5s");
            outputter.startTag("audio");
            outputter.attribute("src", prompt);
            outputter.endTag();
            outputter.endTag();
        }
        outputter.startTag("noinput");
        for (String prompt : prompts) {
            outputter.startTag("prompt");
            outputter.startTag("audio");
            outputter.attribute("src", prompt);
            outputter.endTag();
            outputter.endTag();
        }
        outputter.endTag(); // noinput
        
        outputter.startTag("catch");
        outputter.attribute("event", "connection.disconnect.hangup");
        outputter.startTag("submit");
        outputter.attribute("next", UPLOAD_URL+"?questionId=" + question.getQuestion_id() + "&sessionKey=" +URLEncoder.encode(sessionKey, "UTF-8"));
        outputter.attribute("namelist", "file");
        outputter.attribute("method", "post");
        outputter.attribute("enctype", "multipart/form-data");
        outputter.endTag(); // submit
        outputter.endTag(); // noinput
        
        outputter.startTag("filled");
        outputter.startTag("submit");
        outputter.attribute("next", UPLOAD_URL+"?questionId=" + question.getQuestion_id() + "&sessionKey=" +URLEncoder.encode(sessionKey, "UTF-8"));
        outputter.attribute("namelist", "file");
        outputter.attribute("method", "post");
        outputter.attribute("enctype", "multipart/form-data");
        outputter.endTag(); // submit
        outputter.endTag(); // filled
        
        outputter.endTag(); // record

        /*outputter.startTag("subdialog");
        outputter.attribute("name", "saveWav");
        outputter.attribute("src", uploadURL);
        outputter.attribute("namelist", "file");
        outputter.attribute("method", "post");
        outputter.attribute("enctype", "multipart/form-data");
        outputter.startTag("filled");
        outputter.startTag("if");
        outputter.attribute("cond", "saveWav.response='SUCCESS'");
        outputter.startTag("goto");
        outputter.attribute("next",
                            getAnswerUrl() + "?questionId=" + question.getQuestion_id() + "&sessionKey=" +
                                                            URLEncoder.encode(sessionKey, "UTF-8") + "&answerInput=" +
                                                            URLEncoder.encode(storedAudiofile, "UTF-8"));
        outputter.endTag();
        outputter.startTag("else");
        outputter.endTag();
        for (String prompt : prompts) {
            outputter.startTag("prompt");
            outputter.startTag("audio");
            outputter.attribute("src", prompt);
            outputter.endTag();
            outputter.endTag();
        }*/
        outputter.endTag(); // form
    }

    protected String renderExitQuestion(List<String> prompts, String sessionKey) {

        StringWriter sw = new StringWriter();
        try {
            XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
            outputter.declaration();
            outputter.startTag("vxml");
            outputter.attribute("version", "2.1");
            outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
            outputter.startTag("form");
            outputter.startTag("block");
            for (String prompt : prompts) {
                outputter.startTag("prompt");
                outputter.startTag("audio");
                outputter.attribute("src", prompt);
                outputter.endTag(); // audio
                outputter.endTag(); // prompt
            }
            outputter.endTag(); // block
            
            outputter.startTag("block");
            outputter.startTag("disconnect");
            outputter.endTag(); // block
            outputter.endTag(); // disconnect
            
            outputter.endTag(); // form
            outputter.endTag(); // vxml
            outputter.endDocument();
        }
        catch (Exception e) {
            log.severe("Exception in creating question XML: " + e.toString());
        }
        return sw.toString();
    }

    private Response handleQuestion(Question question, AdapterConfig adapterConfig, String remoteID, String sessionKey) {

        String result = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form><block><exit/></block></form></vxml>";
        Return res = formQuestion(question, adapterConfig.getConfigId(), remoteID, null, sessionKey);
        if (question != null && !question.getType().equalsIgnoreCase("comment"))
            question = res.question;
        Session session = Session.getSession(sessionKey);
        //if the adapter is a trial adapter, add a introductory node
        if (session != null && "true".equals(session.getAllExtras().get(PLAY_TRIAL_AUDIO_KEY))) {
            res.prompts = res.prompts != null ? res.prompts : new ArrayList<String>();
            String trialAudioURL = getTrialAudioURL(question.getPreferred_language());
            res.prompts.add(0, trialAudioURL);
            session.addExtras(PLAY_TRIAL_AUDIO_KEY, "false");
        }
        log.info("question formed at handleQuestion is: " + ServerUtils.serializeWithoutException(question));
        log.info("prompts formed at handleQuestion is: " + res.prompts);

        if (question != null) {
            question.generateIds();
            session.setQuestion(question);
            session.setRemoteAddress(remoteID);
            session.storeSession();

            //convert all text prompts to speech 
            convertTextsToTTSURLS(question, res, session);

            if (question.getType().equalsIgnoreCase("closed")) {
                result = renderClosedQuestion(question, res.prompts, sessionKey);
            }
            else if (question.getType().equalsIgnoreCase("open")) {
                result = renderOpenQuestion(question, res.prompts, sessionKey);
            }
            else if (question.getType().equalsIgnoreCase("referral")) {
                if (question.getUrl() != null && question.getUrl().size() > 0 && question.getUrl().get(0).startsWith("tel:")) {
                    result = renderReferralQuestion(question, adapterConfig, remoteID, res, session);
                }
            }
            else if (question.getType().equalsIgnoreCase("exit")) {
                result = renderExitQuestion(res.prompts, sessionKey);
            }
            else if (res.prompts.size() > 0) {
                result = renderComment(question, res.prompts, sessionKey);
            }
        }
        else if (res.prompts.size() > 0) {
            result = renderComment(null, res.prompts, sessionKey);
        }
        else {
            log.info("Going to hangup? So clear Session?");
        }
        log.info("Sending xml: " + result);
        return Response.ok(result).build();
    }

    /**
     * Converts all prompts which do not have prefix dtmfKey://. If the prompt
     * ends with a .wav suffix it if added directly. If not it is converted to a
     * TTS url
     * 
     * @param question
     * @param res
     */
    private void convertTextsToTTSURLS(Question question, Return res, Session session) {

        if (res.prompts != null) {
            String ttsSpeedProperty = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TSS_SPEED);
            ttsSpeedProperty = ttsSpeedProperty != null ? ttsSpeedProperty : "0";
            ArrayList<String> promptsCopy = new ArrayList<String>();

            //fetch the ttsInfo from session
            TTSInfo ttsInfo = ServerUtils.getTTSInfoFromSession(question, session);
            for (String prompt : res.prompts) {
                if (!prompt.startsWith("dtmfKey://")) {
                    if (!prompt.endsWith(".wav")) {
                        promptsCopy.add(ServerUtils.getTTSURL(ttsInfo, prompt, session));
                    }
                    else {
                        promptsCopy.add(prompt);
                    }
                }
            }
            res.prompts = promptsCopy;
        }
    }

    /** Render a question requesting to be redirected to another agent or a number. 
     * Does a credit check and drops the session if it fails.
     * @param question
     * @param adapterConfig
     * @param remoteID
     * @param sessionKey
     * @param res
     * @param session
     * @return
     */
    public String renderReferralQuestion(Question question, AdapterConfig adapterConfig, String remoteID, Return res,
        Session session) {

        String result;
        // added for release0.4.2 to store the question in the session,
        //for triggering an answered event
        log.info(String.format("current session key before referral is: %s and remoteId %s",
                               session != null ? session.getKey() : null, remoteID));
        
        String url = question.getUrl().get(0);
        if (!ServerUtils.isValidBearerToken(session, adapterConfig, dialogLog)) {
            
            TTSInfo ttsInfo = ServerUtils.getTTSInfoFromSession(question, session);
            ttsInfo.setProvider(TTSProvider.VOICE_RSS);
            String insufficientCreditMessage = ServerUtils.getInsufficientMessage(ttsInfo.getLanguage());
            String ttsurl = ServerUtils.getTTSURL(ttsInfo, insufficientCreditMessage, session);
            return renderExitQuestion(Arrays.asList(ttsurl), session.getKey());
        }
        if (DDRUtils.validateAddressAndUpdateDDRIfInvalid(url, session)) {
            
            String redirectedId = PhoneNumberUtils.formatNumber(url.replace("tel:", ""), null);
            //update url with formatted redirecteId. RFC3966 returns format tel:<blabla> as expected
            question.setUrl(PhoneNumberUtils.formatNumber(redirectedId, PhoneNumberFormat.RFC3966));
            //store the remoteId as its lost while trying to trigger the answered event
            session.addExtras("referredCalledId", redirectedId);
            session.setQuestion(question);
            session.setRemoteAddress(remoteID);
            //create a new ddr record and session to catch the redirect
            Session referralSession = Session.createSession(adapterConfig, redirectedId);
            log.info( "Referral Session created with interalkey: " + referralSession.getInternalSession() );
            referralSession.setAccountId(session.getAccountId());
            referralSession.addExtras("originalRemoteId", remoteID);
            referralSession.addExtras("redirect", "true");
            referralSession.storeSession();
            if (session.getDirection() != null) {
                DDRRecord ddrRecord = null;
                try {
                    ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig,
                                                                                referralSession.getAccountId(),
                                                                                redirectedId, 1, url,
                                                                                session.getKey());
                    if (ddrRecord != null) {
                        ddrRecord.addAdditionalInfo(Session.TRACKING_TOKEN_KEY, session.getTrackingToken());
                        ddrRecord.createOrUpdate();
                        referralSession.setDdrRecordId(ddrRecord.getId());
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                    log.severe(String.format("Continuing without DDR. Error: %s", e.toString()));
                }
                referralSession.setDirection(session.getDirection());
                referralSession.setTrackingToken(session.getTrackingToken());
            }
            referralSession.setQuestion(session.getQuestion());
            referralSession.addExtras(Session.PARENT_SESSION_KEY, session.getKey());
            referralSession.storeSession();
            session.addChildSessionKey( referralSession.getKey() );
            session.storeSession();
        }
        else {
            log.severe(String.format("Redirect address is invalid: %s. Ignoring.. ",
                                     url.replace("tel:", "")));
        }
        result = renderComment(question, res.prompts, session != null ? session.getKey() : null);
        return result;
    }

    protected String getAnswerUrl() {

        return "answer";
    }

    /**
     * @param startTime
     * @param answerTime
     * @param releaseTime
     * @return
     */
    private HashMap<String, Object> getTimeMap(String startTime, String answerTime, String releaseTime) {

        HashMap<String, Object> timeMap = new HashMap<String, Object>();
        timeMap.put("startTime", startTime);
        timeMap.put("answerTime", answerTime);
        timeMap.put("releaseTime", releaseTime);
        return timeMap;
    }

    /**
     * returns the baseURL for the audio of the trial account
     * 
     * @return
     * @throws Exception
     */
    private String getTrialAudioURL(String language) {

        String agentURL = "http://" + Settings.HOST + "/dialoghandler";
        if (language != null && (language.equals("nl") || language.equals("nl-nl"))) {
            agentURL += "/nl_trial_message.wav";
        }
        else {
            agentURL += "/en_trial_message.wav";
        }
        return agentURL;
    }

    /**
     * Store an incoming audio file and return the download url
     * @param bimp
     * @param accountId
     * @return downloadUrl
     */
    private String storeAudioFile(HttpServletRequest request, String accountId, String adapterId, String ddrId) {
        
        String uuid = UUID.randomUUID().toString();
        List<FileItem> multipartfiledata = new ArrayList<FileItem>();
        
        try {
            DiskFileItemFactory dff = new DiskFileItemFactory();
            dff.setSizeThreshold( 16777216 ); // If the file is bigger then 16 Mb store to disk
            dff.setRepository( new File("./blobstore/") );
            multipartfiledata = new ServletFileUpload(dff).parseRequest(request);
        }
        catch ( FileUploadException e1 ) {
            log.warning("Failed to receive file");
        }
        
        if (multipartfiledata.size() != 1) {
                throw new WebApplicationException(Response.status(Status.BAD_REQUEST).build());
        }
        
        Recording recording = null;
        try{
            FileItem fileData = multipartfiledata.get( 0 );
            recording = Recording.createRecording( new Recording(uuid, accountId, fileData.getContentType(), ddrId, adapterId) );

            //upload to S3
            AWSClient client = ParallelInit.getAWSClient();
            if(!client.uploadFileParts(fileData, recording.getFilename()) ) {
                recording.delete(); // Delete file if upload failed
                return null;
            }
            
        } catch(Exception e){
            System.out.println(e.getMessage());
        }
        
        return "http://"+Settings.HOST+"/account/"+accountId+"/recording/"+recording.getFilename();
    }
    
    /**
     * method checks with broadsoft if the session is already in place. If it is
     * place, it returns at error message.
     * 
     * @param address
     * @param config
     * @param session
     * @return error message is the call is already in place. else returns null
     */
    private static String checkIfCallAlreadyInSession(String address, AdapterConfig config, Session session) {

        log.warning(String.format("Existing session %s. Will check with provider if its actually true",
                                  session.getKey()));
        if (!ServerUtils.isInUnitTestingEnvironment() && config != null) {
            Broadsoft broadsoft = new Broadsoft(config);
            ArrayList<String> activeCallsXML = broadsoft.getActiveCallsInfo();
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                for (String activeCallXML : activeCallsXML) {
                    Document dom;
                    dom = db.parse(new ByteArrayInputStream(activeCallXML.getBytes("UTF-8")));
                    Node addressNode = dom.getElementsByTagName("address").item(0);
                    if (addressNode != null && addressNode.getFirstChild().getNodeValue().contains(address)) {
                        return String.format("Session already active: %s. Cannot initiate new call!!", session.getKey());
                    }
                }
            }
            catch (Exception e) {
                log.severe(String.format("Session: %s found in ASK-Fast, but error occured whle trying to check status at provider end. Message: %s",
                                         session.getKey(), e.getMessage()));
            }
        }
        return null;
    }

    /**
     * check if for this session an
     * 
     * @param eventName
     * @param session
     * @return
     */
    private static boolean isEventTriggered(String eventName, Session session, boolean updateSession) {

        if (session != null) {
            if (session.getAllExtras().get("event_" + eventName) != null) {
                log.warning(String.format("%s event already triggered before for this session: %s at: %s", eventName,
                                          session.getKey(),
                                          TimeUtils.getStringFormatFromDateTime(Long.parseLong(session.getPublicExtras()
                                                                          .get("event_" + eventName)), null)));
                return true;
            }
            else if(updateSession) {
                session.getAllExtras().put("event_" + eventName, String.valueOf(TimeUtils.getServerCurrentTimeInMillis()));
                session.storeSession();
            }
        }
        return false;
    }
}
