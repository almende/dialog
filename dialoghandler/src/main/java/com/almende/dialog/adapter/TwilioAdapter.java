package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.apache.commons.lang.StringUtils;
import com.almende.dialog.LogLevel;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.accounts.Recording;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.QuestionEventRunner;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.TTSInfo;
import com.askfast.commons.entity.TTSInfo.TTSProvider;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.resource.factory.CallFactory;
import com.twilio.sdk.resource.instance.Account;
import com.twilio.sdk.resource.instance.Call;
import com.twilio.sdk.verbs.Conference;
import com.twilio.sdk.verbs.Dial;
import com.twilio.sdk.verbs.Gather;
import com.twilio.sdk.verbs.Hangup;
import com.twilio.sdk.verbs.Number;
import com.twilio.sdk.verbs.Play;
import com.twilio.sdk.verbs.Record;
import com.twilio.sdk.verbs.Redirect;
import com.twilio.sdk.verbs.Say;
import com.twilio.sdk.verbs.TwiMLException;
import com.twilio.sdk.verbs.TwiMLResponse;
import com.twilio.sdk.verbs.Verb;

@Path("twilio")
public class TwilioAdapter {
    
    protected static final Logger log = Logger.getLogger(VoiceXMLRESTProxy.class.getName());
    protected static final com.almende.dialog.Logger dialogLog = new com.almende.dialog.Logger();
    private static final int LOOP_DETECTION = 10;
    protected String TIMEOUT_URL = "timeout";
    protected static final String CONFERENCE_ROOM_NAME_KEY = "CONFERENCE_ROOM_NAME";
	//protected String EXCEPTION_URL="exception";
	
    /**
     * Initiates a call to all the numbers in the addressNameMap and returns a
     * Map of <adress, SessionKey>
     * 
     * @param addressNameMap
     *            Map with address (e.g. phonenumber or email) as Key and name
     *            as value. The name is useful for email and not used for SMS
     *            etc
     * @param dialogIdOrUrl
     *            If a String with leading "http" is found its considered as a
     *            url. Else a Dialog of this id is tried t The URL on which a
     *            GET HTTPRequest is performed and expected a question JSON
     * @param config
     *            the adapterConfig which is used to perform this broadcast
     * @param accountId
     *            AccoundId initiating this broadcast. All costs are applied to
     *            this accountId
     * @param applicationId
     *            This is set in the DialogAgent and should match that with the
     *            applicationId of the twillo account
     * @return A Map of <adress, SessionKey> 
     * @throws Exception
     */
    public static HashMap<String, String> dial(Map<String, String> addressNameMap, String dialogIdOrUrl,
        AdapterConfig config, String accountId, String applicationId, String bearerToken) throws Exception {

        HashMap<String, Session> sessionMap = new HashMap<String, Session>();
        HashMap<String, String> result = new HashMap<String, String>();
        // If it is a broadcast don't provide the remote address because it is deceiving.
        String loadAddress = "";
        if (addressNameMap == null || addressNameMap.isEmpty()) {
            throw new Exception("No address given. Error in call request");
        }
        else if (addressNameMap.size() == 1) {
            loadAddress = addressNameMap.keySet().iterator().next();
            loadAddress = PhoneNumberUtils.formatNumber(loadAddress, null);
        }
        //create a session for the first remote address
        String firstRemoteAddress = loadAddress != null && !loadAddress.trim().isEmpty() ? new String(loadAddress)
            : new String(addressNameMap.keySet().iterator().next());
        firstRemoteAddress = PhoneNumberUtils.formatNumber(firstRemoteAddress, null);
        Session session = Session.getOrCreateSession(config, firstRemoteAddress);
        session.setAccountId(accountId);
        session.killed = false;
        session.setDirection("outbound");
        session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
        session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.BROADSOFT.toString());
        session.setAdapterID(config.getConfigId());
        session.setAccountId(accountId);
        session.addExtras(DialogAgent.BEARER_TOKEN_KEY, bearerToken);
        session.setRemoteAddress(firstRemoteAddress);
        session.storeSession();
        
        String url = Dialog.getDialogURL(dialogIdOrUrl, accountId, session);

        session = session.reload();
        session.setStartUrl(url);
        session.storeSession();
        
        //create a ddr record
        DDRRecord ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(config, accountId, firstRemoteAddress, 1,
                                                                              url, session);
        //session = session.reload();
        //fetch the question
        Question question = Question.fromURL(url, loadAddress, config.getFormattedMyAddress(),
                                             ddrRecord != null ? ddrRecord.getId() : null, session, null);
        if (question != null) {
            for (String address : addressNameMap.keySet()) {
                String formattedAddress = PhoneNumberUtils.formatNumber(address, PhoneNumberFormat.E164);
                if (formattedAddress != null && PhoneNumberUtils.isValidPhoneNumber(formattedAddress)) {

                    //ignore the address for which the session is already created.
                    if (!formattedAddress.equals(session.getRemoteAddress())) {
                        //create a new session for every call request 
                        session = Session.createSession(config, formattedAddress);
                    }
                    
                    session.killed = false;
                    session.setStartUrl(url);
                    session.setAccountId(accountId);
                    session.setDirection("outbound");
                    session.setRemoteAddress(formattedAddress);
                    session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
                    session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.TWILIO.toString());
                    session.setAdapterID(config.getConfigId());
                    session.setQuestion(question);
                    session.setDdrRecordId(ddrRecord != null ? ddrRecord.getId() : null);
                    //update session with account credentials
                    session.addExtras(AdapterConfig.ACCESS_TOKEN_KEY, config.getAccessToken());
                    session.addExtras(AdapterConfig.ACCESS_TOKEN_SECRET_KEY, config.getAccessTokenSecret());
                    //update the startTime of the session
                    session.setStartTimestamp(String.valueOf(TimeUtils.getServerCurrentTimeInMillis()));
                    session.storeSession();
                    
                    if(ddrRecord != null) {
                        ddrRecord.addStatusForAddress(formattedAddress, CommunicationStatus.SENT);
                        ddrRecord.createOrUpdate();
                    }
                    
                    String extSession = "";
                    if (!ServerUtils.isInUnitTestingEnvironment()) {
                        
                        Account mainAccount = getTwilioAccount(config.getAccessToken(), config.getAccessTokenSecret());

                        // Make a call
                        CallFactory callFactory = mainAccount.getCallFactory();
                        Map<String, String> callParams = new HashMap<String, String>();
                        callParams.put("To", formattedAddress); // Replace with a valid phone number
                        callParams.put("From", config.getMyAddress()); // Replace with a valid phone
                        // number in your account
                        callParams.put("ApplicationSid", applicationId);
                        //callParams.put("Url", "http://" + Settings.HOST + "/dialoghandler/rest/twilio/new");
                        callParams.put("StatusCallback", "http://" + Settings.HOST + "/dialoghandler/rest/twilio/cc");
                        callParams.put("StatusCallbackMethod", "GET");
                        callParams.put("IfMachine", "Hangup");
                        callParams.put("Record", "false");

                        Call call = callFactory.create(callParams);
                        log.info(String.format("Call triggered with external id: %s", call.getSid()));
                        extSession = call.getSid();
                        session.setExternalSession(extSession);
                        session.storeSession();
                    }
                    sessionMap.put(formattedAddress, session);
                    result.put(formattedAddress, session.getKey());
                }
                else {
                    result.put(address, String.format(DialogAgent.INVALID_ADDRESS_MESSAGE, address));
                    log.severe(String.format("To address is invalid: %s. Ignoring.. ", address));
                    if(ddrRecord != null) {
                        ddrRecord.addStatusForAddress(address, CommunicationStatus.ERROR);
                        ddrRecord.createOrUpdate();
                    }
                    sessionMap.remove(formattedAddress);
                    session.dropIfRemoteAddressMatches(formattedAddress);
                }
            }
        }
        else {
            log.severe(DialogAgent.getQuestionNotFetchedMessage(dialogIdOrUrl));
            dialogLog.log(LogLevel.SEVERE, session.getAdapterConfig(),
                          DialogAgent.getQuestionNotFetchedMessage(dialogIdOrUrl), session);
            if(ddrRecord != null) {

                ddrRecord.setStatusForAddresses(addressNameMap.keySet(), CommunicationStatus.ERROR);
                ddrRecord.addAdditionalInfo(DDRUtils.DDR_MESSAGE_KEY,
                                            DialogAgent.getQuestionNotFetchedMessage(dialogIdOrUrl));
                ddrRecord.createOrUpdate();
            }
            session.drop();
            throw new Exception(DialogAgent.getQuestionNotFetchedMessage(dialogIdOrUrl));
        }
        if(ddrRecord != null) {
            ddrRecord.setToAddress(addressNameMap);
            ddrRecord.setSessionKeysFromMap(sessionMap);
            ddrRecord.createOrUpdate();
        }
        return result;
    }

    /**
     * Handles incoming new calls.
     * @param CallSid
     * @param AccountSid
     * @param localID
     * @param remoteID
     * @param direction
     * @param forwardedFrom
     * @param callStatus
     * @param isTest
     * @return Twilio response
     */
    @Path("new")
    @GET
    @Produces("application/xml")
    public Response getNewDialog(@QueryParam("CallSid") String CallSid, @QueryParam("AccountSid") String AccountSid,
        @QueryParam("From") String localID, @QueryParam("To") String remoteID, @QueryParam("Direction") String direction, 
        @QueryParam("ForwardedFrom") String forwardedFrom, @QueryParam("CallStatus") String callStatus, @QueryParam("isTest") Boolean isTest) {

        return getNewDialogPost(CallSid, AccountSid, localID, remoteID, direction, forwardedFrom, callStatus, isTest);
    }
	
    /**
     *  Handles incoming new calls.
     * @param CallSid
     * @param AccountSid
     * @param localID
     * @param remoteID
     * @param direction
     * @param forwardedFrom
     * @param callStatus
     * @param isTest
     * @return Twilio response
     */
    @Path("new")
    @POST
    @Produces("application/xml")
    public Response getNewDialogPost(@FormParam("CallSid") String CallSid, @FormParam("AccountSid") String AccountSid,
        @FormParam("From") String localID, @FormParam("To") String remoteID, @FormParam("Direction") String direction,
        @FormParam("ForwardedFrom") String forwardedFrom, @FormParam("CallStatus") String callStatus, @QueryParam("isTest") Boolean isTest) {

        log.info("call started:" + direction + ":" + remoteID + ":" + localID);
        localID = checkAnonymousCallerId(localID);
        Map<String, String> extraParams = new HashMap<String, String>();
        if (forwardedFrom != null) {
            extraParams.put("forwardedFrom", forwardedFrom);
        }

        String url = "";
        Session session = Session.getSessionByExternalKey(CallSid);
        AdapterConfig config = null;
        String formattedRemoteId = null;
        
        DDRRecord ddrRecord = null;
        
        if (direction.equals("inbound")) {
            //swap the remote and the local numbers if its inbound
            String tmpLocalId = new String(localID);
            localID = new String(remoteID);
            remoteID = tmpLocalId;
            
            config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, localID);
            
            formattedRemoteId = PhoneNumberUtils.formatNumber(remoteID, null);
            
            //create a session for incoming only. If the session already exists it is a failover call by twilio.
            if (session == null) {
                session = Session.createSession(config, formattedRemoteId);
                
                session.setAccountId(config.getOwner());
                session.setExternalSession(CallSid);
                if (isTest != null && Boolean.TRUE.equals(isTest)) {
                    session.setAsTestSession();
                }
                session.storeSession();
                url = config.getURLForInboundScenario(session);
                try {
                    ddrRecord = DDRUtils.createDDRRecordOnIncomingCommunication(config, config.getOwner(),
                                                                                formattedRemoteId, url, session);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                
                // when it's a failover call also reuse the ddr record.
                ddrRecord = session.getDDRRecord();
            } 
        }
        else {
            direction = "outbound";
            config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, localID);
            try {
                if (session != null) {
                    url = Dialog.getDialogURL(session.getStartUrl(), session.getAccountId(), session);
                    ddrRecord = session.getDDRRecord();
                }
            }
            catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                dialogLog.log(LogLevel.WARNING, config,
                              String.format("Dialog url encoding failed. Error: %s ", e.toString()), session);
            }
        }
        if (session != null) {
            session.setStartUrl(url);
            session.setDirection(direction);
            session.setRemoteAddress(formattedRemoteId);
            session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
            session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.TWILIO.toString());
            session.setAdapterID(config.getConfigId());
            //fetch the question
            Question question = session.getQuestion();
            if (question == null) {
                question = Question.fromURL(url, formattedRemoteId, config.getFormattedMyAddress(),
                                            ddrRecord != null ? ddrRecord.getId() : null, session, extraParams);
            }

            if (!ServerUtils.isValidBearerToken(session, config, dialogLog)) {

                TTSInfo ttsInfo = ServerUtils.getTTSInfoFromSession(question, session);
                String insufficientCreditMessage = ServerUtils.getInsufficientMessage(ttsInfo.getLanguage());
                return Response.ok(renderExitQuestion(question, Arrays.asList(insufficientCreditMessage),
                                                      session.getKey())).build();
            }
            // Check if we were able to load a question
            if (question == null) {
                //If not load a default error message
                question = Question.getError(config.getPreferred_language());
            }
            session.setCallStatus( callStatus );
            session.setQuestion(question);
            session.setDdrRecordId(ddrRecord != null ? ddrRecord.getId() : null);
            session.storeSession();

            if (session.getQuestion() != null) {
                return handleQuestion(question, config, formattedRemoteId, session, extraParams);
            }
            else {
                return Response.ok().build();
            }
        }
        else {
            log.severe(String.format("CallSid: %s From: %s to: %s direction: %s has no sessions", CallSid, localID,
                                     remoteID, direction));
            return Response.ok("No sessions found.").build();
        }
    }
	
    /**
     * The answer inputs are redirected to this endpoint
     * 
     * @param answer_id
     *            This is generally not associated with a twilio answer
     * @param answer_input
     *            The actual answer given for a previous question
     * @param localID
     *            The from address of this call
     * @param remoteID
     *            The to address of this call
     * @param direction
     *            "inbound" or "outbound-dial"
     * @param recordingUrl
     *            Url for the voice recording if previous question was of type
     *            OPEN_AUDIO
     * @param dialCallStatus
     *            The call status
     * @param callSid
     *            The external id for this call. This can also be the parent
     *            externalId if previous question was a referral
     * @return
     */
    @Path("answer")
    @GET
    @Produces("application/xml")
    public Response answer(@QueryParam("answerId") String answer_id, @QueryParam("Digits") String answer_input,
        @QueryParam("From") String localID, @QueryParam("To") String remoteID,
        @QueryParam("Direction") String direction, @QueryParam("RecordingUrl") String recordingUrl,
        @QueryParam("DialCallStatus") String dialCallStatus, @QueryParam("DialCallSid") String dialCallSid,
        @QueryParam("CallSid") String callSid, @QueryParam("ConferenceSid") String conferenceSid, 
        @QueryParam("CallStatus") String callStatus) {

        TwiMLResponse twiml = new TwiMLResponse();
        localID = checkAnonymousCallerId(localID);

        try {
            answer_input = answer_input != null ? URLDecoder.decode(answer_input, "UTF-8") : answer_input;
        }
        catch (UnsupportedEncodingException e) {
            log.warning(String.format("Answer input decode failed for: %s", answer_input));
        }

        if (direction.equals("inbound")) {
            String tmpLocalId = new String(localID);
            localID = new String(remoteID);
            remoteID = tmpLocalId;
        }

        Session session = Session.getSessionByExternalKey(callSid);
        List<String> callIgnored = Arrays.asList("no-answer", "busy", "canceled", "failed");

        if (session != null) {

            if (recordingUrl != null) {
                answer_input = storeAudioFile(recordingUrl.replace(".wav", "") + ".wav", session.getAccountId(),
                                              session.getDdrRecordId(), session.getAdapterID());
            }
            
            // update call status
            session.setCallStatus( callStatus );

            //add a tag in the session saying its picked up
            session.setCallPickedUpStatus(true);
            if(conferenceSid != null) {
                session.addExtras(Session.EXTERNAL_CONFERENCE_KEY, conferenceSid);
            }
            session.storeSession();
            /*
             * Important note: Remove the referralSession. Twilio gives a
             * "completed" status on a preconnect pickup-accept and a preconnect
             * pickup-reject (for pickup-decline it gives a no-answer status)
             */
            if (dialCallStatus != null) {

                boolean isConnected = false;
                boolean isConnectionMade = false;
                
                List<Session> linkedChildSessions = session.getLinkedChildSession();
                for(Session linkedChildSession : linkedChildSessions) {
                    // Check if one of the child sessions was picked up
                    if (linkedChildSession.isCallPickedUp()) {
                        isConnected = true;
                    }
                    if (linkedChildSession.isCallConnected()) {
                        isConnectionMade = true;
                    }
                    // There is a call dialcallstatus so the child has ended let's finalize it
                    AdapterConfig config = session.getAdapterConfig();
                    if (dialCallSid != null && dialCallSid.equals(linkedChildSession.getExternalSession())) {
                        finalizeCall(config, linkedChildSession, dialCallSid, null);
                    }
                    else {
                        finalizeCall(config, linkedChildSession, null, null);
                    }
                }
                // If the status is completed but the call was picked up we interpret it as no-answer
                if (dialCallStatus.equals("completed") && !isConnected) {
                    dialCallStatus = "no-answer";
                }
                if ("completed".equals(dialCallStatus) && isConnectionMade) {
                    session.setCallConnectedStatus(true);
                    session.storeSession();
                }

                //if the linked child session is found and not pickedup. trigger the next question
                if (callIgnored.contains(dialCallStatus) && session.getQuestion() != null) {
                    
                    Map<String, String> extras = session.getPublicExtras();
                    if(session.getCallStatus() != null) {
                        extras.put( "callStatus", session.getCallStatus() );
                    }

                    session.addExtras("requester", session.getLocalAddress());
                    Question noAnswerQuestion = session.getQuestion().event("timeout", "Call rejected",
                                                                            extras, remoteID,
                                                                            session);
                    return handleQuestion(noAnswerQuestion, session.getAdapterConfig(), remoteID, session,
                                          extras);
                }
            }

            Question question = session.getQuestion();
            log.info(String.format("Question before answer is: %s", ServerUtils.serializeWithoutException(question)));
            
            if (question != null) {
                
                String responder = session.getRemoteAddress();
                if (session.killed) {
                    log.warning("session is killed");
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
                String answerForQuestion = question.getQuestion_expandedtext(session);
                
                question = question.answer(responder, answer_id, answer_input, session);
                log.info(String.format("Question after answer is: %s", ServerUtils.serializeWithoutException(question)));
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
                        e.printStackTrace();
                    }
                }
                //the answered event is triggered if there are no next requests to process and the previous question 
                //was not an exit question (which would also give a null question on question.answer())
                if(question != null && !"exit".equalsIgnoreCase(question.getType())) {
                    session.setCallConnectedStatus(true);
                    answered(direction, remoteID, localID, session.getKey());
                }
                else {
                    session.setCallConnectedStatus(false);
                }
                session.storeSession();
                return handleQuestion(question, session.getAdapterConfig(), responder, session, null);
            }
            else {
                log.warning("No question found in session!");
            }
        }
        else {
            log.warning("No session found for external call id: " + callSid);
            dialogLog.severe(null, "No session found!", session);
        }
        String reply = twiml.toXML();
        return Response.ok(reply).build();
    }
	
    @Path("timeout")
    @GET
    @Produces("application/xml")
    public Response timeout(@QueryParam("From") String localID, @QueryParam("To") String remoteID,
        @QueryParam("Direction") String direction, @QueryParam("CallSid") String callSid, @QueryParam("CallStatus") String callStatus) throws Exception {

        localID = checkAnonymousCallerId(localID);

        //swap local and remote ids if its an incoming call
        if (direction.equals("inbound")) {
            String tmpLocalId = new String(localID);
            localID = new String(remoteID);
            remoteID = tmpLocalId;
        }
        Session session = Session.getSessionByExternalKey(callSid);
        if (session != null) {
            
            // update call status
            session.setCallStatus( callStatus );
            
            Question question = session.getQuestion();
            String responder = session.getRemoteAddress();
            if (session.killed) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            HashMap<String, Object> extras = new HashMap<String, Object>();
            extras.put("sessionKey", session.getKey());
            extras.put("requester", session.getLocalAddress());
            if(session.getCallStatus()!=null) {
                extras.put( "callStatus", session.getCallStatus() );
            }
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
                log.warning("No question found for this session :" + session.getKey());
            }
            session.storeSession();
            return handleQuestion(question, session.getAdapterConfig(), responder, session, null);
        }
        else {
            log.warning("Strange that no session is found for external call id: " + callSid);
        }
        TwiMLResponse twiml = new TwiMLResponse();
        String reply = twiml.toXML();
        return Response.ok(reply).build();
    }

    @Path("preconnect")
    @GET
    @Produces("application/xml")
    public Response preconnect(@QueryParam("From") String localID, @QueryParam("AccountSid") String accountSid,
        @QueryParam("To") String remoteID, @QueryParam("Direction") String direction,
        @QueryParam("CallSid") String callSid, @QueryParam("ParentCallSid") String parentCallSid) {

        long beforePreconnect = TimeUtils.getServerCurrentTimeInMillis();
        
        String reply = (new TwiMLResponse()).toXML();
        Session session = Session.getSessionByExternalKey(callSid);
        //fetch session from parent call
        if (session == null) {
            session = fetchSessionFromParent(localID, remoteID, accountSid, callSid, parentCallSid);
        }

        if (session != null) {
            if (session.getQuestion() != null) {
                Question question = session.getQuestion();
                String responder = session.getRemoteAddress();

                if (session.killed) {
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
                HashMap<String, String> extras = new HashMap<String, String>();
                extras.put("sessionKey", session.getKey());
                extras.put("requester", session.getLocalAddress());
                question = question.event("preconnect", "preconnect event", extras, responder, session);
                // If there is no preconnect the isCallPickedUp is never set
                if (question == null) {
                    session.setCallPickedUpStatus(true);
                    session.storeSession();
                    answered(direction, remoteID, localID, session.getKey());
                }

                //reload the session
                session = Session.getSession(session.getKey());
                session.setQuestion(question);
                session.storeSession();
                long afterPreconnectEvent = TimeUtils.getServerCurrentTimeInMillis();
                log.info(String.format("It took %s for the preconnect event to finish",
                                       (afterPreconnectEvent - beforePreconnect) / 1000.0));
                return handleQuestion(question, session.getAdapterConfig(), responder, session, null);
            }
            else {
                session.setCallPickedUpStatus(true);
                session.storeSession();
                answered(direction, remoteID, localID, session.getKey());
            }
        }
        else {
            log.severe(String.format("No session found for: localID: %s, remoteID: %s, callSid: %s", localID, remoteID,
                                     callSid));
        }
        return Response.ok(reply).build();
    }

    @Path("cc")
    @GET
    public Response receiveCCMessage(@QueryParam("CallSid") String callSid, @QueryParam("From") String localID,
        @QueryParam("To") String remoteID, @QueryParam("Direction") String direction,
        @QueryParam("CallStatus") String status) {

        localID = checkAnonymousCallerId(localID);

        log.info("Received twiliocc status: " + status);

        if (direction.equals("outbound-api")) {
            direction = "outbound";
        }
        else if (direction.equals("inbound")) {
            String tmpLocalId = new String(localID);
            localID = remoteID;
            remoteID = tmpLocalId;
        }
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, localID);
        Session session = Session.getSessionByExternalKey(callSid);
        if (session != null) {
            //update session with call timings
            if (status.equals("completed")) {
                finalizeCall(config, session, callSid, remoteID);
            }
        }
        log.info("Session key: or external sid" + session != null ? session.getKey() : callSid);
        return Response.ok("").build();
    }
    
    public void answered(String direction, String remoteID, String localID, String sessionKey) {

        log.info("call answered with:" + direction + "_" + remoteID + "_" + localID);
        Session session = Session.getSession(sessionKey);
        //for direction = transfer (redirect event), json should not be null        
        //make sure that the answered call is not triggered twice
        if (session != null && session.getQuestion() != null && !isEventTriggered("answered", session)) {
            
            //update the communication status to received status
            DDRRecord ddrRecord = session.getDDRRecord();
            if (ddrRecord != null && !"inbound".equals(session.getDirection())) {
                ddrRecord.addStatusForAddress(session.getRemoteAddress(), CommunicationStatus.RECEIVED);
                ddrRecord.createOrUpdate();
            }
            String responder = session.getRemoteAddress();
            String referredCalledId = session.getAllExtras().get("referredCalledId");
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
        }
    }
	
    /**
     * Retrieve call information and with that: - update ddr record - destroy
     * session - send hangup
     * 
     * @param config
     * @param session
     * @param callSid
     * @param direction
     * @param remoteID
     */
    private void finalizeCall(AdapterConfig config, Session session, String callSid, String remoteID) {

        Call call = null;
        // There are 2 cases where we don't receive a callSid:
        // 1 is when there is a referral to multiple users
        // 2 is when the child session is created but the status is never processed
        if (callSid != null && !ServerUtils.isInUnitTestingEnvironment()) {
            String accountSid = config.getAccessToken();
            String authToken = config.getAccessTokenSecret();
            TwilioRestClient client = new TwilioRestClient(accountSid, authToken);

            call = client.getAccount().getCall(callSid);
        }

        if (session == null && callSid != null) {
            remoteID = call.getTo();
            session = Session.getSessionByExternalKey(callSid);
        } 
        // The remoteID is the one in the session then the session 
        else if (session.getExternalSession()==null && callSid!=null) {
            session.setExternalSession( callSid );
        }

        if (session != null) {
            log.info(String.format("Finalizing call for id: %s, internal id: %s", session.getKey(),
                                   session.getInternalSession()));
            String direction = session.getDirection();
            if(direction==null && call!=null) {
                direction = call.getDirection() != null && call.getDirection().equalsIgnoreCase("outbound-dial") ? "outbound"
                    : "inbound";
            }
            try {
                // Only update the call times if the session belongs to the callSID
                if(call!=null) {
                    //sometimes answerTimeStamp is only given in the ACTIVE ccxml
                    updateSessionWithCallTimes(session, call);
                } else {
                    log.info("Session belongs to the other leg? i: "+session.getLocalAddress()+" e: "+session.getRemoteAddress());
                    session.setReleaseTimestamp( session.getStartTimestamp() );
                }
                session.setDirection(direction);
                if(remoteID!=null) {
                    session.setRemoteAddress(remoteID);
                }
                session.setLocalAddress(config.getMyAddress());
                session.storeSession();
                
                // finalize child sessions if there are any left
                List<Session> childSessions = session.getLinkedChildSession();
                for(Session childSession : childSessions) {
                    finalizeCall( config, childSession, null, null );
                }
                
                //flush the keys if ddrProcessing was successful
                if (DDRUtils.stopDDRCosts(session)) {
                    session.drop();
                }
                hangup(session);

            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            log.warning("Failed to finalize call because no session was found for: " + callSid);
        }
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
                Question question = Question.fromURL(session.getStartUrl(), session.getRemoteAddress(), session);
                session.setQuestion(question);
            }
            if (session.getQuestion() != null && !isEventTriggered("hangup", session)) {
                
                HashMap<String, Object> timeMap = getTimeMap(session.getStartTimestamp(), session.getAnswerTimestamp(),
                                                             session.getReleaseTimestamp());
                timeMap.put("referredCalledId", session.getAllExtras().get("referredCalledId"));
                timeMap.put("sessionKey", session.getKey());
                if(session.getAllExtras() != null && !session.getAllExtras().isEmpty()) {
                    timeMap.putAll(session.getPublicExtras());
                }
                if(session.getCallStatus() != null) {
                    timeMap.put( "callStatus", session.getCallStatus() );
                }
                Response hangupResponse = handleQuestion(null, session.getAdapterConfig(), session.getRemoteAddress(),
                                                         session, null);
                timeMap.put("requester", session.getLocalAddress());
                QuestionEventRunner questionEventRunner = new QuestionEventRunner(session.getQuestion(), "hangup",
                                                                                  "Hangup", session.getRemoteAddress(),
                                                                                  timeMap, session);
                Thread questionEventRunnerThread = new Thread(questionEventRunner);
                questionEventRunnerThread.start();
                return hangupResponse;
            }
            else {
                log.info("no question received");
            }
        }
        return Response.ok("").build();
    }
    
    /**
     * Updates the session with the call times
     * @param session
     * @param call
     * @return
     * @throws ParseException
     */
    public static Session updateSessionWithCallTimes(Session session, Call call) throws ParseException {

        if (call != null && session != null) {
            String pattern = "EEE, dd MMM yyyy HH:mm:ss Z";
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
            String created = call.getProperty("date_created");
            session.setStartTimestamp(format.parse(created).getTime() + "");
            if (call.getEndTime() != null) {
                session.setReleaseTimestamp(format.parse(call.getEndTime()).getTime() + "");
            }
            if (call.getDuration() != null) {
                if(call.getDuration().equals("0")) {
                    session.setAnswerTimestamp(session.getReleaseTimestamp());
                }
                else if(call.getStartTime() != null) {
                    session.setAnswerTimestamp(format.parse(call.getStartTime()).getTime() + "");
                }
            }
            else {
                session.setAnswerTimestamp(null);
                session.setReleaseTimestamp(session.getStartTimestamp());
            }
            if (session.getReleaseTimestamp() == null && session.getAnswerTimestamp() != null) {
                Long releaseTimeByDuration = Long.parseLong(session.getAnswerTimestamp()) +
                                             (Long.parseLong(call.getDuration()) * 1000);
                session.setReleaseTimestamp(releaseTimeByDuration + "");
            }
            return session;
        }
        return null;
    }
    
    /**
     * @param startTime
     * @param answerTime
     * @param releaseTime
     * @return
     */
    private HashMap<String, Object> getTimeMap( String startTime, String answerTime, String releaseTime )
    {
        HashMap<String, Object> timeMap = new HashMap<String, Object>();
        timeMap.put( "startTime", startTime );
        timeMap.put( "answerTime", answerTime );
        timeMap.put( "releaseTime", releaseTime );
        return timeMap;
    }
    
    /**
     * check if for this session an 
     * @param eventName
     * @param session
     * @return
     */
    private static boolean isEventTriggered(String eventName, Session session) {

        if (session != null) {
            if (session.getAllExtras().get("event_" + eventName) != null) {
                String timestamp = TimeUtils.getStringFormatFromDateTime(Long.parseLong(session.getAllExtras()
                                                .get("event_" + eventName)), null);
                log.warning(eventName + "event already triggered before for this session at: " + timestamp);
                return true;
            }
        }
        return false;
    }
	
    public class Return{
        ArrayList<String> prompts;
        Question question;

        public Return( ArrayList<String> prompts, Question question ) {
            this.prompts = prompts;
            this.question = question;
        }
    }
	
    public Return formQuestion(Question question, String adapterID, String address, String ddrRecordId,
        Session session, Map<String, String> extraParams) {

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

            if (qText != null && !qText.equals("")) {
                prompts.add(qText);
            }
            
            if (question.getType().equalsIgnoreCase("closed")) {
                for (Answer ans : question.getAnswers()) {
                    String answer = ans.getAnswer_text();
                    if (answer != null && !answer.equals("") && !answer.startsWith("dtmfKey://")) {
                        prompts.add(answer);
                    }
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
                    String localAddress = null;
                    if (session != null) {
                        localAddress = session.getAdapterConfig() != null ? session.getAdapterConfig()
                                                .getFormattedMyAddress() : session.getLocalAddress();
                    }
                    question = Question.fromURL(question.getUrl().get(0), address, localAddress, ddrRecordId, session,
                                                extraParams);
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
    
    /**
     * Gets the corresponding twilio account linked to the given config
     * 
     * @param config
     * @return
     */
    public static Account getTwilioAccount(String accountSid, String authToken) {

        if (accountSid != null && authToken != null) {
            TwilioRestClient twilio = new TwilioRestClient(accountSid, authToken);
            // Get the main account (The one we used to authenticate the client)
            return twilio.getAccount();
        }
        return null;
    }
	
    protected String renderComment(Question question, ArrayList<String> prompts, String sessionKey) {

        TwiMLResponse twiml = new TwiMLResponse();
        try {
            
            addPrompts(prompts, twiml, ServerUtils.getTTSInfoFromSession(question, sessionKey) );
            if (question != null && question.getAnswers() != null && !question.getAnswers().isEmpty()) {
                Redirect redirect = new Redirect(getAnswerUrl());
                redirect.setMethod("GET");
                twiml.append(redirect);
            }
        }
        catch (TwiMLException e) {
            e.printStackTrace();
        }
        return twiml.toXML();
    }
    
    protected String renderReferral(Question question,ArrayList<String> prompts, String sessionKey, String remoteID){
        TwiMLResponse twiml = new TwiMLResponse();

        try {
            addPrompts(prompts, twiml, ServerUtils.getTTSInfoFromSession(question, sessionKey));
            String redirectTimeoutProperty = question
                .getMediaPropertyValue( MediumType.BROADSOFT,
                                        MediaPropertyKey.TIMEOUT );
            String redirectTimeout = redirectTimeoutProperty != null ? redirectTimeoutProperty.replace( "s", "" ) : "30";
            int timeout = 30;
            try {
                timeout = Integer.parseInt( redirectTimeout );
            }
            catch ( NumberFormatException e ) {
                e.printStackTrace();
            }
            
            Dial dial = new Dial();
            
            List<String> urls = question.getUrl();           
            for(String url : urls) {

                if (DDRUtils.validateAddressAndUpdateDDRIfInvalid(url, sessionKey)) {

                    url = url.replace("tel:", "").trim();
                    Number number = new Number( PhoneNumberUtils.formatNumber(url, null));
                    number.setMethod("GET");
                    number.setUrl(getPreconnectUrl());
                    dial.append(number);
                }
            }
            
            dial.setCallerId( remoteID );
            dial.setTimeout( timeout );

            dial.setMethod( "GET" );
            dial.setAction( getAnswerUrl() );

            twiml.append( dial );
        }
        catch ( TwiMLException e ) {
            log.warning( "Failed to create referal" );
        }
        return twiml.toXML();
    }

    protected String renderClosedQuestion(Question question, ArrayList<String> prompts, String sessionKey) {

        try {
            sessionKey = URLEncoder.encode(sessionKey, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        TwiMLResponse twiml = new TwiMLResponse();
        Gather gather = new Gather();
        gather.setAction(getAnswerUrl());
        gather.setMethod("GET");
        gather.setNumDigits(1);

        String noAnswerTimeout = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT);
        
        boolean useHash = true;
        if(question.getAnswers().size() > 11) {
        	useHash = false;
        }
        else {
            List<Answer> answers = question.getAnswers();
            for (Answer answer : answers) {
                if (answer != null && answer.getAnswer_text() != null &&
                    answer.getAnswer_text().startsWith("dtmfKey://#")) {

                    useHash = true;
                    break;
                }
            }
        }
        
        //assign a default timeout if one is not specified
        noAnswerTimeout = noAnswerTimeout != null ? noAnswerTimeout : "5";
        if (noAnswerTimeout.endsWith("s")) {
            log.warning("No answer timeout must end with 's'. E.g. 10s. Found: " + noAnswerTimeout);
            noAnswerTimeout = noAnswerTimeout.replace("s", "");
        }
        int timeout = 5;
        try {
            timeout = Integer.parseInt(noAnswerTimeout);
        }
        catch (NumberFormatException e) {
            e.printStackTrace();
        }
        gather.setTimeout(timeout);
        if(useHash) {
        	gather.setFinishOnKey("");
        }
        try {
            addPrompts(prompts, gather, ServerUtils.getTTSInfoFromSession(question, sessionKey));
            twiml.append(gather);
            Redirect redirect = new Redirect(getTimeoutUrl());
            redirect.setMethod("GET");
            twiml.append(redirect);
        }
        catch (TwiMLException e) {
            e.printStackTrace();
        }

        return twiml.toXML();
    }

    protected String renderOpenQuestion(Question question, ArrayList<String> prompts, String sessionKey) {

        TwiMLResponse twiml = new TwiMLResponse();

        String typeProperty = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TYPE);
        if (typeProperty != null && typeProperty.equalsIgnoreCase("audio")) {
            renderVoiceMailQuestion(question, prompts, sessionKey, twiml);
        }
        else {

            Gather gather = new Gather();
            gather.setAction(getAnswerUrl());
            gather.setMethod("GET");

            String dtmfMaxLength = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                  MediaPropertyKey.ANSWER_INPUT_MAX_LENGTH);
            if (dtmfMaxLength != null) {
                try {
                    int digits = Integer.parseInt(dtmfMaxLength);
                    gather.setNumDigits(digits);
                }
                catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }

            String noAnswerTimeout = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT);
            //assign a default timeout if one is not specified

            noAnswerTimeout = noAnswerTimeout != null ? noAnswerTimeout : "5";
            if (noAnswerTimeout.endsWith("s")) {
                log.warning("No answer timeout must end with 's'. E.g. 10s. Found: " + noAnswerTimeout);
                noAnswerTimeout = noAnswerTimeout.replace("s", "");
            }
            int timeout = 5;
            try {
                timeout = Integer.parseInt(noAnswerTimeout);
            }
            catch (NumberFormatException e) {
                e.printStackTrace();
            }
            gather.setTimeout(timeout);

            try {
                addPrompts(prompts, gather, ServerUtils.getTTSInfoFromSession(question, sessionKey));
                twiml.append(gather);
                Redirect redirect = new Redirect(getTimeoutUrl());
                redirect.setMethod("GET");
                twiml.append(redirect);
            }
            catch (TwiMLException e) {
                e.printStackTrace();
            }
        }

        return twiml.toXML();
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
        TwiMLResponse twiml) {

        addPrompts(prompts, twiml, ServerUtils.getTTSInfoFromSession(question, sessionKey));

        Record record = new Record();
        record.setAction(getAnswerUrl());
        record.setMethod("GET");

        // Set max voicemail length
        //assign a default voice mail length if one is not specified
        String voiceMessageLengthProperty = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                           MediaPropertyKey.VOICE_MESSAGE_LENGTH);
        voiceMessageLengthProperty = voiceMessageLengthProperty != null ? voiceMessageLengthProperty : "3600";
        int length = 15;
        try {
            length = Integer.parseInt(voiceMessageLengthProperty);
        }
        catch (NumberFormatException e) {
            log.warning("Failed to parse timeout for voicemail e: " + e.getMessage());
        }
        record.setMaxLength(length);

        // Set timeout
        String timeoutProperty = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT);
        timeoutProperty = timeoutProperty != null ? timeoutProperty : "20";
        int timeout = 20;
        try {
            timeout = Integer.parseInt(timeoutProperty);
        }
        catch (NumberFormatException e) {
            log.warning("Failed to parse timeout for voicemail e: " + e.getMessage());
        }

        record.setTimeout(timeout);

        // Set voicemail beep
        String voiceMailBeep = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.VOICE_MESSAGE_BEEP);
        voiceMailBeep = voiceMailBeep != null ? voiceMailBeep : "true";
        boolean beep = Boolean.parseBoolean(voiceMailBeep);
        record.setPlayBeep(beep);

        try {
            twiml.append(record);

            Redirect redirect = new Redirect(getTimeoutUrl());
            redirect.setMethod("GET");
            twiml.append(redirect);
        }
        catch (TwiMLException e) {
            log.warning("Failed to append record");
        }
    }
    
    protected String renderExitQuestion(Question question, List<String> prompts, String sessionKey) {

        TwiMLResponse twiml = new TwiMLResponse();
        addPrompts(prompts, twiml, ServerUtils.getTTSInfoFromSession(question, sessionKey));
        try {
            twiml.append(new Hangup());
        }
        catch (TwiMLException e) {
            log.warning("Failed to append hangup");
        }
        return twiml.toXML();
    }
    
    /**
     * Renders a conference question. Requests twilio to add the current call to
     * the specified conference
     * 
     * @param question
     * @param prompts
     * @param sessionKey
     * @return
     */
    protected String renderConferenceQuestion(Question question, List<String> prompts, String sessionKey) {

        TwiMLResponse twiml = new TwiMLResponse();
        addPrompts(prompts, twiml, ServerUtils.getTTSInfoFromSession(question, sessionKey));
        try {
            String conferenceName = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                   MediaPropertyKey.CONFERENCE_ROOM_NAME);
            if (conferenceName != null) {
                //create a dial verb
                Dial dial = new Dial();
                dial.setMethod("GET");
                dial.setAction(getAnswerUrl());
                String conferenceStart = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                        MediaPropertyKey.CONFERENCE_START_ON_CONNECT);
                String conferenceEnd = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                      MediaPropertyKey.CONFERENCE_END_ON_DISCONNECT);
                String conferenceWaitURL = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                          MediaPropertyKey.CONFERENCE_WAIT_URL);
                String terminateOnStar = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                          MediaPropertyKey.DTMF_TERMINATE);
                Session session = Session.getSession(sessionKey);
                if (session != null) {
                    Conference conference = new Conference(conferenceName + "_" + session.getAccountId());
                    if (conferenceStart != null) {
                        conference.setStartConferenceOnEnter(Boolean.parseBoolean(conferenceStart));
                    }
                    if (conferenceEnd != null) {
                        conference.setEndConferenceOnExit(Boolean.parseBoolean(conferenceEnd));
                    }
                    if (conferenceWaitURL != null) {

                        if (conferenceWaitURL.startsWith("http")) {
                            conference.setWaitUrl(formatPrompt(conferenceWaitURL));
                        }
                        else {
                            String ttsWaitUrl = ServerUtils.getTTSURL(conferenceWaitURL, question, session);
                            conference.setWaitUrl(formatPrompt(ttsWaitUrl));
                        }
                    }
                    if(terminateOnStar != null) {
                        dial.setHangupOnStar(Boolean.parseBoolean(terminateOnStar));
                    }
                    session.addExtras(MediaPropertyKey.CONFERENCE_START_ON_CONNECT.toString(),
                                      String.valueOf(Boolean.parseBoolean(conferenceStart)));
                    session.addExtras(MediaPropertyKey.CONFERENCE_END_ON_DISCONNECT.toString(),
                                      String.valueOf(Boolean.parseBoolean(conferenceEnd)));
                    session.storeSession();
                    dial.append(conference);
                    twiml.append(dial);
                }
            }
        }
        catch (TwiMLException e) {
            log.warning("Failed to append conference?");
        }
        return twiml.toXML();
    }
    
    protected void addPrompts(List<String> prompts, Verb twiml, TTSInfo ttsInfo) {

        try {
            for (String prompt : prompts) {

                Verb verbToAppend = null;
                if (prompt.startsWith("http")) {
                    // Replace all the & with &amp; because of xml validity
                    verbToAppend = new Play(formatPrompt(prompt));
                }
                else {
                    if (ttsInfo != null) {

                        Say say = new Say(formatPrompt(prompt));
                        say.setLanguage(ttsInfo.getLanguage().getCode());
                        verbToAppend = say;
                        if (ttsInfo != null) {
                            say.setLanguage(ttsInfo.getLanguage().getCode());
                        }
                    }
                }
                twiml.append(verbToAppend);
            }
        }
        catch (TwiMLException e) {
            log.warning("failed to added prompts: " + e.getMessage());
        }
    }
    
    /**
     * Returns the formatted prompt as needed by Twilio
     * @param prompt
     * @return
     */
    private String formatPrompt(String prompt) {

        if (prompt.startsWith("http")) {
            // Replace all the & with &amp; because of xml validity
            prompt = prompt.replace("&", "&amp;");
        }
        else {
            try {
                prompt = prompt.replace("text://", "");
                prompt = URLDecoder.decode(prompt, "UTF-8");
            }
            catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return prompt;
    }
    
    private Response handleQuestion(Question question, AdapterConfig adapterConfig, String remoteID, Session session,
        Map<String, String> extraParams) {

        String result = (new TwiMLResponse()).toXML();
        Return res = formQuestion(question, adapterConfig.getConfigId(), remoteID, null, session, extraParams);
        if (question != null && !question.getType().equalsIgnoreCase("comment"))
            question = res.question;
        // if the adapter is a trial adapter, add a introductory node
        log.info("question formed at handleQuestion is: " + ServerUtils.serializeWithoutException(question));
        log.info("prompts formed at handleQuestion is: " + res.prompts);

        String sessionKey = session != null ? session.getKey() : null;
        
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
                if (question.getUrl() != null && question.getUrl().size()>0 ) {
                    
                    String newRemoteID = remoteID;
                    String externalCallerId = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                             MediaPropertyKey.USE_EXTERNAL_CALLERID);
                    Boolean callerId = false;
                    if (externalCallerId != null) {
                        callerId = Boolean.parseBoolean(externalCallerId);
                    }
                    if (!callerId) {
                        newRemoteID = adapterConfig.getMyAddress();
                    }
                    
                    for (int i = 0; i < question.getUrl().size(); i++) {
                        String url = question.getUrl().get(i);
                        //for(String url : question.getUrl()) {
                        if (url.startsWith("tel:")) {
                            // added for release0.4.2 to store the question in the
                            // session,
                            // for triggering an answered event
                            // Check with remoteID we are going to use for the call

                            log.info(String.format("current session key before referral is: %s and remoteId %s",
                                                   sessionKey, remoteID));
                            if (DDRUtils.validateAddressAndUpdateDDRIfInvalid(url, sessionKey)) {

                                String redirectedId = PhoneNumberUtils.formatNumber(url.replace("tel:", "").trim(),
                                                                                    null);
                                if (redirectedId != null) {

                                    //check credits
                                    if (!ServerUtils.isValidBearerToken(session, adapterConfig, dialogLog)) {

                                        TTSInfo ttsInfo = ServerUtils.getTTSInfoFromSession(question, session);
                                        String insufficientCreditMessage = ServerUtils.getInsufficientMessage(ttsInfo.getLanguage());
                                        return Response.ok(renderExitQuestion(null,
                                                                              Arrays.asList(insufficientCreditMessage),
                                                                              sessionKey)).build();
                                    }
                                    question.getUrl().set(i, "tel:" + redirectedId);
                                    updateSessionOnRedirect(question, adapterConfig, remoteID, session, newRemoteID,
                                                            redirectedId);
                                }
                            }
                            else {
                                log.severe(String.format("Redirect address is invalid: %s. Ignoring.. ",
                                                         url.replace("tel:", "")));
                            }
                        }
                    }
                    result = renderReferral(question, res.prompts, sessionKey, newRemoteID);
                }
            }
            else if (question.getType().equalsIgnoreCase("exit")) {
                result = renderExitQuestion(question, res.prompts, sessionKey);
            }
            else if (question.getType().equalsIgnoreCase("conference")) {
                result = renderConferenceQuestion(question, res.prompts, sessionKey);
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
        return Response.status(Status.OK).type(MediaType.APPLICATION_XML).entity(result).build();
    }

    /** Creates a new {@link Session} and {@link DDRRecord} for the redirection.
     * @param question
     * @param adapterConfig
     * @param originalRemoteID The remoteId before the referral has taken place
     * @param session The previous sesison in contention that existed between the originalRemoteId and the referralFromId
     * @param referralFromID The origin Id of the referral
     * @param referralToId The destination id of the referral
     */
    private void updateSessionOnRedirect(Question question, AdapterConfig adapterConfig, String originalRemoteID,
        Session session, String referralFromID, String referralToId) {

        // update url with formatted redirecteId. RFC3966
        // returns format tel:<blabla> as expected
        //question.setUrl(referralToId);
        // store the remoteId as its lost while trying to
        // trigger the answered event
        session.addExtras("referredCalledId", referralToId);
        session.setQuestion(question);

        // create a new ddr record and session to catch the
        // redirect
        Session referralSession = Session.createSession(adapterConfig, referralFromID, referralToId);
        referralSession.addExtras("originalRemoteId", originalRemoteID);
        referralSession.addExtras("redirect", "true");
        referralSession.setAccountId(session.getAccountId());
        referralSession.setQuestion(session.getQuestion());
        referralSession.addExtras(Session.PARENT_SESSION_KEY, session.getKey());
        //update the startTime of the session
        referralSession.setStartTimestamp(String.valueOf(TimeUtils.getServerCurrentTimeInMillis()));
        referralSession.storeSession();
        
        if (session.getDirection() != null) {
            
            DDRRecord ddrRecord = null;
            String urls = StringUtils.join( question.getUrl(), "," );
            try {
                ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig,
                                                                            referralSession.getAccountId(),
                                                                            referralToId, question.getUrl().size(),
                                                                            urls, referralSession);
                referralSession = referralSession.reload();
                if (ddrRecord != null) {
                    referralSession.setDdrRecordId(ddrRecord.getId());
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("Continuing without DDR. Error: %s", e.toString()));
            }
            referralSession.setDirection("transfer");
        }
        referralSession.storeSession();
        session.addChildSessionKey( referralSession.getKey() );
        session.storeSession();
    }
    
    protected String getAnswerUrl() {
        return "http://"+Settings.HOST+"/dialoghandler/rest/twilio/answer";
    }
    
    protected String getTimeoutUrl() {
    	return "http://"+Settings.HOST+"/dialoghandler/rest/twilio/timeout";
    }
    
    protected String getPreconnectUrl() {
    	return "http://"+Settings.HOST+"/dialoghandler/rest/twilio/preconnect";
    }
    
    private String checkAnonymousCallerId(String callerId) {

        if (callerId.contains("+266696687")) {
            return "";
        }
        return callerId;
    }
    
    /**
     * Store an incoming audio file and return the download url
     * @param bimp
     * @param accountId
     * @return downloadUrl
     */
    private String storeAudioFile(String url, String accountId, String ddrId, String adapterId) {
        
        String uuid = UUID.randomUUID().toString();
        Recording recording = Recording.createRecording( new Recording(uuid, accountId, url, "audio/wav", ddrId, adapterId) );
        
        return "http://"+Settings.HOST+"/account/"+accountId+"/recording/"+recording.getId()+".wav";
    }
    
    /**
     * Fetch the linked session using a parentCall sid stored within that of a
     * childCallSid
     * 
     * @param localID
     * @param accountSid
     * @param callSid
     * @return
     */
    public Session fetchSessionFromParent(String localID, String remoteID, String accountSid, String callSid,
        String parentCallSid) {

        try {
            if (parentCallSid == null) {

                AdapterConfig adapterConfig = AdapterConfig.findAdapterConfig(AdapterType.CALL.toString(), localID,
                                                                              null);
                //fetch the parent session for this preconnect
                Account twilioAccount = getTwilioAccount(accountSid != null ? accountSid
                                                             : adapterConfig.getAccessToken(),
                                                         adapterConfig.getAccessTokenSecret());
                if (twilioAccount != null) {
                    Call call = twilioAccount.getCall(callSid);
                    parentCallSid = call.getParentCallSid();
                }
            }
            if (parentCallSid != null) {
                return Session.getSessionFromParentExternalId(callSid, parentCallSid, remoteID);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            log.severe(String.format("Session fetch failed from parent callsid. localId: %s, accountSid: %s and callSid: %s",
                                     localID, accountSid, callSid));
        }
        return null;
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
                    //do not format the url to tts if voicerss is given
                    if (!prompt.endsWith(".wav") && ttsInfo.getProvider() != null &&
                        !TTSProvider.VOICE_RSS.equals(ttsInfo.getProvider())) {

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
}
