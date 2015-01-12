package com.almende.dialog.adapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.znerd.xmlenc.XMLOutputter;

import com.almende.dialog.LogLevel;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Broadsoft;
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
import com.almende.util.myBlobstore.MyBlobStore;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;

@Path("/vxml/")
public class VoiceXMLRESTProxy {
	protected static final Logger log = Logger.getLogger(VoiceXMLRESTProxy.class.getName());
	protected static final com.almende.dialog.Logger dialogLog =  new com.almende.dialog.Logger();
	private static final int LOOP_DETECTION=10;
	private static final String DTMFGRAMMAR="dtmf2hash";
	private static final String PLAY_TRIAL_AUDIO_KEY = "playTrialAccountAudio";
	private static final int MAX_RETRIES=1;
	protected String TIMEOUT_URL="timeout";
	protected String EXCEPTION_URL="exception";
	private String host = "";
	
	public static void killSession(Session session){
		
		AdapterConfig config = session.getAdapterConfig();
		if(config!=null) {
			Broadsoft bs = new Broadsoft(config);
			bs.endCall(session.getExternalSession());
		}
	}
	
    /**
     * @Deprecated. Use broadcast calling mechanism instead. <br>
     *              {@link VoiceXMLRESTProxy#dial(Map, String, String, AdapterConfig)
     *              dial} method
     * 
     * @param formattedAddress
     * @param url
     * @param config
     * @return
     */
    @Deprecated
    public static String dial(String address, String url, AdapterConfig config, String accountId) {

        String formattedAddress = PhoneNumberUtils.formatNumber(address, PhoneNumberFormat.E164);

        if (formattedAddress != null) {
            //avoid multiple calls to be made to the same number, from the same adapter. 
            Session session = Session.getSession(Session.getSessionKey(config, formattedAddress));
            if (session != null) {
                String responseMessage = checkIfCallAlreadyInSession(formattedAddress, config, session);
                if (responseMessage != null) {
                    return responseMessage;
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
            session.killed = false;
            session.setStartUrl(url);
            session.setDirection("outbound");
            session.setRemoteAddress(formattedAddress);
            session.setType(AdapterAgent.ADAPTER_TYPE_BROADSOFT);
            session.setAccountId(accountId);
            session.storeSession();
            Question question = Question.fromURL(url, config.getConfigId(), formattedAddress, config.getMyAddress(),
                                                 session.getDdrRecordId(), session.getKey());
            session.setQuestion(question);
            session.storeSession();

            dialogLog.log(LogLevel.INFO, config,
                          String.format("Call started from: %s to: %s", config.getMyAddress(), formattedAddress),
                          session);
            Broadsoft bs = new Broadsoft(config);
            bs.startSubscription();

            String extSession = bs.startCall(formattedAddress + "@outbound", session);
            //create a ddrRecord
            try {
                DDRRecord ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(config, accountId,
                                                                                      formattedAddress, 1, url);
                if (ddrRecord != null) {
                    session.setDdrRecordId(ddrRecord.getId());
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("DDR creation failed for session: %s. Reason: %s", session.getKey(),
                                         e.getMessage()));
            }
            session.setExternalSession(extSession);
            session.storeSession();

            return session.getKey();
        }
        else {
            log.severe(String.format("To address is invalid: %s. Ignoring.. ", formattedAddress));
            return address + ": Invalid address";
        }
    }

    /**
     * Initiates a call to all the numbers in the addressNameMap and returns a
     * Map of <adress, SessionKey>
     * 
     * @param addressNameMap Map with address (e.g. phonenumber or email) as Key and name
    *            as value. The name is useful for email and not used for SMS etc
     * @param url
     *            The URL on which a GET HTTPRequest is performed and expected a
     *            question JSON
     * @param config the adapterConfig which is used to perform this broadcast
     * @param accountId AccoundId initiating this broadcast. All costs are applied to this accountId
     * @return
     * @throws Exception
     */
    public static HashMap<String, String> dial(Map<String, String> addressNameMap, String url, AdapterConfig config,
        String accountId) throws Exception {

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
                session.killed = false;
                session.setStartUrl(url);
                session.setDirection("outbound");
                session.setRemoteAddress(formattedAddress);
                session.setType(AdapterAgent.ADAPTER_TYPE_BROADSOFT);
                session.setAdapterID(config.getConfigId());
                session.setQuestion(question);
                session.setAccountId(accountId);
                session.storeSession();
                dialogLog.log(LogLevel.INFO, session.getAdapterConfig(), String
                                                .format("Outgoing call requested from: %s to: %s",
                                                        session.getLocalAddress(), formattedAddress), session);
                String extSession = "";
                if (!ServerUtils.isInUnitTestingEnvironment()) {
                    Broadsoft bs = new Broadsoft(config);
                    String subscriptiion = bs.startSubscription();
                    log.info(String.format("Calling subscription complete. Message: %s. Starting call.. ",
                                           subscriptiion));
                    extSession = bs.startCall(formattedAddress + "@outbound", session);
                }
                //create a ddrRecord
                try {
                    DDRRecord ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(config, accountId,
                                                                                          formattedAddress, 1, url);
                    if (ddrRecord != null) {
                        session.setDdrRecordId(ddrRecord.getId());
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                    log.severe(String.format("DDR creation failed for session: %s. Reason: %s", session.getKey(),
                                             e.getMessage()));
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
	    String repeat = minLength.equals( maxLength ) ? minLength : (minLength + "-" +  maxLength);
		String result = "<?xml version=\"1.0\"?> "+
						"<grammar mode=\"dtmf\" version=\"1.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.w3.org/2001/06/grammar http://www.w3.org/TR/speech-grammar/grammar.xsd\" xmlns=\"http://www.w3.org/2001/06/grammar\"  root=\"untilHash\" > "+
							"<rule id=\"digit\"> "+
								"<one-of> "+
									"<item> 0 </item> "+
									"<item> 1 </item> "+
									"<item> 2 </item> "+
									"<item> 3 </item> "+
									"<item> 4 </item> "+
									"<item> 5 </item> "+
									"<item> 6 </item> "+
									"<item> 7 </item> "+
									"<item> 8 </item> "+
									"<item> 9 </item> "+
									"<item> * </item> "+
								"</one-of> "+
							"</rule> "+
							"<rule id=\"untilHash\" scope=\"public\"> "+
								"<one-of> "+
									"<item repeat=\"" + repeat + "\"><ruleref uri=\"#digit\"/></item> "+
									"<item> # </item> "+
								"</one-of> "+
							"</rule> "+
						"</grammar> ";
		return Response.ok(result).build();
	}
	
    @Path("new")
    @GET
    @Produces("application/voicexml")
    public Response getNewDialog(@QueryParam("direction") String direction,
                                 @QueryParam("remoteID") String remoteID,
                                 @QueryParam("externalRemoteID") String externalRemoteID,
                                 @QueryParam("localID") String localID,
                                 @Context UriInfo ui)
    {
        log.info("call started:"+direction+":"+remoteID+":"+localID);
        this.host=ui.getBaseUri().toString().replace(":80/", "/");
        
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_BROADSOFT, localID);
        String formattedRemoteId = remoteID;
        //format the remote number
        formattedRemoteId = PhoneNumberUtils.formatNumber(remoteID.split("@")[0], PhoneNumberFormat.E164);
        externalRemoteID = PhoneNumberUtils.formatNumber(externalRemoteID.split("@")[0], PhoneNumberFormat.E164);

        if (formattedRemoteId == null) {
            log.severe(String.format("RemoveId address is invalid: %s. Ignoring.. ", remoteID));
            return Response.ok().build();
        }
            
        String sessionKey = AdapterAgent.ADAPTER_TYPE_BROADSOFT+"|"+localID+"|"+ formattedRemoteId;
        Session session = Session.getSession(sessionKey);
        
        String url = "";
        if ( session != null && direction.equalsIgnoreCase("outbound")) {
                url = session.getStartUrl();
            dialogLog.log(LogLevel.INFO, config, String
                                            .format("Trying to fetch dialog for %s, due to outgoing Call from: %s ",
                                                    formattedRemoteId, config.getMyAddress()), session);
        }
        else if(direction.equals("inbound")) {
            //create a session for incoming only
            //create a session for incoming only. Flush any existing one
            if (session != null) {
                session.drop();
            }
            session = Session.createSession(config, formattedRemoteId);
            session.setAccountId(config.getOwner());
            session.setRemoteAddress( externalRemoteID );
            session.storeSession();
            url = config.getURLForInboundScenario();
            Broadsoft bs = new Broadsoft( config );
            bs.startSubscription();
            dialogLog.log(LogLevel.INFO, config,
                          String.format("Incoming Call received from: %s at: %s", formattedRemoteId, config.getMyAddress()),
                          session);
        }
        
        if(session != null) {
            session.setStartUrl( url );
            session.setDirection( direction );
            session.setRemoteAddress( externalRemoteID );
            session.setType( AdapterAgent.ADAPTER_TYPE_BROADSOFT );
            session.setAdapterID( config.getConfigId() );
        }
        else {
            log.severe(String.format("Session %s not found", sessionKey));
            return null;
        }
        
        Question question = session.getQuestion();
        if(question == null) {
            question = Question.fromURL(url, session.getAdapterConfig().getConfigId(), externalRemoteID, localID,
                                        session.getDdrRecordId(), session.getKey());
        }
        // Check if we were able to load a question
        if(question==null) {
            //If not load a default error message
            question = Question.getError( config.getPreferred_language() );
        }
        session.setQuestion(question);
        
        log.info("Current session info: "+ ServerUtils.serializeWithoutException(session));
        
        if (session.getQuestion() != null) {
            //play trial account audio if the account is trial
            if(config.getAccountType() != null && config.getAccountType().equals(AccountType.TRIAL)){
                session.getExtras().put(PLAY_TRIAL_AUDIO_KEY, "true");
            }
            //create ddr record
            DDRRecord ddrRecord = null;
            try {
                if (direction.equalsIgnoreCase("outbound")) {
                    if (session.getDdrRecordId() == null) {
                        ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(config, session.getAccountId(),
                                                                                    formattedRemoteId, 1, url);
                    }
                    else {
                        //create a new ddr record only if it is missing
                        ddrRecord = DDRRecord.getDDRRecord(session.getDdrRecordId(), session.getAccountId());
                        if(ddrRecord == null) {
                            ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(config, session.getAccountId(),
                                                                                        formattedRemoteId, 1, url);
                        }
                    }
                }
                else {
                    ddrRecord = DDRUtils.createDDRRecordOnIncomingCommunication(config, session.getAccountId(),
                                                                                formattedRemoteId, 1, url);
                }
                session.setDdrRecordId( ddrRecord != null ? ddrRecord.getId() : null);
                if (ddrRecord != null) {
                    log.info(String.format("For session: %s, a new DDRRecord is created: %s", sessionKey,
                                           ServerUtils.serializeWithoutException(ddrRecord)));
                    ddrRecord.addAdditionalInfo(Session.TRACKING_TOKEN_KEY, session.getTrackingToken());
                }
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
                if (ddrRecord != null) {
                    ddrRecord.createOrUpdate();
                }
                else {
                    log.severe("DDRRecord not found. Not expected to be null here!!");
                }
                if(session != null) {
                    session.storeSession();
                    //refetch the session and make sure the ddr is attached
                    session = Session.getSession(session.getKey());
                    if(session != null) {
                        if(session.getDdrRecordId() != null) {
                            log.info(String.format("Session: %s updated with ddrRecord: %s for direction: %s",
                                                   session.getKey(), session.getDdrRecordId(), direction));
                        }
                        else {
                            log.severe(String.format("Session: %s updated with no ddrRecord for direction: %s",
                                                     session.getKey(), direction));
                        }
                    }
                    else {
                        log.severe(String.format("Session not found for: %s after trying to attach ddrRecord for direction: %s",
                                                 sessionKey, direction));
                    }
                }
                else {
                    log.severe("Session not found. Not expected to be null here!!");
                }
            }
            return handleQuestion( question, config, externalRemoteID, sessionKey );
        }
        else {
            return Response.ok().build();
        }
    }
	
    @Path( "answer" )
    @GET
    @Produces( "application/voicexml+xml" )
    public Response answer( @QueryParam( "questionId" ) String question_id,
        @QueryParam( "answerId" ) String answer_id, @QueryParam( "answerInput" ) String answer_input,
        @QueryParam( "sessionKey" ) String sessionKey, @Context UriInfo ui )
    {
        try
        {
            answer_input = answer_input != null ? URLDecoder.decode( answer_input, "UTF-8" ) : answer_input;
        }
        catch ( UnsupportedEncodingException e )
        {
            log.warning( String.format( "Answer input decode failed for: %s", answer_input) );
        }
        this.host = ui.getBaseUri().toString().replace( ":80", "" );
        String reply = "<vxml><exit/></vxml>";
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
        return Response.ok( reply ).build();
    }
	
    @Path( "timeout" )
    @GET
    @Produces( "application/voicexml+xml" )
    public Response timeout( @QueryParam( "questionId" ) String question_id,
        @QueryParam( "sessionKey" ) String sessionKey ) throws Exception
    {
        String reply = "<vxml><exit/></vxml>";
        Session session = Session.getSession( sessionKey );
        if ( session != null )
        {
            Question question = session.getQuestion();
            String responder = session.getRemoteAddress();
            if ( session.killed )
            {
                return Response.status( Response.Status.BAD_REQUEST ).build();
            }
            dialogLog.log(LogLevel.INFO,
                          session.getAdapterConfig(),
                          String.format("Timeout from: %s for question: %s", responder,
                                        question.getQuestion_expandedtext()), session);
            HashMap<String,Object> extras = new HashMap<String, Object>();
            extras.put( "sessionKey", sessionKey );
            extras.put("requester", session.getLocalAddress());
            question = question.event( "timeout", "No answer received", extras, responder );
            session.setQuestion( question );
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
                log.warning("No question found for this session :"+ sessionKey);
            }
            session.storeSession();
            return handleQuestion( question, session.getAdapterConfig(), responder, sessionKey );
        }
        else {
            log.warning("Strange that no session is found for: "+ sessionKey);
        }
        return Response.ok( reply ).build();
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
                                        question.getQuestion_expandedtext()), session);

            HashMap<String, String> extras = new HashMap<String, String>();
            extras.put("sessionKey", sessionKey);
            extras.put("requester", session.getLocalAddress());
            question = question.event("exception", "Wrong answer received", extras, responder);
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
     * used to trigger answered event unlike {@link VoiceXMLRESTProxy#answer(String, String, String, String, UriInfo)}
     * @return
     * @throws Exception 
     */
    public Response answered( String direction, String remoteID, String localID, String startTime,
        String answerTime) throws Exception
    {
        log.info( "call answered with:" + direction + "_" + remoteID + "_" + localID );
        String sessionKey = AdapterAgent.ADAPTER_TYPE_BROADSOFT+"|"+localID+"|"+remoteID.split( "@outbound" )[0]; //ignore the @outbound suffix
        Session session = Session.getSession(sessionKey);
        //for direction = transfer (redirect event), json should not be null        
        //make sure that the answered call is not triggered twice
        if (session != null && session.getQuestion() != null && !isEventTriggered("answered", session)) {
            String responder = session.getRemoteAddress();
            String referredCalledId = session.getExtras().get("referredCalledId");
            HashMap<String, Object> timeMap = getTimeMap(startTime, answerTime, null);
            timeMap.put("referredCalledId", referredCalledId);
            timeMap.put("sessionKey", sessionKey);
            timeMap.put("requester", session.getLocalAddress());
            session.getQuestion().event("answered", "Answered", timeMap, responder);
            dialogLog.log(LogLevel.INFO, session.getAdapterConfig(),
                          String.format("Call from: %s answered by: %s", session.getLocalAddress(), responder), session);
        }
        return Response.ok( "" ).build();
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
            
            if(!config.getXsiSubscription().equals( subscriptionId )) {
                log.warning("Ignoring because of subscriptionId: "+ subscriptionId + " doesn't match :" + config.getXsiSubscription());
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
                            else if (rpChild.getNodeName().equals("callType")) {
                                type = rpChild.getTextContent();
                            }
                        }

                        fullAddress = new String(address);
                        // Check if session can be matched to call
                        if (type.equals("Network") || type.equals("Group") || type.equals("Unknown")) {

                            address = address.replace("tel:", "").replace("sip:", "");

                            log.info("Going to format phone number: " + address);
                            String[] addressArray = address.split("@");
                            address = PhoneNumberUtils.formatNumber(addressArray[0], null);
                            String formattedAddress = address != null ? new String(address) : addressArray[0];
                            if (address != null) {
                                if (addressArray.length > 1) {
                                    address += "@" + addressArray[1];
                                }
                                
                                String sessionKey = AdapterAgent.ADAPTER_TYPE_BROADSOFT + "|" + config.getMyAddress() +
                                "|" + formattedAddress;
                                
                                Session session = null;
                                // if formattedAddress is empty (probably anonymous caller)
                                // (Expensive query)
                                if(formattedAddress.isEmpty()) {
                                    List<Session> sessions = Session.findSessionByLocalAndRemoteAddress( config.getMyAddress(), formattedAddress );
                                    if(sessions.size() == 1) {
                                        session = sessions.get(0);
                                    }
                                } else {
                                    // find the session based on the active call on the 
                                    session = Session.getSession(sessionKey);
                                }
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
                                                 !releaseCause.getTextContent()
                                                                                 .equalsIgnoreCase("Temporarily Unavailable") && !releaseCause
                                                                                .getTextContent()
                                                                                .equalsIgnoreCase("User Not Found"))) {
                                                session.setDirection(direction);
                                                session.setAnswerTimestamp(answerTimeString);
                                                session.setStartTimestamp(startTimeString);
                                                if (session.getQuestion() == null) {
                                                    Question questionFromIncomingCall = Session
                                                                                    .getQuestionFromDifferentSession(config.getConfigId(),
                                                                                                                     "inbound",
                                                                                                                     "referredCalledId",
                                                                                                                     session.getRemoteAddress());
                                                    if (questionFromIncomingCall != null) {
                                                        session.setQuestion(questionFromIncomingCall);
                                                        session.storeSession();
                                                    }
                                                }
                                                session.storeSession();
                                                answered(direction, address, config.getMyAddress(), startTimeString,
                                                         answerTimeString);
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
                                        if (releaseCause.getTextContent().equals("Server Failure")) {
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
                                        else if (releaseCause.getTextContent().equals("Request Failure")) {
                                            log.severe("Restart call?? ReleaseCause: " + releaseCause.getTextContent());
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
                                                if (session.getAnswerTimestamp() == null &&
                                                    session.getStartTimestamp() != null &&
                                                    session.getReleaseTimestamp() != null) {
                                                    callReleased = true;
                                                }
                                                log.info(String.format("Probably a disconnect of a sip. %s hangup event",
                                                                       callReleased ? "calling" : "not calling"));
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
                                            String answerTimestamp = session.getAnswerTimestamp();
                                            answerTimeString = (answerTimestamp != null && answerTimeString == null) ? answerTimestamp
                                                                                                                    : answerTimeString;
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
                                            if (DDRUtils.stopDDRCosts(session.getKey(), true)) {
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
                log.info("EventName: "+eventName.getTextContent());
                if (eventName != null && eventName.getTextContent().equals("SubscriptionTerminatedEvent")) {

                    Broadsoft bs = new Broadsoft(config);
                    String newId = bs.restartSubscription(subscriptionId);
                    if(newId!=null) {
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
     * @param textForSpeech actually text that has to be spoken 
     * @param language format "language-country" check the full link at {@link http://www.voicerss.org/api/documentation.aspx VoiceRSS}
     * @param contentType file format
     * @param speed -10 to 10
     * @param format audio formats
     * @param req
     * @param resp
     */
    @GET
    @Path( "tts/{textForSpeech}" )
    public Response redirectToSpeechEngine( @PathParam( "textForSpeech" ) String textForSpeech,
        @QueryParam( "hl" ) @DefaultValue( "nl-nl" ) String language,
        @QueryParam( "c" ) @DefaultValue( "wav" ) String contentType,
        @QueryParam( "r" ) @DefaultValue( "0" ) String speed,
        @QueryParam( "f" ) @DefaultValue( "8khz_8bit_mono" ) String format,
        @Context HttpServletRequest req,
        @Context HttpServletResponse resp ) throws IOException, URISyntaxException
    {
        String ttsURL = getTTSURL( textForSpeech, language, contentType, speed, format );
        return Response.seeOther( new URI( ttsURL ) ).build();
    }

    /**
     * simple endpoint for repeating a question based on its session and question id
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
	
	protected String renderOpenQuestion(Question question,ArrayList<String> prompts,String sessionKey)
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
//                                        Question.updateRetryCount( sessionKey );
                                    }
                                    else
                                    {
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
		        e.printStackTrace();
			log.severe("Exception in creating open question XML: "+ e.toString());
		}		
		return sw.toString();
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
    
    protected String renderExitQuestion(Question question, ArrayList<String> prompts, String sessionKey) {
	    
    	StringWriter sw = new StringWriter();
    	try {
	    	XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				outputter.startTag("form");
			    	outputter.startTag("block");
					for (String prompt : prompts){
						outputter.startTag("prompt");
							outputter.startTag("audio");
								outputter.attribute("src", prompt);
							outputter.endTag();
						outputter.endTag();
					}
						outputter.startTag("exit");
						outputter.endTag();
					
					outputter.endTag();
					outputter.endTag();
				outputter.endTag();
			outputter.endDocument();
		} catch (Exception e) {
			log.severe("Exception in creating question XML: "+ e.toString());
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
        if (session != null && "true".equals(session.getExtras().get(PLAY_TRIAL_AUDIO_KEY))) {
            res.prompts = res.prompts != null ? res.prompts : new ArrayList<String>();
            String trialAudioURL = getTrialAudioURL(question.getPreferred_language());
            res.prompts.add(0, trialAudioURL);
            session.getExtras().put(PLAY_TRIAL_AUDIO_KEY, "false");
        }
        log.info("question formed at handleQuestion is: " + ServerUtils.serializeWithoutException(question));
        log.info("prompts formed at handleQuestion is: " + res.prompts);

        if (question != null) {
            question.generateIds();
            session.setQuestion(question);
            session.setRemoteAddress(remoteID);
            session.storeSession();

            //convert all text prompts to speech 
            if (res.prompts != null) {
                String language = question.getPreferred_language().contains("-") ? question.getPreferred_language()
                                                                                : "nl-nl";
                String ttsSpeedProperty = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                         MediaPropertyKey.TSS_SPEED);
                ttsSpeedProperty = ttsSpeedProperty != null ? ttsSpeedProperty : "0";
                ArrayList<String> promptsCopy = new ArrayList<String>();
                for (String prompt : res.prompts) {
                    if (!prompt.startsWith("dtmfKey://")) {
                        if (!prompt.endsWith(".wav")) {
                            promptsCopy.add(getTTSURL(prompt, language, "wav", ttsSpeedProperty, null));
                        }
                        else {
                            promptsCopy.add(prompt);
                        }
                    }
                }
                res.prompts = promptsCopy;
            }

            if (question.getType().equalsIgnoreCase("closed")) {
                result = renderClosedQuestion(question, res.prompts, sessionKey);
            }
            else if (question.getType().equalsIgnoreCase("open")) {
                result = renderOpenQuestion(question, res.prompts, sessionKey);
            }
            else if (question.getType().equalsIgnoreCase("referral")) {
                if (question.getUrl() != null && question.getUrl().startsWith("tel:")) {
                    // added for release0.4.2 to store the question in the session,
                    //for triggering an answered event
                    log.info(String.format("current session key before referral is: %s and remoteId %s", sessionKey,
                                           remoteID));
                    String redirectedId = PhoneNumberUtils.formatNumber(question.getUrl().replace("tel:", ""), null);
                    if (redirectedId != null) {
                        //update url with formatted redirecteId. RFC3966 returns format tel:<blabla> as expected
                        question.setUrl(PhoneNumberUtils.formatNumber(redirectedId, PhoneNumberFormat.RFC3966));
                        //store the remoteId as its lost while trying to trigger the answered event
                        HashMap<String, String> extras = new HashMap<String, String>();
                        extras.put("referredCalledId", redirectedId);
                        session.getExtras().putAll(extras);
                        session.setQuestion(question);
                        session.setRemoteAddress(remoteID);
                        //create a new ddr record and session to catch the redirect
                        Session referralSession = Session.createSession(adapterConfig, redirectedId);
                        referralSession.setAccountId(session.getAccountId());
                        referralSession.storeSession();
                        if (session.getDirection() != null) {
                            DDRRecord ddrRecord = null;
                            try {
                                ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig,
                                                                                            referralSession.getAccountId(),
                                                                                            redirectedId, 1,
                                                                                            question.getUrl());
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
                        referralSession.storeSession();
                        session.storeSession();
                    }
                    else {
                        log.severe(String.format("Redirect address is invalid: %s. Ignoring.. ", question.getUrl()
                                                        .replace("tel:", "")));
                    }
                    result = renderComment(question, res.prompts, sessionKey);
                }
            }
            else if (question.getType().equalsIgnoreCase("exit")) {
            	result = renderExitQuestion(question, res.prompts, sessionKey);
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
	
    protected String getAnswerUrl() {

        return "answer";
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
    
    /**
     * returns the baseURL for the audio of the trial account 
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
        if (config != null) {
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
     * @param eventName
     * @param session
     * @return
     */
    private static boolean isEventTriggered(String eventName, Session session) {

        if (session != null) {
            if (session.getExtras().get("event_" + eventName) != null) {
                log.warning(String.format("%s event already triggered before for this session: %s at: %s", eventName,
                                          session.getKey(),
                                          TimeUtils.getStringFormatFromDateTime(Long.parseLong(session.getExtras()
                                                                          .get("event_" + eventName)), null)));
                return true;
            }
            else {
                session.getExtras().put("event_" + eventName, String.valueOf(TimeUtils.getServerCurrentTimeInMillis()));
                session.storeSession();
            }
        }
        return false;
    }
}
