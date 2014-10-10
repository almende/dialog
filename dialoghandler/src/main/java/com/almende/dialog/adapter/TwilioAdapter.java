package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.znerd.xmlenc.XMLOutputter;

import com.almende.dialog.LogLevel;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.myBlobstore.MyBlobStore;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.resource.factory.CallFactory;
import com.twilio.sdk.resource.instance.Account;
import com.twilio.sdk.resource.instance.Call;
import com.twilio.sdk.verbs.Gather;
import com.twilio.sdk.verbs.Play;
import com.twilio.sdk.verbs.Redirect;
import com.twilio.sdk.verbs.TwiMLException;
import com.twilio.sdk.verbs.TwiMLResponse;

@Path("twilio")
public class TwilioAdapter {
	protected static final Logger log = Logger.getLogger(VoiceXMLRESTProxy.class.getName());
	protected static final com.almende.dialog.Logger dialogLog =  new com.almende.dialog.Logger();
	private static final int LOOP_DETECTION=10;
	protected String TIMEOUT_URL="timeout";
	protected String EXCEPTION_URL="exception";
	private String host = "";
	
	public static HashMap<String, String> dial(Map<String, String> addressNameMap, String url, String senderName,
	        AdapterConfig config) throws Exception {
		
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
            		callParams.put("ApplicationSid", "AP865e18e691287a06b24ad1097d935c35");
            		//callParams.put("Url", "http://" + Settings.HOST + "/dialoghandler/rest/twilio/new");
            		
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
    		@QueryParam("Direction") String direction,
    		@Context UriInfo ui) {
		
		// TODO: Implement
		log.info("call started:"+direction+":"+remoteID+":"+localID);
        this.host=ui.getBaseUri().toString().replace(":80", "");
        
        String formattedRemoteId = remoteID;
        
        if(direction.equals("inbound")) {
        	String tmpLocalId = localID;
        	localID = remoteID;
        	remoteID = tmpLocalId;        			
        }
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_TWILIO, localID);
            
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
    		@FormParam("Direction") String direction, 
    		@Context UriInfo ui) {
		
		
		// TODO: Implement
		log.info("call started:"+direction+":"+remoteID+":"+localID);
        this.host=ui.getBaseUri().toString().replace(":80", "");
        String formattedRemoteId = remoteID;
        
        if(direction.equals("inbound")) {
        	String tmpLocalId = localID;
        	localID = remoteID;
        	remoteID = tmpLocalId;        			
        }
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_TWILIO, localID);
                    
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
	
	@Path( "answer" )
    @GET
    @Produces("application/xml")
    public Response answer( @QueryParam( "answerId" ) String answer_id, @QueryParam( "Digits" ) String answer_input,
            @QueryParam( "From" ) String localID,
            @QueryParam( "To" ) String remoteID,
            @QueryParam( "Direction" ) String direction,
            @Context UriInfo ui )
    {
		TwiMLResponse twiml = new TwiMLResponse();
		
		try
        {
            answer_input = answer_input != null ? URLDecoder.decode( answer_input, "UTF-8" ) : answer_input;
        }
        catch ( UnsupportedEncodingException e )
        {
            log.warning( String.format( "Answer input decode failed for: %s", answer_input) );
        }
		
		if(direction.equals("inbound")) {
        	String tmpLocalId = localID;
        	localID = remoteID;
        	remoteID = tmpLocalId;        			
        }
            
        String sessionKey = AdapterAgent.ADAPTER_TYPE_TWILIO+"|"+localID+"|"+ remoteID;
        this.host = ui.getBaseUri().toString().replace( ":80", "" );
        
        Session session = Session.getSession( sessionKey );
        if ( session != null )
        {
            Question question = session.getQuestion();
            if ( question != null )
            {
                String responder = session.getRemoteAddress();
                if ( session.killed )
                {
                    log.warning( "session is killed" );
                    return Response.status( Response.Status.BAD_REQUEST ).build();
                }
                if (question.getType() != null && !question.getType().equalsIgnoreCase("comment")) {
                    dialogLog.log(LogLevel.INFO,
                                  session.getAdapterConfig(),
                                  String.format("Answer input: %s from: %s to question: %s", answer_input,
                                                session.getRemoteAddress(), question.getQuestion_expandedtext()),
                                  session);
                }
                String answerForQuestion = question.getQuestion_expandedtext();
                question = question.answer( responder, session.getAdapterConfig().getConfigId(), answer_id,
                    answer_input, sessionKey );
                //reload the session
                session = Session.getSession( sessionKey );
                session.setQuestion( question );
                session.storeSession();
                //check if ddr is in session. save the answer in the ddr
                if(session.getDdrRecordId() != null) {
                    try {
                        DDRRecord ddrRecord = DDRRecord.getDDRRecord(session.getDdrRecordId(), session.getAccountId());
                        if(ddrRecord != null) {
                            ddrRecord.addAdditionalInfo(DDRRecord.ANSWER_INPUT_KEY + ":" + answerForQuestion,
                                                        answer_input);
                            ddrRecord.createOrUpdateWithLog();
                        }
                    }
                    catch (Exception e) {
                    }
                }
                return handleQuestion( question, session.getAdapterConfig(), responder, sessionKey );
            } else {
                log.warning( "No question found in session!" );
            }
        }
        else
        {
            log.warning( "No session found for: " + sessionKey );
            dialogLog.severe(null, "No session found!", session);
        }
        
        String reply = twiml.toXML();
        return Response.ok( reply ).build();
    }
	
	@Path( "timeout" )
    @GET
    @Produces( "application/xml" )
    public Response timeout( @QueryParam( "From" ) String localID,
            @QueryParam( "To" ) String remoteID,
            @QueryParam( "Direction" ) String direction ) throws Exception
    {
		TwiMLResponse twiml = new TwiMLResponse();
		String reply = twiml.toXML();
    	// TODO: Implement
    	return Response.ok(reply).build();
    }
	
	@Path("cc")
    @GET
    public Response receiveCCMessage(@QueryParam( "CallSid" ) String callSid,
    		@QueryParam( "From" ) String localID,
            @QueryParam( "To" ) String remoteID,
            @QueryParam( "Direction" ) String direction,
            @QueryParam( "CallStatus" ) String status) {
		
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
            	
            	String accountSid = config.getAccessToken();
            	String authToken = config.getAccessTokenSecret();
            	TwilioRestClient client = new TwilioRestClient(accountSid, authToken);
            	
            	Call call = client.getAccount().getCall(callSid); 
            	
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
            }
		}
		
		log.info("Session key: " + sessionKey);
        
		
		return Response.ok().build();
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
            	String prefix = "http://tts.ask-fast.com/api/parse?text=";
                prompts.add(prefix+qText);
            }
            
            if (question.getType().equalsIgnoreCase("closed")) {
                for (Answer ans : question.getAnswers()) {
                    String answer = ans.getAnswer_text();
                    if (answer != null && !answer.equals("") && !answer.startsWith("dtmfKey://")) {
                    	String prefix = "http://tts.ask-fast.com/api/parse?text=";
                        prompts.add(prefix+answer);
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
	
    protected String renderComment(Question question,ArrayList<String> prompts, String sessionKey){
    	TwiMLResponse twiml = new TwiMLResponse();
    	
    	try {
	    	for(String prompt : prompts) {
	    		twiml.append(new Play(prompt));
	    	}
	    	Redirect redirect = new Redirect(getAnswerUrl());
	    	redirect.setMethod("GET");
	    	twiml.append(redirect);
    	}catch(TwiMLException e ) {
    		e.printStackTrace();
    	}
    	return twiml.toXML();
    }
	/*protected String renderComment(Question question,ArrayList<String> prompts, String sessionKey){

	String redirectTimeoutProperty = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT );
        //assign a default timeout if one is not specified
        String redirectTimeout = redirectTimeoutProperty != null ? redirectTimeoutProperty : "40s";
        if(!redirectTimeout.endsWith("s"))
        {
            log.warning("Redirect timeout must be end with 's'. E.g. 40s. Found: "+ redirectTimeout);
            redirectTimeout += "s";
        }
        
        String redirectTypeProperty = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.TYPE );
        String redirectType = redirectTypeProperty != null ? redirectTypeProperty.toLowerCase() : "bridge";
        if(!redirectType.equals("blind") && !redirectType.equals("bridge"))
        {
            log.warning("Redirect must be blind or bridge. Found: "+ redirectTimeout);
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
						if (question != null && question.getType().equalsIgnoreCase("referral")){
							outputter.startTag("transfer");
								outputter.attribute("name", "thisCall");
								outputter.attribute("dest", question.getUrl());
								if(redirectType.equals("bridge")) {
									outputter.attribute("bridge","true");
								} else {
									outputter.attribute("bridge","false");
								}
								outputter.attribute("connecttimeout",redirectTimeout);
								for (String prompt : prompts){
									outputter.startTag("prompt");
										outputter.startTag("audio");
											outputter.attribute("src", prompt);
										outputter.endTag();
									outputter.endTag();
								}
								outputter.startTag("filled");
									outputter.startTag("if");
										outputter.attribute("cond", "thisCall=='noanswer'");
										outputter.startTag("goto");
											outputter.attribute("next", TIMEOUT_URL+"?questionId="+question.getQuestion_id()+"&sessionKey="+URLEncoder.encode(sessionKey, "UTF-8"));
										outputter.endTag();
									outputter.startTag("elseif");
										outputter.attribute("cond", "thisCall=='busy' || thisCall=='network_busy'");
									outputter.endTag();
										outputter.startTag("goto");
											outputter.attribute("next", EXCEPTION_URL+"?questionId="+question.getQuestion_id()+"&sessionKey="+URLEncoder.encode(sessionKey, "UTF-8"));
										outputter.endTag();	
									outputter.startTag("else");
									outputter.endTag();
										outputter.startTag("goto");
											outputter.attribute("next", getAnswerUrl()+"?questionId="+question.getQuestion_id()+"&sessionKey="+URLEncoder.encode(sessionKey, "UTF-8"));
										outputter.endTag();	
									outputter.endTag();
								outputter.endTag();
							outputter.endTag();
						} else {
							outputter.startTag("block");
								for (String prompt : prompts){
									outputter.startTag("prompt");
										outputter.startTag("audio");
											outputter.attribute("src", prompt);
										outputter.endTag();
									outputter.endTag();
								}
								if(question!=null) {
									outputter.startTag("goto");
										outputter.attribute("next", getAnswerUrl()+"?questionId="+question.getQuestion_id()+"&sessionKey="+URLEncoder.encode(sessionKey, "UTF-8"));
									outputter.endTag();
								}
							outputter.endTag();
						}
						
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();
		} catch (Exception e) {
			log.severe("Exception in creating question XML: "+ e.toString());
		}
		return sw.toString();	
	}*/
	
	private String renderClosedQuestion(Question question, ArrayList<String> prompts, String sessionKey) {
				
		try {
			sessionKey = URLEncoder.encode(sessionKey, "UTF-8");
		} catch (UnsupportedEncodingException e ) {
			e.printStackTrace();
		}
		
		TwiMLResponse twiml = new TwiMLResponse();
        Gather gather = new Gather();
        gather.setAction(getAnswerUrl());
        gather.setMethod("GET");
        gather.setNumDigits(1);
        
        String noAnswerTimeout = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT);
        //assign a default timeout if one is not specified
        noAnswerTimeout = noAnswerTimeout != null ? noAnswerTimeout : "10";
        if (noAnswerTimeout.endsWith("s")) {
            log.warning("No answer timeout must end with 's'. E.g. 10s. Found: " + noAnswerTimeout);
            noAnswerTimeout = noAnswerTimeout.replace("s", "");
        }
        int timeout = 10;
        try {
        	timeout = Integer.parseInt(noAnswerTimeout);
        } catch (NumberFormatException e) {
        	e.printStackTrace();
        }
        gather.setTimeout(timeout);
        try {
	        for (String prompt : prompts) {
	            gather.append(new Play(prompt));
	        }
        
        
            twiml.append(gather);
            Redirect redirect = new Redirect(getTimeoutUrl());
            redirect.setMethod("GET");
            twiml.append(redirect);
        } catch (TwiMLException e) {
            e.printStackTrace();
        }
        
        return twiml.toXML();
	}
	

   /* private String renderClosedQuestion(Question question, ArrayList<String> prompts, String sessionKey) {

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
    }*/
	
	protected String renderOpenQuestion(Question question,ArrayList<String> prompts,String sessionKey) {
		TwiMLResponse twiml = new TwiMLResponse();
		// TODO: Implement
    	return twiml.toXML();
	}
	
	/*protected String renderOpenQuestion(Question question,ArrayList<String> prompts,String sessionKey)
	{
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");

				// Check if media property type equals audio
				// if so record audio message, if not record dtmf input
				String typeProperty = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.TYPE );
				if(typeProperty!=null && typeProperty.equalsIgnoreCase("audio")) 
				{
				    renderVoiceMailQuestion( question, prompts, sessionKey, outputter );
				} 
				else 
				{
				    //see if a dtmf length is defined in the question
                    String dtmfMinLength = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.ANSWER_INPUT_MIN_LENGTH );
                    dtmfMinLength = dtmfMinLength != null ? dtmfMinLength : "";
                    String dtmfMaxLength = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.ANSWER_INPUT_MAX_LENGTH );
                    dtmfMaxLength = dtmfMaxLength != null ? dtmfMaxLength : "";
                    String noAnswerTimeout = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT );
                    String retryLimit = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.RETRY_LIMIT );
                    retryLimit = retryLimit != null ? retryLimit : String.valueOf(Question.DEFAULT_MAX_QUESTION_LOAD);
                    //assign a default timeout if one is not specified
                    noAnswerTimeout = noAnswerTimeout != null ? noAnswerTimeout : "5s";
                    if(!noAnswerTimeout.endsWith("s"))
                    {
                        log.warning("No answer timeout must end with 's'. E.g. 10s. Found: "+ noAnswerTimeout);
                        noAnswerTimeout += "s";
                    }
    				outputter.startTag("var");
    					outputter.attribute("name","answerInput");
    				outputter.endTag();
    				outputter.startTag("var");
    					outputter.attribute("name","questionId");
    					outputter.attribute("expr", "'"+question.getQuestion_id()+"'");
    				outputter.endTag();
    				outputter.startTag("var");
    					outputter.attribute("name","sessionKey");
    					outputter.attribute("expr", "'"+sessionKey+"'");
    				outputter.endTag();
    				outputter.startTag("form");
        				outputter.startTag( "property" );
                                        outputter.attribute( "name", "timeout" );
                                        outputter.attribute( "value", noAnswerTimeout );
                                outputter.endTag();
    				outputter.startTag("field");
    				        outputter.attribute("name", "answer");
    					outputter.startTag("grammar");
    					outputter.attribute("mode", "dtmf");
                                        outputter.attribute( "src", DTMFGRAMMAR + "?minlength=" + dtmfMinLength 
                                    + "&maxlength=" + dtmfMaxLength );
    							outputter.attribute("type", "application/srgs+xml");
    						outputter.endTag();
    						for (String prompt: prompts){
    							outputter.startTag("prompt");
    								outputter.startTag("audio");
    									outputter.attribute("src", prompt);
    								outputter.endTag();
    							outputter.endTag();
    						}
				outputter.startTag( "noinput" );
				    outputter.startTag( "goto" );
                                if ( question.getEventCallback("timeout") != null)
                                {
                                    outputter.attribute("next", "timeout?questionId=" + question.getQuestion_id() +
                                                                "&sessionKey=" + URLEncoder.encode(sessionKey, "UTF-8"));
                                }
                                else
                                {
                                    Integer retryCount = Question.getRetryCount( sessionKey );
                                    if ( retryCount < Integer.parseInt( retryLimit ) )
                                    {
                                        outputter.attribute( "next", "retry?questionId=" + question.getQuestion_id()
                                            + "&sessionKey=" + URLEncoder.encode(sessionKey, "UTF-8") );
                                        Question.updateRetryCount( sessionKey );
                                    }
                                    else
                                    {
                                        outputter.attribute("next", "retry?questionId=" + question.getQuestion_id());
                                        Question.flushRetryCount( sessionKey );
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
								outputter.attribute("namelist","answerInput questionId sessionKey");
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
		} catch (Exception e) {
			log.severe("Exception in creating open question XML: "+ e.toString());
		}		
		return sw.toString();
	}*/

	/** renders/updates the xml for recording an audio and posts it to the user on the callback 
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
        String uuid = UUID.randomUUID().toString();
        String filename = uuid + ".wav";
        String storedAudiofile = host + "download/" + filename;

        MyBlobStore store = new MyBlobStore();
        String uploadURL = store.createUploadUrl(filename, "/dialoghandler/rest/download/audio.vxml");

        outputter.startTag("form");
        outputter.attribute("id", "ComposeMessage");
            outputter.startTag("record");
                    outputter.attribute("name", "file");
                    outputter.attribute("beep", voiceMailBeep);
                    outputter.attribute("maxtime", voiceMessageLengthProperty);
                    outputter.attribute("dtmfterm", dtmfTerm);
                    //outputter.attribute("finalsilence", "3s");
                    for (String prompt : prompts){
                            outputter.startTag("prompt");
                                    outputter.attribute("timeout", "5s");
                                    outputter.startTag("audio");
                                            outputter.attribute("src", prompt);
                                    outputter.endTag();
                            outputter.endTag();
                    }
                    outputter.startTag("noinput");
                            for (String prompt : prompts){
                                    outputter.startTag("prompt");
                                            outputter.startTag("audio");
                                                    outputter.attribute("src", prompt);
                                            outputter.endTag();
                                    outputter.endTag();
                            }
                            /*outputter.startTag("goto");
                                    outputter.attribute("next", handleTimeoutURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
                            outputter.endTag();*/
                    outputter.endTag();
            outputter.endTag();
        
            outputter.startTag("subdialog");
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
                                    for (String prompt : prompts){
                                            outputter.startTag("prompt");
                                                    outputter.startTag("audio");
                                                            outputter.attribute("src", prompt);
                                                    outputter.endTag();
                                            outputter.endTag();
                                    }
                            outputter.endTag();
                    outputter.endTag();
	}
    
	private Response handleQuestion(Question question, AdapterConfig adapterConfig, String remoteID, String sessionKey) {

		String result = (new TwiMLResponse()).toXML();
		Return res = formQuestion(question, adapterConfig.getConfigId(),
				remoteID, null, sessionKey);
		if (question != null && !question.getType().equalsIgnoreCase("comment"))
			question = res.question;
		Session session = Session.getSession(sessionKey);
		// if the adapter is a trial adapter, add a introductory node
		log.info("question formed at handleQuestion is: "
				+ ServerUtils.serializeWithoutException(question));
		log.info("prompts formed at handleQuestion is: " + res.prompts);

		if (question != null) {
			question.generateIds();
			session.setQuestion(question);
			session.setRemoteAddress(remoteID);
			session.storeSession();

			// convert all text prompts to speech
			if (res.prompts != null) {
				String language = question.getPreferred_language()
						.contains("-") ? question.getPreferred_language()
						: "nl-nl";
				String ttsSpeedProperty = question.getMediaPropertyValue(
						MediumType.BROADSOFT, MediaPropertyKey.TSS_SPEED);
				ttsSpeedProperty = ttsSpeedProperty != null ? ttsSpeedProperty
						: "0";
				ArrayList<String> promptsCopy = new ArrayList<String>();
				for (String prompt : res.prompts) {
					if (!prompt.startsWith("dtmfKey://")) {
						if (!prompt.endsWith(".wav")) {
							promptsCopy.add(getTTSURL(prompt, language, "wav",
									ttsSpeedProperty, null));
						} else {
							promptsCopy.add(prompt);
						}
					}
				}
				res.prompts = promptsCopy;
			}

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
					log.info(String
							.format("current session key before referral is: %s and remoteId %s",
									sessionKey, remoteID));
					String redirectedId = PhoneNumberUtils.formatNumber(
							question.getUrl().replace("tel:", ""), null);
					if (redirectedId != null) {
						// update url with formatted redirecteId. RFC3966
						// returns format tel:<blabla> as expected
						question.setUrl(PhoneNumberUtils.formatNumber(
								redirectedId, PhoneNumberFormat.RFC3966));
						// store the remoteId as its lost while trying to
						// trigger the answered event
						HashMap<String, String> extras = new HashMap<String, String>();
						extras.put("referredCalledId", redirectedId);
						session.getExtras().putAll(extras);
						session.setQuestion(question);
						session.setRemoteAddress(remoteID);
						// create a new ddr record and session to catch the
						// redirect
						Session referralSession = Session.getOrCreateSession(
								adapterConfig, redirectedId);
						if (session.getDirection() != null) {
							DDRRecord ddrRecord = null;
							try {
								ddrRecord = DDRUtils
										.createDDRRecordOnOutgoingCommunication(
												adapterConfig, redirectedId, 1,
												question.getUrl());
								if (ddrRecord != null) {
									ddrRecord.addAdditionalInfo(
											Session.TRACKING_TOKEN_KEY,
											session.getTrackingToken());
									ddrRecord.createOrUpdate();
									referralSession.setDdrRecordId(ddrRecord
											.getId());
								}
							} catch (Exception e) {
								e.printStackTrace();
								log.severe(String.format(
										"Continuing without DDR. Error: %s",
										e.toString()));
							}
							referralSession
									.setDirection(session.getDirection());
							referralSession.setTrackingToken(session
									.getTrackingToken());
						}
						referralSession.setQuestion(session.getQuestion());
						referralSession.storeSession();
						session.storeSession();
					} else {
						log.severe(String.format(
								"Redirect address is invalid: %s. Ignoring.. ",
								question.getUrl().replace("tel:", "")));
					}
					result = renderComment(question, res.prompts, sessionKey);
				}
			} else if (res.prompts.size() > 0) {
				result = renderComment(question, res.prompts, sessionKey);
			}
		} else if (res.prompts.size() > 0) {
			result = renderComment(null, res.prompts, sessionKey);
		} else {
			log.info("Going to hangup? So clear Session?");
		}
		log.info("Sending xml: " + result);
		return Response.ok(result).build();
	}
	
	/**
     * returns the TTS URL from tts.ask-fast
     * 
     * @param textForSpeech
     * @param language
     * @param contentType
     * @return
     */
    private String getTTSURL( String textForSpeech, String language, String contentType, String speed, String format )
    {
        speed = (speed != null && !speed.isEmpty()) ? speed : "0"; 
        contentType = (contentType != null && !contentType.isEmpty()) ? contentType : "wav";
        format = (format != null && !format.isEmpty()) ? format : "8khz_8bit_mono";
        try
        {
            textForSpeech = URLEncoder.encode( textForSpeech.replace( "text://", "" ), "UTF-8").replace( "+", "%20" );
        }
        catch ( UnsupportedEncodingException e )
        {
            e.printStackTrace();
            log.severe( e.getLocalizedMessage() );
        }
        return "http://tts.ask-fast.com/api/parse?text=" + textForSpeech + "&lang=" + language
            + "&codec=" + contentType + "&speed=" + speed + "&format=" + format + "&type=.wav";
    }
    
    protected String getAnswerUrl() {
        return "http://"+Settings.HOST+"/dialoghandler/rest/twilio/answer";
    }
    
    protected String getTimeoutUrl() {
    	return "http://"+Settings.HOST+"/dialoghandler/rest/twilio/timeout";
    }
}
