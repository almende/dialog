package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
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
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.DDRRecord.CommunicationStatus;
import com.askfast.commons.entity.TTSInfo;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;
import com.askfast.strowger.sdk.StrowgerRestClient;
import com.askfast.strowger.sdk.actions.Dtmf;
import com.askfast.strowger.sdk.actions.Hangup;
import com.askfast.strowger.sdk.actions.Include;
import com.askfast.strowger.sdk.actions.Play;
import com.askfast.strowger.sdk.actions.Record;
import com.askfast.strowger.sdk.actions.StrowgerAction;
import com.askfast.strowger.sdk.model.Call;
import com.askfast.strowger.sdk.model.ControlResult;
import com.askfast.strowger.sdk.model.Dial;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;

@Path("strowger")
public class TPAdapter {

    protected static final Logger log = Logger.getLogger(TPAdapter.class.getName());
    private static final int LOOP_DETECTION = 10;
    protected String TIMEOUT_URL = "timeout";
    public static final String INBOUND = "incoming";
    public static final String OUTBOUND = "outgoing";
    
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
        AdapterConfig config, String accountId, String senderName, String bearerToken) throws Exception {

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
        session.setDirection(OUTBOUND);
        session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
        session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.TP.toString());
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
                    session.setDirection(OUTBOUND);
                    session.setRemoteAddress(formattedAddress);
                    session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
                    session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.TP.toString());
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
                        
                        StrowgerRestClient client = new StrowgerRestClient( config.getAccessToken(), config.getAccessTokenSecret() );

                        // Make a call
                        
                        Dial dial = new Dial(config.getMyAddress(), senderName, formattedAddress);
                        Call call = client.initiateCall( config.getMyAddress(), dial );
                        
                        if(call==null) {
                            String errorMessage = String.format("Call not started between %s and %s. Error in requesting adapter provider",
                                                                config.getMyAddress(), formattedAddress);
                            log.severe(errorMessage);
                            session.drop();
                            result.put(formattedAddress, errorMessage);
                            continue;
                        }
                        extSession = call.getCallId();
                        log.info(String.format("Call triggered with external id: %s", extSession));
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
    @Produces("application/json")
    public Response getNewDialogPost(@QueryParam("isTest") Boolean isTest, String json) {
        
        ControlResult res = ControlResult.fromJson( json );
        Call call = res.getCall();
        String callId = call.getCallId();
        String remoteID = call.getCalled();
        String localID = call.getCaller();
        String direction = call.getCallType();

        log.info("call started:" + call.getCallType() + ":" + remoteID + ":" + localID);
        Map<String, String> extraParams = new HashMap<String, String>();

        String url = "";
        Session session = Session.getSessionByExternalKey(callId);
        AdapterConfig config = null;
        String formattedRemoteId = null;
        
        DDRRecord ddrRecord = null;
        
        String reply = new StrowgerAction().toJson();
        
        if (direction.equals(INBOUND)) {
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
                session.setExternalSession(callId);
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
            direction = OUTBOUND;
            config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, localID);
            try {
                if (session != null) {
                    url = Dialog.getDialogURL(session.getStartUrl(), session.getAccountId(), session);
                    ddrRecord = session.getDDRRecord();
                }
            }
            catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        if (session != null) {
            session.setStartUrl(url);
            session.setDirection(direction);
            session.setRemoteAddress(formattedRemoteId);
            session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
            session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.TP.toString());
            session.setAdapterID(config.getConfigId());
            //fetch the question
            Question question = session.getQuestion();
            if (question == null) {
                question = Question.fromURL(url, formattedRemoteId, config.getFormattedMyAddress(),
                                            ddrRecord != null ? ddrRecord.getId() : null, session, extraParams);
            }

            if (!ServerUtils.isValidBearerToken(session, config)) {

                TTSInfo ttsInfo = ServerUtils.getTTSInfoFromSession(question, session);
                String insufficientCreditMessage = ServerUtils.getInsufficientMessage(ttsInfo.getLanguage());
                return Response.ok(renderExitQuestion(question, Arrays.asList(insufficientCreditMessage), session)).build();
            }
            // Check if we were able to load a question
            if (question == null) {
                //If not load a default error message
                question = Question.getError(config.getPreferred_language());
            }
            session.setQuestion(question);
            session.setDdrRecordId(ddrRecord != null ? ddrRecord.getId() : null);
            session.storeSession();

            if (session.getQuestion() != null) {
                return handleQuestion(question, config, formattedRemoteId, session, extraParams);
            }
            else {
                return Response.ok(reply).build();
            }
        }
        else {
            log.severe(String.format("CallSid: %s From: %s to: %s direction: %s has no sessions", callId, localID,
                                     remoteID, direction));
            return Response.ok(reply).build();
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
    @POST
    @Produces("application/json")
    public Response answer(String json) {

        ControlResult res = ControlResult.fromJson( json );
        Call call = res.getCall();
        String callId = call.getCallId();
        String localID = call.getCaller();
        String remoteID = call.getCalled();
        String direction = call.getCallType();
        String answer_input = res.getDtmf();
        String recordingUrl = res.getRecordingUrl();
        
        StrowgerAction strowger = new StrowgerAction();

        try {
            answer_input = answer_input != null ? URLDecoder.decode(answer_input, "UTF-8") : answer_input;
        }
        catch (UnsupportedEncodingException e) {
            log.warning(String.format("Answer input decode failed for: %s", answer_input));
        }

        if (direction.equals(INBOUND)) {
            String tmpLocalId = new String(localID);
            localID = new String(remoteID);
            remoteID = tmpLocalId;
        }

        Session session = Session.getSessionByExternalKey(callId);

        if (session != null) {

            if (recordingUrl != null) {
                answer_input = storeAudioFile(recordingUrl.replace(".wav", "") + ".wav", session.getAccountId(),
                                              session.getDdrRecordId(), session.getAdapterID());
            }
            
            //TODO: update call status

            //add a tag in the session saying its picked up
            session.setCallPickedUpStatus(true);
            session.storeSession();

            Question question = session.getQuestion();
            log.info(String.format("Question before answer is: %s", ServerUtils.serializeWithoutException(question)));
            
            if (question != null) {
                
                String responder = session.getRemoteAddress();
                if (session.killed) {
                    log.warning("session is killed");
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
                String answerForQuestion = question.getQuestion_expandedtext(session);
                
                question = question.answer(responder, null, answer_input, session);
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
            log.warning("No session found for external call id: " + callId);
        }
        String reply = strowger.toJson();
        return Response.ok(reply).build();
    }
        
    @Path("timeout")
    @POST
    @Produces("application/json")
    public Response timeout(String json) throws Exception {

        ControlResult res = ControlResult.fromJson( json );
        Call call = res.getCall();
        String callId = call.getCallId();
        String localID = call.getCaller();
        String remoteID = call.getCalled();
        String direction = call.getCallType();
        
        //swap local and remote ids if its an incoming call
        if (direction.equals(INBOUND)) {
            String tmpLocalId = new String(localID);
            localID = new String(remoteID);
            remoteID = tmpLocalId;
        }
        Session session = Session.getSessionByExternalKey(callId);
        if (session != null) {
            
            //TODO: update call status
            
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
            log.warning("Strange that no session is found for external call id: " + callId);
        }
        StrowgerAction strowger = new StrowgerAction();
        String reply = strowger.toJson();
        return Response.ok(reply).build();
    }

    @Path("cc")
    @POST
    public Response receiveCCMessage(String json) {

        ControlResult res = ControlResult.fromJson( json );
        Call call = res.getCall();
        String callId = call.getCallId();
        String localID = call.getCaller();
        String remoteID = call.getCalled();
        String direction = call.getCallType();
        Date termanationTime = call.getTerminationTime();

        if (direction.equals("outbound-api")) {
            direction = OUTBOUND;
        }
        else if (direction.equals(INBOUND)) {
            String tmpLocalId = new String(localID);
            localID = remoteID;
            remoteID = tmpLocalId;
        }
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, localID);
        Session session = Session.getSessionByExternalKey(callId);
        if (session != null) {
            //update session with call timings
            if (termanationTime != null) {
                finalizeCall(config, session, callId, remoteID, call);
            }
        }
        log.info("Session key: or external sid" + session != null ? session.getKey() : callId);
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
            if (ddrRecord != null && !INBOUND.equals(session.getDirection())) {
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
    private void finalizeCall(AdapterConfig config, Session session, String callSid, String remoteID, Call call) {

        // TODO: Implement
        if (session == null && callSid != null) {
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
                    finalizeCall( config, childSession, null, null, null );
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
     * Updates the session with the call times
     * @param session
     * @param call
     * @return
     * @throws ParseException
     */
    public static Session updateSessionWithCallTimes(Session session, Call call) throws ParseException {

        if (call != null && session != null) {
            
            Date dialTime = call.getDialTime();
            Date connectTime = call.getConnectTime();
            Date terminationTime = call.getTerminationTime();
            if(dialTime != null) {
                session.setStartTimestamp(dialTime.getTime() + "");
            }
            if (terminationTime != null) {
                session.setReleaseTimestamp(terminationTime.getTime() + "");
            }
            if (connectTime != null) {
                session.setAnswerTimestamp(connectTime.getTime() + "");
            }
            return session;
        }
        return null;
    }
    
    private Response handleQuestion(Question question, AdapterConfig adapterConfig, String remoteID, Session session,
                                    Map<String, String> extraParams) {

        String result = new StrowgerAction().toJson();
        Return res = formQuestion(question, adapterConfig.getConfigId(), remoteID, null, session, extraParams);
        if (question != null && !question.getType().equalsIgnoreCase("comment"))
            question = res.question;
        // if the adapter is a trial adapter, add a introductory node
        log.info("question formed at handleQuestion is: " + ServerUtils.serializeWithoutException(question));
        log.info("prompts formed at handleQuestion is: " + res.prompts);
        
        if (question != null) {
            question.generateIds();
            session.setQuestion(question);
            session.setRemoteAddress(remoteID);
            session.storeSession();
            
            if (question.getType().equalsIgnoreCase("closed")) {
                result = renderClosedQuestion(question, res.prompts, session);
            }
            else if (question.getType().equalsIgnoreCase("open")) {
                result = renderOpenQuestion(question, res.prompts, session);
            }
            else if (question.getType().equalsIgnoreCase("referral")) {
                // TODO: Implement handling of referral questions
            }
            else if (question.getType().equalsIgnoreCase("exit")) {
                result = renderExitQuestion(question, res.prompts, session);
            }
            else if (question.getType().equalsIgnoreCase("conference")) {
             // TODO: Implement handling of conference questions
            }
            else if (res.prompts.size() > 0) {
                result = renderComment(question, res.prompts, session);
            }
        }
        else if (res.prompts.size() > 0) {
            result = renderComment(null, res.prompts, session);
        }
        else {
            log.info("Going to hangup? So clear Session?");
        }
        log.info("Sending json: " + result);
        return Response.status(Status.OK).entity(result).build();
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
        for ( int count = 0; count <= LOOP_DETECTION; count++ ) {
            if ( question == null )
                break;
            log.info( "Going to form question of type: " + question.getType() );
            if ( question.getType() == null ) {
                question = null;
                break;
            }
            String preferred_language = question.getPreferred_language();
            question.setPreferred_language( preferred_language );
            String qText = question.getQuestion_text();

            if ( qText != null && !qText.equals( "" ) ) {
                prompts.add( qText );
            }

            if ( question.getType().equalsIgnoreCase( "closed" ) ) {
                for ( Answer ans : question.getAnswers() ) {
                    String answer = ans.getAnswer_text();
                    if ( answer != null && !answer.equals( "" ) && !answer.startsWith( "dtmfKey://" ) ) {
                        prompts.add( answer );
                    }
                }
                break; //Jump from forloop
            }
            else if ( question.getType().equalsIgnoreCase( "comment" ) ) {
                // If it is a comment directly read the next question, because we can append the prompts.
                //question = question.answer( null, adapterID, null, null, sessionKey );
                break;
            }
            else if ( question.getType().equalsIgnoreCase( "referral" ) ) {
                if ( question.getUrl() != null && question.getUrl().size() == 1 && !question.getUrl().get( 0 ).startsWith( "tel:" ) ) {
                    String localAddress = null;
                    if ( session != null ) {
                        localAddress = session.getAdapterConfig() != null ? session.getAdapterConfig().getFormattedMyAddress() : session.getLocalAddress();
                    }
                    question = Question.fromURL( question.getUrl().get( 0 ), address, localAddress, ddrRecordId, session, extraParams );
                    //question = question.answer(null, null, null);
                    //                                  break;
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
        return new Return( prompts, question );
    }
    
    protected String renderComment(Question question, ArrayList<String> prompts, Session session) {

        Play play = new Play();
        addPrompts(prompts, play, question, session );
        StrowgerAction strowger = new StrowgerAction();
        strowger.addAction(play);
        if (question != null && question.getAnswers() != null && !question.getAnswers().isEmpty()) {
            Include include = new Include( URI.create( getAnswerUrl() ) );
            
            strowger.addAction(include);
        }
        return strowger.toJson();
    }
    
    protected String renderClosedQuestion(Question question, ArrayList<String> prompts, Session session) {

        StrowgerAction strowger = new StrowgerAction();
        Dtmf dtmf = new Dtmf();
        dtmf.setUrl(URI.create(getAnswerUrl()));
        dtmf.setMaxDigits(1);

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
        dtmf.setTimeout(timeout);
        if(useHash) {
            dtmf.setFinishOnKey("");
        }
        
        Play play = new Play();
        addPrompts(prompts, play, question, session);
        dtmf.setPlay(play);
        strowger.addAction(dtmf);
        
        strowger.addAction(new Include(URI.create(getTimeoutUrl())));
        

        return strowger.toJson();
    }
    
    protected String renderOpenQuestion(Question question, ArrayList<String> prompts, Session session) {

        StrowgerAction strowger = new StrowgerAction();
        

        String typeProperty = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TYPE);
        if (typeProperty != null && typeProperty.equalsIgnoreCase("audio")) {
            renderVoiceMailQuestion(question, prompts, session, strowger);
        }
        else {

            Dtmf dtmf = new Dtmf();
            dtmf.setUrl(URI.create(getAnswerUrl()));
            

            String dtmfMaxLength = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                  MediaPropertyKey.ANSWER_INPUT_MAX_LENGTH);
            if (dtmfMaxLength != null) {
                try {
                    int digits = Integer.parseInt(dtmfMaxLength);
                    dtmf.setMaxDigits(digits);
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
            dtmf.setTimeout(timeout);

            
            Play play = new Play();
            addPrompts(prompts, play, question, session);
            dtmf.setPlay(play);
            strowger.addAction(dtmf);
            
            strowger.addAction(new Include(URI.create(getTimeoutUrl())));
        }

        return strowger.toJson();
    }
    
    /**
     * renders/updates the json for recording an audio and posts it to the user
     * on the callback
     * 
     * @param question
     * @param prompts
     * @param sessionKey
     * @param outputter
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    protected void renderVoiceMailQuestion(Question question, ArrayList<String> prompts, Session session, StrowgerAction strowger) {

        Play play = new Play();
        addPrompts(prompts, play, question, session);
        strowger.addAction(play);

        Record record = new Record(URI.create(getAnswerUrl()));

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

        record.setMaxLength(timeout);

        // Set voicemail beep
        String voiceMailBeep = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.VOICE_MESSAGE_BEEP);
        voiceMailBeep = voiceMailBeep != null ? voiceMailBeep : "true";
        boolean beep = Boolean.parseBoolean(voiceMailBeep);
        record.setPlaySignal(beep);

        strowger.addAction(record);
        
        strowger.addAction(new Include(URI.create(getTimeoutUrl())));
    }
    
    protected String renderExitQuestion(Question question, List<String> prompts, Session session) {
        
        Play play = new Play();
        addPrompts(prompts, play, question, session);
        StrowgerAction strowger = new StrowgerAction();
        strowger.addAction(play);
        
        strowger.addAction( new Hangup());
        
        return strowger.toJson();
    }
    
    protected void addPrompts(List<String> prompts, Play play, Question question, Session session) {

        for (String prompt : prompts) {

            if (prompt.startsWith("http") || prompt.startsWith("https")) {
                play.addLocation(URI.create(prompt));
            }
            else {
                String url = ServerUtils.getTTSURL(formatPrompt(prompt), question, session);
                play.addLocation(URI.create(url));
            }
        }
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
        
        // Since the audio files will removed in an hour, they will need to be downloaded!
        // TODO: Download audio file to s3
        
        return "http://"+Settings.HOST+"/account/"+accountId+"/recording/"+recording.getId()+".wav";
    }
    
    /**
     * Returns the formatted prompt as needed by Strowger
     * @param prompt
     * @return
     */
    private String formatPrompt(String prompt) {

        try {
            prompt = prompt.replace("text://", "");
            prompt = URLDecoder.decode(prompt, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return prompt;
    }
    
    public String getAnswerUrl() {
        return "http://"+Settings.HOST+"/dialoghandler/rest/strowger/answer";
    }
    
    protected String getTimeoutUrl() {
        return "http://"+Settings.HOST+"/dialoghandler/rest/strowger/timeout";
    }
}
