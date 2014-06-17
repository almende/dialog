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
import com.almende.dialog.DDRWrapper;
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
import com.almende.dialog.util.PhoneNumberUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.myBlobstore.MyBlobStore;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;

@Path("/vxml/")
public class VoiceXMLRESTProxy {
	protected static final Logger log = Logger.getLogger(VoiceXMLRESTProxy.class.getName());
	protected static final com.almende.dialog.Logger dialogLog =  new com.almende.dialog.Logger();
	private static final int LOOP_DETECTION=10;
	private static final String DTMFGRAMMAR="dtmf2hash";
	
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
     * @param address
     * @param url
     * @param config
     * @return
     */
    @Deprecated
    public static String dial(String address, String url, AdapterConfig config) {

        try {
            address = PhoneNumberUtils.formatNumber(address, PhoneNumberFormat.E164);
        }
        catch (Exception e) {
            log.severe(String.format("Phonenumber: %s is not valid", address));
            return "";
        }

        Session session = Session.getOrCreateSession(config, address);
        session.killed = false;
        session.setStartUrl(url);
        session.setDirection("outbound");
        session.setRemoteAddress(address);
        session.setType(AdapterAgent.ADAPTER_TYPE_BROADSOFT);
        session.setTrackingToken(UUID.randomUUID().toString());

        Question question = Question.fromURL(url, config.getConfigId(), address, config.getMyAddress());
        session.setQuestion(question);
        session.storeSession();

        DDRWrapper.log(url, session.getTrackingToken(), session, "Dial", config);

        Broadsoft bs = new Broadsoft(config);
        bs.startSubscription();

        String extSession = bs.startCall(address + "@outbound");

        session.setExternalSession(extSession);
        session.storeSession();

        return session.getKey();
    }

    /**
     * initiates a call to all the numbers in the addressNameMap and returns a
     * Map of <adress, SessionKey>
     * @return
     */
    public static HashMap<String, String> dial( Map<String, String> addressNameMap, String url, String senderName, AdapterConfig config )
    throws Exception
    {
        HashMap<String, String> resultSessionMap = new HashMap<String, String>();
        // If it is a broadcast don't provide the remote address because it is deceiving.
        String loadAddress = "";
        if(addressNameMap.size()==1)
            loadAddress = addressNameMap.keySet().iterator().next();

        //fetch the question
        Question question = Question.fromURL( url, config.getConfigId(), loadAddress, config.getMyAddress() );

        for ( String address : addressNameMap.keySet() )
        {
            try
            {
                String formattedAddress = PhoneNumberUtils.formatNumber( address, PhoneNumberFormat.E164 );
                Session session = Session.getOrCreateSession( config, formattedAddress );
                session.killed=false;
                session.setStartUrl(url);
                session.setDirection("outbound");
                session.setRemoteAddress(formattedAddress);
                session.setType(AdapterAgent.ADAPTER_TYPE_BROADSOFT);
                session.setTrackingToken(UUID.randomUUID().toString());
                session.setAdapterID(config.getConfigId());
                session.setQuestion( question );
                DDRWrapper.log(url,session.getTrackingToken(),session,"Dial",config);

                String extSession = "";
                if ( !ServerUtils.isInUnitTestingEnvironment() )
                {
                    Broadsoft bs = new Broadsoft( config );
                    bs.startSubscription();
                    extSession = bs.startCall( formattedAddress + "@outbound" );
                }
                session.setExternalSession( extSession );
                session.storeSession();
                resultSessionMap.put( formattedAddress, session.getKey() );
            }
            catch ( Exception e )
            {
                log.severe( String.format( "Phonenumber: %s is not valid", address ) );
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
    public Response getNewDialog(@QueryParam("direction") String direction,@QueryParam("remoteID") String remoteID,@QueryParam("localID") String localID, @Context UriInfo ui)
    {
        log.info("call started:"+direction+":"+remoteID+":"+localID);
        this.host=ui.getBaseUri().toString().replace(":80", "");
        
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_BROADSOFT, localID);
        String formattedRemoteId = remoteID;
        //format the remote number
        try
        {
            formattedRemoteId = PhoneNumberUtils.formatNumber( remoteID.split( "@" )[0], PhoneNumberFormat.E164 );
        }
        catch ( Exception e1 )
        {
            log.severe( "Remote number formatting failed: "+ remoteID.split( "@" )[0] );
        }
        String sessionKey = AdapterAgent.ADAPTER_TYPE_BROADSOFT+"|"+localID+"|"+ formattedRemoteId;
        Session session = Session.getSession(sessionKey);
        
        String url = "";
        if ( session != null && direction.equalsIgnoreCase("outbound")) {
                url = session.getStartUrl();
        }
        else if(direction.equals("inbound")) {
            //create a session for incoming only
            session = Session.getOrCreateSession(config, formattedRemoteId);
            url = config.getURLForInboundScenario();
            Broadsoft bs = new Broadsoft( config );
            bs.startSubscription();
        }
        
        if(session != null) {
            session.setStartUrl( url );
            session.setDirection( direction );
            session.setRemoteAddress( remoteID );
            session.setType( AdapterAgent.ADAPTER_TYPE_BROADSOFT );
            session.setAccountId( config.getOwner() );
            session.setTrackingToken( UUID.randomUUID().toString() );
            session.setAdapterID( config.getConfigId() );
        }
        else {
            log.severe(String.format("Session %s not found", sessionKey));
            return null;
        }
        
        Question question = session.getQuestion();
        if(question == null) {
            question = Question.fromURL(url,session.getAdapterConfig().getConfigId(),remoteID,localID);
            session.setQuestion( question );
        }
        DDRWrapper.log(question,session,"Start",config);
        
        if (session.getQuestion() != null) {
            //create ddr record
            try {
                DDRRecord ddrRecord = null;
                if (direction.equalsIgnoreCase("outbound")) {
                    ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(config, remoteID, 1);
                }
                else {
                    ddrRecord = DDRUtils.createDDRRecordOnIncomingCommunication(config, remoteID, 1);
                }
                session.setDdrRecordId(ddrRecord.getId());
            }
            catch (Exception e) {
                String errorMessage = String.format("Creating DDR records failed. Direction: %s for adapterId: %s with address: %s remoteId: %s and localId: %s",
                                                    direction, config.getConfigId(), config.getMyAddress(), remoteID,
                                                    localID);
                log.severe(errorMessage);
                dialogLog.severe(config.getConfigId(), errorMessage);
            }
            finally {
                session.storeSession();
            }
            return handleQuestion( question, config.getConfigId(), remoteID, sessionKey );
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
                DDRWrapper.log( question, session, "Answer" );
                question = question.answer( responder, session.getAdapterConfig().getConfigId(), answer_id,
                    answer_input, sessionKey );
                //reload the session
                session = Session.getSession( sessionKey );
                session.setQuestion( question );
                session.storeSession();
                return handleQuestion( question, session.getAdapterConfig().getConfigId(), responder, sessionKey );
            } else {
                log.warning( "No question found in session!" );
            }
        }
        else
        {
            log.warning( "No session found for: " + sessionKey );
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
            DDRWrapper.log( question, session, "Timeout" );
            HashMap<String,Object> extras = new HashMap<String, Object>();
            extras.put( "sessionKey", sessionKey );
            question = question.event( "timeout", "No answer received", extras, responder );
            session.setQuestion( question );
            session.storeSession();
            return handleQuestion( question, session.getAdapterConfig().getConfigId(), responder, sessionKey );
        }
        return Response.ok( reply ).build();
    }
	
	@Path("exception")
	@GET
	@Produces("application/voicexml+xml")
	public Response exception(@QueryParam("questionId") String question_id, @QueryParam("sessionKey") String sessionKey){
		String reply="<vxml><exit/></vxml>";
		Session session = Session.getSession( sessionKey );
		if (session != null && session.getQuestion() != null){
			Question question = session.getQuestion();
			String responder = session.getRemoteAddress();
			
			if (session.killed){
				return Response.status(Response.Status.BAD_REQUEST).build();
			}
			DDRWrapper.log(question,session,"Timeout");

			HashMap<String, String> extras = new HashMap<String, String>();
			extras.put( "sessionKey", sessionKey );
			question = question.event("exception", "Wrong answer received", extras, responder);
			//reload the session
			session = Session.getSession( sessionKey );
			session.setQuestion( question );
			session.storeSession();
			return handleQuestion(question,session.getAdapterID(),responder,sessionKey);
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
                                                     session.getRemoteAddress(), session.getLocalAddress());
                session.setQuestion(question);
            }
            if (session.getQuestion() != null) {
                HashMap<String, Object> timeMap = getTimeMap(session.getStartTimestamp(), session.getAnswerTimestamp(),
                                                             session.getReleaseTimestamp());
                timeMap.put("referredCalledId", session.getExtras().get("referredCalledId"));
                timeMap.put("sessionKey", session.getKey());
                handleQuestion(null, session.getAdapterConfig().getConfigId(), session.getRemoteAddress(),
                               session.getKey());
                session.getQuestion().event("hangup", "Hangup", session.getExtras(), session.getRemoteAddress());
                DDRWrapper.log(session.getQuestion(), session, "Hangup");
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
        String sessionKey = AdapterAgent.ADAPTER_TYPE_BROADSOFT+"|"+localID+"|"+remoteID.split( "@" )[0]; //ignore the @outbound suffix
        Session session = Session.getSession(sessionKey);
        //for direction = transfer (redirect event), json should not be null        
        if ( session != null && session.getQuestion() != null)
        {
            Question question = session.getQuestion();
            String responder = session.getRemoteAddress();
            String referredCalledId = session.getExtras().get( "referredCalledId" );
            HashMap<String, Object> timeMap = getTimeMap( startTime, answerTime, null );
            timeMap.put( "referredCalledId", referredCalledId );
            timeMap.put( "sessionKey", sessionKey );
            question.event( "answered", "Answered", timeMap, responder );
            DDRWrapper.log( question, session, "Answered" );
        }
        return Response.ok( "" ).build();
    }
	
    @Path("cc")
    @POST
    public Response receiveCCMessage(String xml) {
        
        log.info("Received cc: "+xml);
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            
            Document dom = db.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
            Node subscriberId = dom.getElementsByTagName("subscriberId").item(0);
            
            AdapterConfig config = AdapterConfig.findAdapterConfigByUsername(subscriberId.getTextContent());
            
            Node eventData = dom.getElementsByTagName("eventData").item(0);
            // check if incall event
            if(eventData.getChildNodes().getLength()>1) {
                Node call = eventData.getChildNodes().item(1);
                Node personality = null;
                Node callState = null;
                Node remoteParty = null;
                Node releaseCause = null;
                Node answerTime = null;
                Node releaseTime = null;
                Node startTime = null;
                
                for ( int i = 0; i < call.getChildNodes().getLength(); i++ )
                {
                    Node node = call.getChildNodes().item( i );
                    if ( node.getNodeName().equals( "personality" ) )
                    {
                        personality = node;
                    }
                    else if ( node.getNodeName().equals( "callState" ) )
                    {
                        callState = node;
                    }
                    else if ( node.getNodeName().equals( "remoteParty" ) )
                    {
                        remoteParty = node;
                    }
                    else if ( node.getNodeName().equals( "releaseCause" ) )
                    {
                        releaseCause = node;
                    }
                    else if ( node.getNodeName().equals( "startTime" ) )
                    {
                        startTime = node;
                    }
                    else if ( node.getNodeName().equals( "answerTime" ) )
                    {
                        answerTime = node;
                    }
                    else if ( node.getNodeName().equals( "releaseTime" ) )
                    {
                        releaseTime = node;
                    }
                }              
                
                if(callState!=null && callState.getNodeName().equals("callState")) {

                    // Check if call
                    if ( callState.getTextContent().equals( "Released" )
                        || callState.getTextContent().equals( "Active" ) )
                    {
                        String startTimeString = startTime != null ? startTime.getTextContent()
                                                                  : null;
                        String answerTimeString = answerTime != null ? answerTime.getTextContent()
                                                                    : null;
                        String releaseTimeString = releaseTime != null ? releaseTime.getTextContent()
                                                                      : null;

                        // Check if a sip or network call
                        String type="";
                        String address="";
                        String fullAddress = "";
                        for(int i=0; i<remoteParty.getChildNodes().getLength();i++) {
                            Node rpChild = remoteParty.getChildNodes().item(i);
                            if(rpChild.getNodeName().equals("address")) {
                                address=rpChild.getTextContent();
                            }else if(rpChild.getNodeName().equals("callType")) {
                                type=rpChild.getTextContent();
                            }
                        }
                        
                        fullAddress = new String(address);
                        // Check if session can be matched to call
                        if(type.equals("Network") || type.equals("Group") || type.equals("Unknown")) {
                            
                            address = address.replace("tel:", "").replace("sip:", "");
                            
                            log.info("Going to format phone number: "+address);
                            
                            if(address.startsWith("+")) 
                            {
                                address = PhoneNumberUtils.formatNumber(address, null);
                            }
                            
                            String sessionKey = AdapterAgent.ADAPTER_TYPE_BROADSOFT+"|"+config.getMyAddress()+"|"+address.split( "@" )[0];
                            Session session = Session.getSession(sessionKey);
                            if (session != null) {

                                log.info("Session key: " + sessionKey);
                                String direction = "inbound";
                                if (personality.getTextContent().equals("Originator") && !address.contains("outbound")) {
                                    //address += "@outbound";
                                    direction = "transfer";
                                    log.info("Transfer detected????");

                                    //when the receiver hangs up, an active callstate is also triggered. 
                                    // but the releaseCause is also set to Temporarily Unavailable
                                    if (callState.getTextContent().equals("Active")) {
                                        if (releaseCause == null ||
                                            (releaseCause != null &&
                                             !releaseCause.getTextContent().equalsIgnoreCase("Temporarily Unavailable") && !releaseCause
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
                                            String extSession = bs.startCall(address);
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
                                            String extSession = bs.startCall(address);
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
                                        if (DDRUtils.stopCostsAtCallHangup(session.getKey(), true)) {
                                            session.drop();
                                        }
                                        hangup(session);
                                    }
                                }
                            }
                            
                        } else {
                            log.warning("Can't handle hangup of type: "+type+" (yet)");
                        }
                    }
                }
            } else {
                Node eventName = dom.getElementsByTagName("eventName").item(0);
                if(eventName!=null && eventName.getTextContent().equals("SubscriptionTerminatedEvent")) {
                    
                    Broadsoft bs = new Broadsoft(config);
                    bs.startSubscription();
                    log.info("Start a new dialog");
                }
                
                log.info("Received a subscription update!");
            }
            
        } catch (Exception e) {
            log.severe("Something failed: "+ e.getMessage());
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
    @Path( "retry" )
    public Response retryQuestion( @QueryParam( "sessionKey" ) String sessionKey ) throws Exception
    {
        Session session = Session.getSession( sessionKey );
        if(session.getQuestion() != null)
        {
            return handleQuestion( session.getQuestion(), session.getAdapterID(), session.getRemoteAddress(), sessionKey );
        }
        return Response.ok( "" ).build();
    }

	public class Return {
		ArrayList<String> prompts;
		Question question;

		public Return(ArrayList<String> prompts, Question question) {
			this.prompts = prompts;
			this.question = question;
		}
	}
	
	public Return formQuestion(Question question, String adapterID,String address) {
		ArrayList<String> prompts = new ArrayList<String>();
		for (int count = 0; count<=LOOP_DETECTION; count++){
			if (question == null) break;
			log.info("Going to form question of type: "+question.getType());
            if ( question.getType() == null )
            {
                question = null;
                break;
            }
			String preferred_language = question.getPreferred_language();
			question.setPreferred_language(preferred_language);	
			String qText = question.getQuestion_text();
			
			if(qText!=null && !qText.equals("")) prompts.add(qText);

			if (question.getType().equalsIgnoreCase("closed")) {
				for (Answer ans : question.getAnswers()) {
					String answer = ans.getAnswer_text();
					if (answer != null && !answer.equals("")) prompts.add(answer);
				}
				break; //Jump from forloop
			} else if (question.getType().equalsIgnoreCase("comment")) {
				//question = question.answer(null, adapterID, null, null);
				break;
			} else 	if (question.getType().equalsIgnoreCase("referral")) {
				if(!question.getUrl().startsWith("tel:")) {
					question = Question.fromURL(question.getUrl(),adapterID,address);
					//question = question.answer(null, null, null);
//					break;
				} else {
				    // Break out because we are going to reconnect
					break;
				}			
			} else {
				break; //Jump from forloop (open questions, etc.)
			}
		}
		return new Return(prompts, question);
	}
	
	protected String renderComment(Question question,ArrayList<String> prompts, String sessionKey){

		String handleTimeoutURL = "timeout";
		String handleExceptionURL = "exception";
		
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
											outputter.attribute("next", handleTimeoutURL+"?questionId="+question.getQuestion_id()+"&sessionKey="+sessionKey);
										outputter.endTag();
									outputter.startTag("elseif");
										outputter.attribute("cond", "thisCall=='busy' || thisCall=='network_busy'");
									outputter.endTag();
										outputter.startTag("goto");
											outputter.attribute("next", handleExceptionURL+"?questionId="+question.getQuestion_id()+"&sessionKey="+sessionKey);
										outputter.endTag();	
									outputter.startTag("else");
									outputter.endTag();
										outputter.startTag("goto");
											outputter.attribute("next", getAnswerUrl()+"?questionId="+question.getQuestion_id()+"&sessionKey="+sessionKey);
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
										outputter.attribute("next", getAnswerUrl()+"?questionId="+question.getQuestion_id()+"&sessionKey="+sessionKey);
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

        String handleTimeoutURL = "timeout";

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
                                                                    "&sessionKey=" + sessionKey);
                outputter.endTag();
            }
            outputter.startTag("noinput");
            outputter.startTag("goto");
            outputter.attribute("next", handleTimeoutURL + "?questionId=" + question.getQuestion_id() + "&sessionKey=" +
                                        sessionKey);
            outputter.endTag();
            outputter.endTag();
            outputter.startTag("nomatch");
            outputter.startTag("goto");
            outputter.attribute("next", getAnswerUrl() + "?questionId=" + question.getQuestion_id() +
                                        "&answerId=-1&sessionKey=" + sessionKey);
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
	    String handleTimeoutURL = "/vxml/timeout";
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
                                if ( retryLimit == null )
                                {
                
                                    outputter.attribute( "next", handleTimeoutURL + 
                                        "?questionId=" + question.getQuestion_id() + "&sessionKey=" + sessionKey );
                                }
                                else
                                {
                                    Integer retryCount = Question.getRetryCount( sessionKey );
                                    if ( retryCount < Integer.parseInt( retryLimit ) )
                                    {
                                        outputter.attribute( "next", "/retry" + "?questionId=" + question.getQuestion_id()
                                            + "&sessionKey=" + sessionKey );
                                        Question.updateRetryCount( sessionKey );
                                    }
                                    else
                                    {
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
	}

    /** renders/updates the xml for recording an audio and posts it to the user on the callback 
     * @param question
     * @param prompts
     * @param sessionKey
     * @param outputter
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    protected void renderVoiceMailQuestion( Question question, ArrayList<String> prompts, String sessionKey,
        XMLOutputter outputter ) throws IOException, UnsupportedEncodingException
    {
				    //assign a default voice mail length if one is not specified
	                String voiceMessageLengthProperty = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.VOICE_MESSAGE_LENGTH );
	                voiceMessageLengthProperty = voiceMessageLengthProperty != null ? voiceMessageLengthProperty : "15s";
	                if(!voiceMessageLengthProperty.endsWith("s"))
	                {
	                    log.warning("Voicemail length must be end with 's'. E.g. 40s. Found: "+ voiceMessageLengthProperty);
	                    voiceMessageLengthProperty += "s";
	                }
	                
	                String dtmfTerm = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.DTMF_TERMINATE );
	                dtmfTerm = dtmfTerm != null ? dtmfTerm : "true";
	                String voiceMailBeep = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.VOICE_MESSAGE_BEEP );
	                voiceMailBeep = voiceMailBeep != null ? voiceMailBeep : "true";
	                
				    // Fetch the upload url
	                //String host = this.host.replace("rest/", "");
	                String uuid = UUID.randomUUID().toString();
	                String filename = uuid+".wav";
				    String storedAudiofile = host+"download/"+filename;

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
                                        outputter.attribute("next", getAnswerUrl()+"?questionId="+question.getQuestion_id()+"&sessionKey="+sessionKey+"&answerInput="+URLEncoder.encode(storedAudiofile, "UTF-8"));
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
                        outputter.endTag();
                    outputter.endTag();
	}
	
	private Response handleQuestion(Question question, String adapterID,String remoteID,String sessionKey)
	{
		String result="<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form><block><exit/></block></form></vxml>";
		Return res = formQuestion(question,adapterID,remoteID);
		if(question !=null && !question.getType().equalsIgnoreCase("comment"))
			question = res.question;
		
		log.info( "question formed at handleQuestion is: "+ ServerUtils.serializeWithoutException( question ));
		log.info( "prompts formed at handleQuestion is: "+ res.prompts );

        if ( question != null )
        {
            question.generateIds();
            Session session = Session.getSession( sessionKey );
            session.setQuestion( question );
            session.setRemoteAddress( remoteID );
            session.storeSession();
            
            //convert all text prompts to speech 
            if(res.prompts != null)
            {
                String language = question.getPreferred_language().contains( "-" ) ? question.getPreferred_language()
                                                                                   : "nl-nl";
                String ttsSpeedProperty = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.TSS_SPEED );
                ttsSpeedProperty = ttsSpeedProperty != null ? ttsSpeedProperty : "0";
                ArrayList<String> promptsCopy = new ArrayList<String>();
                for ( String prompt : res.prompts )
                {
                    if ( !prompt.startsWith( "dtmfKey://" ) )
                    {
                        if ( !prompt.endsWith( ".wav" ) )
                        {
                            promptsCopy.add( getTTSURL( prompt, language, "wav", ttsSpeedProperty, null ) );
                        }
                        else
                        {
                            promptsCopy.add( prompt );
                        }
                    }
                }
                res.prompts = promptsCopy;
            }
            
            if ( question.getType().equalsIgnoreCase( "closed" ) )
            {
                result = renderClosedQuestion( question, res.prompts, sessionKey );
            }
            else if ( question.getType().equalsIgnoreCase( "open" ) )
            {
                result = renderOpenQuestion( question, res.prompts, sessionKey );
            }
            else if ( question.getType().equalsIgnoreCase( "referral" ) )
            {
                if ( question.getUrl().startsWith( "tel:" ) )
                {
                    // added for release0.4.2 to store the question in the session,
                    //for triggering an answered event
                    log.info( String.format( "current session key before referral is: %s and remoteId %s", sessionKey,
                        remoteID ) );
                    try
                    {
                        String redirectedId = PhoneNumberUtils.formatNumber( question.getUrl().replace( "tel:", "" ),
                            null );
                        //update url with formatted redirecteId. RFC3966 returns format tel:<blabla> as expected
                        question.setUrl( PhoneNumberUtils.formatNumber( redirectedId, PhoneNumberFormat.RFC3966 ) );
                        //store the remoteId as its lost while trying to trigger the answered event
                        HashMap<String, String> extras = new HashMap<String, String>();
                        extras.put( "referredCalledId", redirectedId );
                        session.getExtras().putAll( extras );
                        session.setQuestion( question );
                        session.setRemoteAddress( remoteID );
                        //create a new ddr record and session to catch the redirect
                        Session referralSession = Session.getOrCreateSession(AdapterConfig.getAdapterConfig(adapterID),
                                                                             redirectedId);
                        if ( session.getDirection() != null )
                        {
                            DDRRecord ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(
                                AdapterConfig.getAdapterConfig( adapterID ), redirectedId, 1 );
                            referralSession.setDdrRecordId(ddrRecord.getId() );
                            referralSession.setDirection( session.getDirection() );
                        }
                        referralSession.storeSession();
                        session.storeSession();
                    }
                    catch ( Exception e )
                    {
                        log.severe( String.format( "Phonenumber: %s is not valid",
                            question.getUrl().replace( "tel:", "" ) ) );
                    }
                    result = renderComment( question, res.prompts, sessionKey );
                }
            }
            else if ( res.prompts.size() > 0 )
            {
                result = renderComment( question, res.prompts, sessionKey );
            }
        }
        else if ( res.prompts.size() > 0 )
        {
            result = renderComment( null, res.prompts, sessionKey );
        }
        else
        {
            log.info( "Going to hangup? So clear Session?" );
        }
		log.info("Sending xml: "+result);
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
}
