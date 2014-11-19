package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.almende.dialog.LogLevel;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.resource.factory.CallFactory;
import com.twilio.sdk.resource.instance.Account;
import com.twilio.sdk.resource.instance.Call;
import com.twilio.sdk.verbs.Dial;
import com.twilio.sdk.verbs.Gather;
import com.twilio.sdk.verbs.Hangup;
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
	protected static final com.almende.dialog.Logger dialogLog =  new com.almende.dialog.Logger();
	private static final int LOOP_DETECTION=10;
	protected String TIMEOUT_URL="timeout";
	//protected String EXCEPTION_URL="exception";
	
	public static HashMap<String, String> dial(Map<String, String> addressNameMap, String url, String senderName,
	        AdapterConfig config, String applicationId) throws Exception {
		
		HashMap<String, String> resultSessionMap = new HashMap<String, String>();
        // If it is a broadcast don't provide the remote address because it is deceiving.
        String loadAddress = "";
        if (addressNameMap.size() == 1)
            loadAddress = addressNameMap.keySet().iterator().next();

        //fetch the question
        Question question = Question.fromURL(url, config.getConfigId(), loadAddress, config.getMyAddress(), null, null);
        for (String address : addressNameMap.keySet()) {
            String formattedAddress = PhoneNumberUtils.formatNumber(address, PhoneNumberFormat.E164);
            if (formattedAddress != null) {
                //avoid multiple calls to be made to the same number, from the same adapter. 
                Session session = Session.getSession(Session.getSessionKey(config, formattedAddress));
                if (session != null) {
                    // recreate a fresh session
                    session.drop();
                    session = Session.getOrCreateSession(config, formattedAddress);
                }
                else {
                    session = Session.getOrCreateSession(config, formattedAddress);
                }
                session.killed = false;
                session.setStartUrl(url);
                session.setDirection("outbound");
                session.setRemoteAddress(formattedAddress);
                session.setType(AdapterAgent.ADAPTER_TYPE_TWILIO);
                session.setAdapterID(config.getConfigId());
                session.setQuestion(question);
                dialogLog.log(LogLevel.INFO, session.getAdapterConfig(), String.format("Outgoing call requested from: %s to: %s",
                                                                                       session.getLocalAddress(),
                                                                                       formattedAddress), session);
                String extSession = "";
                if (!ServerUtils.isInUnitTestingEnvironment()) {
                	String accountSid = config.getAccessToken();
                	String authToken = config.getAccessTokenSecret();
                    TwilioRestClient twilio = new TwilioRestClient(accountSid, authToken);
                    
                    // Get the main account (The one we used to authenticate the client)
            		Account mainAccount = twilio.getAccount();
                    
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
            		System.out.println(call.getSid());
            		extSession = call.getSid();
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
        return resultSessionMap;
	}
	
	@Path("new")
    @GET
    @Produces("application/xml")
    public Response getNewDialog(@QueryParam("CallSid") String CallSid,
    		@QueryParam("AccountSid") String AccountSid,
    		@QueryParam("From") String localID,
    		@QueryParam("To") String remoteID,
    		@QueryParam("Direction") String direction) {
		
		log.info("call started:"+direction+":"+remoteID+":"+localID);
        
        if (direction.equals("inbound")) {
            String tmpLocalId = new String(localID);
            localID = new String(remoteID);
            remoteID = tmpLocalId;
        }
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_TWILIO, localID);
        String formattedRemoteId = PhoneNumberUtils.formatNumber(remoteID, null);
        String sessionKey = AdapterAgent.ADAPTER_TYPE_TWILIO+"|"+localID+"|"+ formattedRemoteId;
        Session session = Session.getSession(sessionKey);
        
        String url = "";
        if ( session != null && direction.startsWith("outbound")) {
                url = session.getStartUrl();
            dialogLog.log(LogLevel.INFO, config, String
                                            .format("Trying to fetch dialog for %s, due to outgoing Call from: %s ",
                                                    formattedRemoteId, config.getMyAddress()), session);
        }
        else if(direction.equals("inbound")) {
            //create a session for incoming only
            session = Session.getOrCreateSession(config, formattedRemoteId);
        }
        
        if(session != null) {
            session.setStartUrl( url );
            session.setDirection( direction );
            session.setRemoteAddress( formattedRemoteId );
            session.setType( AdapterAgent.ADAPTER_TYPE_TWILIO );
            session.setAccountId( config.getOwner() );
            session.setAdapterID( config.getConfigId() );
        }
        else {
            log.severe(String.format("Session %s not found", sessionKey));
            return null;
        }
        
        Question question = session.getQuestion();
        if(question == null) {
            question = Question.fromURL(url, session.getAdapterConfig().getConfigId(), formattedRemoteId, localID,
                                        session.getDdrRecordId(), session.getKey());
        }
        session.setQuestion(question);
        
        if (session.getQuestion() != null) {

            //create ddr record
            DDRRecord ddrRecord = null;
            try {
                if (direction.contains("outbound")) {
                    ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(config, formattedRemoteId, 1, url);
                }
                else {
                    ddrRecord = DDRUtils.createDDRRecordOnIncomingCommunication(config, formattedRemoteId, 1, url);
                }
                session.setDdrRecordId( ddrRecord != null ? ddrRecord.getId() : null);
                ddrRecord.addAdditionalInfo(Session.TRACKING_TOKEN_KEY, session.getTrackingToken());
            }
            catch (Exception e) {
                String errorMessage = String.format("Creating DDR records failed. Direction: %s for adapterId: %s with address: %s remoteId: %s and localId: %s",
                                                    direction, config.getConfigId(), config.getMyAddress(), formattedRemoteId,
                                                    localID);
                log.severe(errorMessage);
                dialogLog.severe(config.getConfigId(), errorMessage, ddrRecord != null ? ddrRecord.getId() : null,
                                 sessionKey);
            }
            finally {
            	ddrRecord.createOrUpdate();
                session.storeSession();
            }
            return handleQuestion( question, config, formattedRemoteId, sessionKey );
        }
        else {
            return Response.ok().build();
        }
	}
	
	@Path("new")
    @POST
    @Produces("application/xml")
    public Response getNewDialogPost(@FormParam("CallSid") String CallSid,
    		@FormParam("AccountSid") String AccountSid,
    		@FormParam("From") String localID,
    		@FormParam("To") String remoteID,
    		@FormParam("Direction") String direction) {
		
		log.info("call started:"+direction+":"+remoteID+":"+localID);
        
        //swap the remote and the local numbers if its inbound
        if(direction.equals("inbound")) {
        	String tmpLocalId = new String(localID);
        	localID = new String(remoteID);
        	remoteID = tmpLocalId;        			
        }
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_TWILIO, localID);
        String formattedRemoteId = PhoneNumberUtils.formatNumber(remoteID, null);
        String sessionKey = AdapterAgent.ADAPTER_TYPE_TWILIO+"|"+localID+"|"+ formattedRemoteId;
        Session session = Session.getSession(sessionKey);
        
        String url = "";
        if ( session != null && direction.startsWith("outbound")) {
                url = session.getStartUrl();
            dialogLog.log(LogLevel.INFO, config, String
                                            .format("Trying to fetch dialog for %s, due to outgoing Call from: %s ",
                                                    formattedRemoteId, config.getMyAddress()), session);
        }
        else if(direction.equals("inbound")) {
            //create a session for incoming only
            session = Session.getOrCreateSession(config, formattedRemoteId);
            url = config.getURLForInboundScenario();
        }
        
        if(session != null) {
            session.setStartUrl( url );
            session.setDirection( direction );
            session.setRemoteAddress( formattedRemoteId );
            session.setType( AdapterAgent.ADAPTER_TYPE_TWILIO );
            session.setAccountId( config.getOwner() );
            session.setAdapterID( config.getConfigId() );
        }
        else {
            log.severe(String.format("Session %s not found", sessionKey));
            return null;
        }
        
        Question question = session.getQuestion();
        if(question == null) {
            question = Question.fromURL(url, session.getAdapterConfig().getConfigId(), formattedRemoteId, localID,
                                        session.getDdrRecordId(), session.getKey());
        }
        session.setQuestion(question);
        
        if (session.getQuestion() != null) {

            //create ddr record
            DDRRecord ddrRecord = null;
            try {
                if (direction.contains("outbound")) {
                    ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(config, formattedRemoteId, 1, url);
                }
                else {
                    ddrRecord = DDRUtils.createDDRRecordOnIncomingCommunication(config, formattedRemoteId, 1, url);
                }
                session.setDdrRecordId( ddrRecord != null ? ddrRecord.getId() : null);
                ddrRecord.addAdditionalInfo(Session.TRACKING_TOKEN_KEY, session.getTrackingToken());
            }
            catch (Exception e) {
                String errorMessage = String.format("Creating DDR records failed. Direction: %s for adapterId: %s with address: %s remoteId: %s and localId: %s",
                                                    direction, config.getConfigId(), config.getMyAddress(), formattedRemoteId,
                                                    localID);
                log.severe(errorMessage);
                dialogLog.severe(config.getConfigId(), errorMessage, ddrRecord != null ? ddrRecord.getId() : null,
                                 sessionKey);
            }
            finally {
            	ddrRecord.createOrUpdate();
                session.storeSession();
            }
            return handleQuestion( question, config, formattedRemoteId, sessionKey );
        }
        else {
            return Response.ok().build();
        }
	}
	
    @Path("answer")
    @GET
    @Produces("application/xml")
    public Response answer(@QueryParam("answerId") String answer_id, @QueryParam("Digits") String answer_input,
        @QueryParam("From") String localID, @QueryParam("To") String remoteID,
        @QueryParam("Direction") String direction, @QueryParam("RecordingUrl") String recordingUrl, 
        @QueryParam("DialCallStatus") String dialCallStatus, @QueryParam("DialCallSid") String dialCallSid) {

        TwiMLResponse twiml = new TwiMLResponse();
        
        try {
            answer_input = answer_input != null ? URLDecoder.decode(answer_input, "UTF-8") : answer_input;
        }
        catch (UnsupportedEncodingException e) {
            log.warning(String.format("Answer input decode failed for: %s", answer_input));
        }
        
        if(recordingUrl!=null) {
        	answer_input= recordingUrl.replace(".wav", "") + ".wav";
        }

        if (direction.equals("inbound")) {
            String tmpLocalId = localID;
            localID = remoteID;
            remoteID = tmpLocalId;
        }

        String sessionKey = AdapterAgent.ADAPTER_TYPE_TWILIO + "|" + localID + "|" + remoteID;

        Session session = Session.getSession(sessionKey);
        
        // Remove the referralSession
        if ("completed".equals(dialCallStatus)) {

            AdapterConfig config = session.getAdapterConfig();
            finalizeCall(config, null, dialCallSid, "outbound", null);
        }
        //if call is rejected. call the hangup event
        else if ("no-answer".equalsIgnoreCase(dialCallStatus) && session != null && session.getQuestion() != null) {
            
            Map<String, String> extras = session.getExtras();
            extras.put("requester", session.getLocalAddress());
            Question noAnswerQuestion = session.getQuestion().event("hangup", "Call rejected", extras, remoteID);
            return handleQuestion(noAnswerQuestion, session.getAdapterConfig(), remoteID, sessionKey);
        }
        
        if (session != null) {
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
                                                session.getRemoteAddress(), question.getQuestion_expandedtext()),
                                  session);
                }
                String answerForQuestion = question.getQuestion_expandedtext();
                question = question.answer(responder, session.getAdapterConfig().getConfigId(), answer_id,
                                           answer_input, sessionKey);
                //reload the session
                session = Session.getSession(sessionKey);
                session.setQuestion(question);
                session.storeSession();
                //check if ddr is in session. save the answer in the ddr
                if (session.getDdrRecordId() != null) {
                    try {
                        DDRRecord ddrRecord = DDRRecord.getDDRRecord(session.getDdrRecordId(), session.getAccountId());
                        if (ddrRecord != null) {
                            ddrRecord.addAdditionalInfo(DDRRecord.ANSWER_INPUT_KEY + ":" + answerForQuestion,
                                                        answer_input);
                            ddrRecord.createOrUpdateWithLog();
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
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

        String reply = twiml.toXML();
        return Response.ok(reply).build();
    }
	
    @Path("timeout")
    @GET
    @Produces("application/xml")
    public Response timeout(@QueryParam("From") String localID, @QueryParam("To") String remoteID,
        @QueryParam("Direction") String direction) throws Exception {

        String sessionKey = AdapterAgent.ADAPTER_TYPE_TWILIO+"|"+localID+"|"+ remoteID;
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
                                        question.getQuestion_expandedtext()), session);
            HashMap<String, Object> extras = new HashMap<String, Object>();
            extras.put("sessionKey", sessionKey);
            extras.put("requester", session.getLocalAddress());
            question = question.event("timeout", "No answer received", extras, responder);
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
        TwiMLResponse twiml = new TwiMLResponse();
        String reply = twiml.toXML();
        return Response.ok(reply).build();
    }
	
    @Path("preconnect")
    @GET
    @Produces("application/voicexml+xml")
    public Response preconnect(@QueryParam("From") String localID, @QueryParam("To") String remoteID,
            @QueryParam("Direction") String direction) {

    	// TODO: test
    	String sessionKey = AdapterAgent.ADAPTER_TYPE_TWILIO+"|"+localID+"|"+ remoteID;
    	
        String reply = (new TwiMLResponse()).toXML();
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
                                        question.getQuestion_expandedtext()), session);

            HashMap<String, String> extras = new HashMap<String, String>();
            extras.put("sessionKey", sessionKey);
            extras.put("requester", session.getLocalAddress());
            question = question.event("preconnect", "Wrong answer received", extras, responder);
            //reload the session
            session = Session.getSession(sessionKey);
            session.setQuestion(question);
            session.storeSession();
            return handleQuestion(question, session.getAdapterConfig(), responder, sessionKey);
        }
        return Response.ok(reply).build();
    }
    
	@Path("cc")
    @GET
    public Response receiveCCMessage(@QueryParam( "CallSid" ) String callSid,
    		@QueryParam( "From" ) String localID,
            @QueryParam( "To" ) String remoteID,
            @QueryParam( "Direction" ) String direction,
            @QueryParam( "CallStatus" ) String status) {
		
		if(direction.equals("outbound-api")) {
			direction = "outbound";
		}
		
		if(direction.equals("inbound")) {
        	String tmpLocalId = localID;
        	localID = remoteID;
        	remoteID = tmpLocalId;        			
        }
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_TWILIO, localID);
        String sessionKey = AdapterAgent.ADAPTER_TYPE_TWILIO + "|" + config.getMyAddress() +
		                "|" + remoteID;
		Session session = Session.getSession(sessionKey);
		if (session != null) {
			
			//update session with call timings
            if (status.equals("completed")) {
            	
            	finalizeCall(config, session, callSid, direction, remoteID);
            }
		}
		
		log.info("Session key: " + sessionKey);
        
		
		return Response.ok("").build();
	}
	
	/**
	 * Retrieve call information and with that:
	 *   - update ddr record
	 *   - destroy session
	 *   - send hangup
	 * @param config
	 * @param session
	 * @param callSid
	 * @param direction
	 * @param remoteID
	 */
	private void finalizeCall(AdapterConfig config, Session session, String callSid, String direction, String remoteID) {
		
		String accountSid = config.getAccessToken();
    	String authToken = config.getAccessTokenSecret();
    	TwilioRestClient client = new TwilioRestClient(accountSid, authToken);
    	
    	Call call = client.getAccount().getCall(callSid); 
    	
    	if(session==null) {
    		String localAddress = call.getFrom(); 
    		remoteID = call.getTo();
    		String sessionKey = AdapterAgent.ADAPTER_TYPE_TWILIO + "|" + localAddress + "|" + remoteID;
    		session = Session.getSession(sessionKey);
    	}
    	
    	if(session!=null) {
    		log.info("Finalizing call for: "+session.getKey());
	    	String pattern = "EEE, dd MMM yyyy HH:mm:ss Z";
	    	SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
	    	
	    	Long startTime  = 0L;
	    	Long answerTime = 0L;
	    	Long endTime = 0L;
	    	try {
	    		String created = call.getProperty("date_created");
	    		startTime = format.parse(created).getTime();
	    		answerTime = format.parse(call.getStartTime()).getTime();
	    		endTime = format.parse(call.getEndTime()).getTime();
	    	    
	    		//sometimes answerTimeStamp is only given in the ACTIVE ccxml
	            session.setAnswerTimestamp(answerTime+"");
	            session.setStartTimestamp(startTime+"");
	            session.setReleaseTimestamp(endTime+"");
	            session.setDirection(direction);
	            session.setRemoteAddress(remoteID);
	            session.setLocalAddress(config.getMyAddress());
	            session.storeSession();
	            //flush the keys if ddrProcessing was successful
	            if (DDRUtils.stopDDRCosts(session.getKey(), true)) {
	                session.drop();
	            }
	            hangup(session);
	        
	    	} catch (Exception e) {
	    		e.printStackTrace();
	    	}
    	} else {
    		log.warning("Failed to finalize call because no session was found for: " + callSid);
    	}
	}
	
	/**
     * hang up a call based on the session.
     * 
     * @param session if null, doesnt trigger an hangup event. Also expects a question to be there in this session, or atleast a 
     * startURL from where the question can be fetched.
     * @return
     * @throws Exception
     */
    public Response hangup(Session session) throws Exception {

        if (session != null) {
            log.info("call hangup with:" + session.getDirection() + ":" + session.getRemoteAddress() + ":" +
                     session.getLocalAddress());
            if (session.getQuestion() == null) {
                Question question = Question.fromURL(session.getStartUrl(), session.getAdapterConfig().getConfigId(),
                                                     session.getRemoteAddress(), session.getLocalAddress(),
                                                     session.getDdrRecordId(), session.getKey());
                session.setQuestion(question);
            }
            if (session.getQuestion() != null && !isEventTriggered("hangup", session)) {
                
                HashMap<String, Object> timeMap = getTimeMap(session.getStartTimestamp(), session.getAnswerTimestamp(),
                                                             session.getReleaseTimestamp());
                timeMap.put("referredCalledId", session.getExtras().get("referredCalledId"));
                timeMap.put("sessionKey", session.getKey());
                if(session.getExtras() != null && !session.getExtras().isEmpty()) {
                    timeMap.putAll(session.getExtras());
                }
                Response hangupResponse = handleQuestion(null, session.getAdapterConfig(), session.getRemoteAddress(),
                                                         session.getKey());
                timeMap.put("requester", session.getLocalAddress());
                session.getQuestion().event("hangup", "Hangup", timeMap, session.getRemoteAddress());
                dialogLog.log(LogLevel.INFO, session.getAdapterConfig(),
                              String.format("Call hungup from: %s", session.getRemoteAddress()), session);
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
     * check if for this session an 
     * @param eventName
     * @param session
     * @return
     */
    private static boolean isEventTriggered(String eventName, Session session) {

        if (session != null) {
            if (session.getExtras().get("event_" + eventName) != null) {
                String timestamp = TimeUtils.getStringFormatFromDateTime(Long.parseLong(session.getExtras()
                                                .get("event_" + eventName)), null);
                log.warning(eventName + "event already triggered before for this session at: " + timestamp);
                return true;
            }
            else {
                session.getExtras().put("event_" + eventName, String.valueOf(TimeUtils.getServerCurrentTimeInMillis()));
                session.storeSession();
            }
        }
        return false;
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
                //question = question.answer(null, adapterID, null, null);
                break;
            }
            else if (question.getType().equalsIgnoreCase("referral")) {
                if (question.getUrl() != null && !question.getUrl().startsWith("tel:")) {
                    question = Question.fromURL(question.getUrl(), adapterID, address, ddrRecordId, sessionKey);
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

        TwiMLResponse twiml = new TwiMLResponse();

        try {
            addPrompts(prompts, question.getPreferred_language(), twiml);
            Redirect redirect = new Redirect(getAnswerUrl());
            redirect.setMethod("GET");
            twiml.append(redirect);
        }
        catch (TwiMLException e) {
            e.printStackTrace();
        }
        return twiml.toXML();
    }
    
    protected String renderReferral(Question question,ArrayList<String> prompts, String sessionKey, String remoteID){
    	TwiMLResponse twiml = new TwiMLResponse();
    	
    	try {
    		addPrompts(prompts, question.getPreferred_language(), twiml);
    		
    		String redirectTimeoutProperty = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT );
                String redirectTimeout = redirectTimeoutProperty != null ? redirectTimeoutProperty.replace("s", "") : "30";
    		int timeout = 30;
            try {
                timeout = Integer.parseInt(redirectTimeout);
            }
            catch (NumberFormatException e) {
                e.printStackTrace();
            }

            com.twilio.sdk.verbs.Number number = new com.twilio.sdk.verbs.Number(question.getUrl());
            
            String usePreconnect = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.USE_PRECONNECT );
    		usePreconnect = usePreconnect != null ? usePreconnect : "false";
    		boolean preconnect = Boolean.parseBoolean(usePreconnect);
    		
    		if(preconnect) {
    			number.setMethod("GET");
    			number.setUrl(getPreconnectUrl());
    		}
    		
    		Dial dial = new Dial();
    		dial.setCallerId(remoteID);
    		dial.append(number);
    		dial.setTimeout(timeout);
    		
    		dial.setMethod("GET");
    		dial.setAction(getAnswerUrl());
    		
    		twiml.append(dial);
    	}catch(TwiMLException e ) {
    		log.warning("Failed to create referal");
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
        } else {
        	List<Answer> answers = question.getAnswers();
        	for(Answer answer : answers) {
        		if(answer.getAnswer_text().startsWith("dtmfKey://#")) {
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
            addPrompts(prompts, question.getPreferred_language(), gather);
            
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

    protected String renderOpenQuestion(Question question,ArrayList<String> prompts,String sessionKey) {
		TwiMLResponse twiml = new TwiMLResponse();
		
		String typeProperty = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.TYPE );
		if(typeProperty!=null && typeProperty.equalsIgnoreCase("audio")) {
			renderVoiceMailQuestion(question, prompts, sessionKey, twiml);
		} else {
			
	        Gather gather = new Gather();
	        gather.setAction(getAnswerUrl());
	        gather.setMethod("GET");
			
			String dtmfMaxLength = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.ANSWER_INPUT_MAX_LENGTH );
			if(dtmfMaxLength!=null) {
	            try {
	            	int digits = Integer.parseInt(dtmfMaxLength);
	            	gather.setNumDigits(digits);
	            }
	            catch (NumberFormatException e) {
	                e.printStackTrace();
	            }
			}
			
			String noAnswerTimeout = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT );
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
            	addPrompts(prompts, question.getPreferred_language(), gather);
                
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

	/** renders/updates the xml for recording an audio and posts it to the user on the callback 
	     * @param question
	     * @param prompts
	     * @param sessionKey
	     * @param outputter
	     * @throws IOException
	     * @throws UnsupportedEncodingException
	     */
    protected void renderVoiceMailQuestion(Question question, ArrayList<String> prompts, String sessionKey,
                                           TwiMLResponse twiml) {

    	addPrompts(prompts, question.getPreferred_language(), twiml);
    	
    	Record record = new Record();    	
    	record.setAction(getAnswerUrl());
    	record.setMethod("GET");
    	
    	// Set max voicemail length
    	//assign a default voice mail length if one is not specified
        String voiceMessageLengthProperty = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                           MediaPropertyKey.VOICE_MESSAGE_LENGTH);
        voiceMessageLengthProperty = voiceMessageLengthProperty != null ? voiceMessageLengthProperty : "15";
        int length = 15;
        try {
            length = Integer.parseInt(voiceMessageLengthProperty);
        }
        catch (NumberFormatException e) {
            log.warning("Failed to parse timeout for voicemail e: "+e.getMessage());
        }
        record.setMaxLength(length);

        // Set timeout
        String timeoutProperty = question.getMediaPropertyValue(MediumType.BROADSOFT,
                MediaPropertyKey.TIMEOUT);
        timeoutProperty = timeoutProperty != null ? timeoutProperty : "5";
        int timeout = 5;
        try {
            timeout = Integer.parseInt(timeoutProperty);
        }
        catch (NumberFormatException e) {
            log.warning("Failed to parse timeout for voicemail e: "+e.getMessage());
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
        } catch (TwiMLException e) {
        	log.warning("Failed to append record");
        }
	}
    
    protected String renderExitQuestion(Question question, ArrayList<String> prompts, String sessionKey) {
    	TwiMLResponse twiml = new TwiMLResponse();
    	
    	addPrompts(prompts, question.getPreferred_language(), twiml);
    	
    	try {
        	twiml.append(new Hangup());
        } catch (TwiMLException e) {
        	log.warning("Failed to append hangup");
        }
    	
    	return twiml.toXML();
    }
    
    protected void addPrompts(ArrayList<String> prompts, String language, Verb twiml) {

        String lang = language.contains("-") ? language : "nl-NL";
        try {
            for (String prompt : prompts) {
                if (prompt.startsWith("http")) {
                    twiml.append(new Play(prompt));
                }
                else {
                    Say say = new Say(prompt.replace("text://", ""));
                    say.setLanguage(lang);
                    twiml.append(say);
                }
            }
        }
        catch (TwiMLException e) {
            log.warning("failed to added prompts: " + e.getMessage());
        }
    }
    
	private Response handleQuestion(Question question, AdapterConfig adapterConfig, String remoteID, String sessionKey) {

		String result = (new TwiMLResponse()).toXML();
		Return res = formQuestion(question, adapterConfig.getConfigId(),remoteID, null, sessionKey);
		if (question != null && !question.getType().equalsIgnoreCase("comment"))
			question = res.question;
		Session session = Session.getSession(sessionKey);
		// if the adapter is a trial adapter, add a introductory node
		log.info("question formed at handleQuestion is: "+ ServerUtils.serializeWithoutException(question));
		log.info("prompts formed at handleQuestion is: " + res.prompts);

		if (question != null) {
			question.generateIds();
			session.setQuestion(question);
			session.setRemoteAddress(remoteID);
			session.storeSession();

			if (question.getType().equalsIgnoreCase("closed")) {
				result = renderClosedQuestion(question, res.prompts, sessionKey);
			} else if (question.getType().equalsIgnoreCase("open")) {
				result = renderOpenQuestion(question, res.prompts, sessionKey);
			} else if (question.getType().equalsIgnoreCase("referral")) {
				if (question.getUrl() != null
						&& question.getUrl().startsWith("tel:")) {
					// added for release0.4.2 to store the question in the
					// session,
					// for triggering an answered event
					
					// Check with remoteID we are going to use for the call
					String externalCallerId = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.USE_EXTERNAL_CALLERID );
		            Boolean callerId = false;
		            if(externalCallerId!=null) {
		            	callerId = Boolean.parseBoolean(externalCallerId);
		            }
		    		if(!callerId) {
		    			remoteID = adapterConfig.getMyAddress();
		    		}
		    		
		    		log.info(String.format("current session key before referral is: %s and remoteId %s", sessionKey, remoteID));
					
					String redirectedId = PhoneNumberUtils.formatNumber(question.getUrl().replace("tel:", ""), null);
					if (redirectedId != null) {
						// update url with formatted redirecteId. RFC3966
						// returns format tel:<blabla> as expected
						question.setUrl(redirectedId);
						// store the remoteId as its lost while trying to
						// trigger the answered event
						HashMap<String, String> extras = new HashMap<String, String>();
						extras.put("referredCalledId", redirectedId);
						session.getExtras().putAll(extras);
						session.setQuestion(question);
						session.setRemoteAddress(remoteID);
						
						// create a new ddr record and session to catch the
						// redirect
						Session referralSession = Session.getOrCreateSession(adapterConfig, remoteID, redirectedId);
						if (session.getDirection() != null) {
							DDRRecord ddrRecord = null;
							try {
								ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, redirectedId, 1,question.getUrl());
								if (ddrRecord != null) {
									ddrRecord.addAdditionalInfo(Session.TRACKING_TOKEN_KEY,session.getTrackingToken());
									ddrRecord.createOrUpdate();
									referralSession.setDdrRecordId(ddrRecord.getId());
								}
							} catch (Exception e) {
								e.printStackTrace();
								log.severe(String.format("Continuing without DDR. Error: %s",e.toString()));
							}
							referralSession.setDirection(session.getDirection());
							referralSession.setTrackingToken(session.getTrackingToken());
						}
						referralSession.setQuestion(session.getQuestion());
						referralSession.storeSession();
						session.storeSession();
					} else {
						log.severe(String.format("Redirect address is invalid: %s. Ignoring.. ",question.getUrl().replace("tel:", "")));
					}
					result = renderReferral(question, res.prompts, sessionKey, remoteID);
				}
			} else if (question.getType().equalsIgnoreCase("exit")) {
				result = renderExitQuestion(question, res.prompts, sessionKey);
			} else if (res.prompts.size() > 0) {
				result = renderComment(question, res.prompts, sessionKey);
			}
		} else if (res.prompts.size() > 0) {
			result = renderComment(null, res.prompts, sessionKey);
		} else {
			log.info("Going to hangup? So clear Session?");
		}
		log.info("Sending xml: " + result);
		return Response.status(Status.OK).type(MediaType.APPLICATION_XML).entity(result).build();
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
}
