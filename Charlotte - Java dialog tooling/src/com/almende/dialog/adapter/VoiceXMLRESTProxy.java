package com.almende.dialog.adapter;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
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
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.state.StringStore;
import com.almende.dialog.util.PhoneNumberUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

@Path("/vxml/")
public class VoiceXMLRESTProxy {
	protected static final Logger log = Logger.getLogger(com.almende.dialog.adapter.VoiceXMLRESTProxy.class.getName());
	private static final int LOOP_DETECTION=10;
	private static final String DTMFGRAMMAR="/vxml/dtmf2hash";
	
	private static final int MAX_RETRIES=1;
	
	protected String TIMEOUT_URL="/vxml/timeout";
	protected String EXCEPTION_URL="/vxml/exception";
	
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
	 * {@link VoiceXMLRESTProxy#dial(Map, String, String, AdapterConfig) dial} method
	 * 
	 * @param address
	 * @param url
	 * @param config
	 * @return
	 */
	@Deprecated
	public static String dial(String address, String url, AdapterConfig config)
	{
		try
        {
            address = PhoneNumberUtils.formatNumber(address, null)+"@outbound";
        }
        catch ( Exception e )
        {
            log.severe( String.format( "Phonenumber: %s is not valid", address ) );
            return "";
        }
		
		String adapterType="broadsoft";
		String sessionKey = adapterType+"|"+config.getMyAddress()+"|"+address;
		Session session = Session.getSession(sessionKey);
		if (session == null){
			log.severe("VoiceXMLRESTProxy couldn't start new outbound Dialog, adapterConfig not found? "+sessionKey);
			return "";
		}
		session.killed=false;
		session.setStartUrl(url);
		session.setDirection("outbound");
		session.setRemoteAddress(address);
		session.setType(adapterType);
		session.setTrackingToken(UUID.randomUUID().toString());
		session.storeSession();
		
		Question question = Question.fromURL(url,config.getConfigId(),address,config.getMyAddress());
		StringStore.storeString("InitialQuestion_"+sessionKey, question.toJSON());
		
		DDRWrapper.log(url,session.getTrackingToken(),session,"Dial",config);
		
		Broadsoft bs = new Broadsoft(config);
		bs.startSubscription();
		
		String extSession = bs.startCall(address);
		
		session.setExternalSession(extSession);
		session.storeSession();
		
		return sessionKey;
	}

    /**
     * initiates a call to all the numbers in the addressNameMap and returns a
     * Map of <adress, SessionKey>
     * @return
     */
    public static HashMap<String, String> dial( Map<String, String> addressNameMap, String url, String senderName, AdapterConfig config )
    throws Exception
    {
        String adapterType = "broadsoft";
        String sessionPrefix = adapterType+"|"+config.getMyAddress()+"|" ;
        HashMap<String, String> resultSessionMap = new HashMap<String, String>();

        // If it is a broadcast don't provide the remote address because it is deceiving.
        String loadAddress = null;
        if(addressNameMap.size()==1)
            loadAddress = addressNameMap.keySet().iterator().next();

        //fetch the question
        Question question = Question.fromURL(url, config.getConfigId(), loadAddress);
        String questionJson = question.toJSON();

        for ( String address : addressNameMap.keySet() )
        {
            try
            {
                String formattedAddress = PhoneNumberUtils.formatNumber( address, PhoneNumberFormat.E164 )
                    + "@outbound";
                String sessionKey = sessionPrefix + formattedAddress;
                Session session = Session.getSession( sessionKey );
                if ( session == null )
                {
                    log.severe( "VoiceXMLRESTProxy couldn't start new outbound Dialog, adapterConfig not found? "
                        + sessionKey );
                    return null;
                }
                session.killed=false;
                session.setStartUrl(url);
                session.setDirection("outbound");
                session.setRemoteAddress(formattedAddress);
                session.setType(adapterType);
                session.setTrackingToken(UUID.randomUUID().toString());
                
                if(session.getAdapterID()==null)
                    session.setAdapterID(config.getConfigId());
                session.storeSession();

                StringStore.storeString("InitialQuestion_"+ sessionKey, questionJson);
                StringStore.storeString( "question_" + session.getRemoteAddress() + "_" + session.getLocalAddress(),
                    questionJson );
                DDRWrapper.log(url,session.getTrackingToken(),session,"Dial",config);

                Broadsoft bs = new Broadsoft( config );
                bs.startSubscription();

                String extSession = bs.startCall( formattedAddress );
                session.setExternalSession( extSession );
                session.storeSession();
                resultSessionMap.put(formattedAddress, sessionKey);
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
	public Response getDTMF2Hash() {
		
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
									"<item repeat=\"0-\"><ruleref uri=\"#digit\"/></item> "+
									"<item> # </item> "+
								"</one-of> "+
							"</rule> "+
						"</grammar> ";
		
		return Response.ok(result).build();
	}
	
    @Path("new")
    @GET
    @Produces("application/voicexml")
    public Response getNewDialog(@QueryParam("direction") String direction,@QueryParam("remoteID") String remoteID,@QueryParam("localID") String localID, @Context UriInfo ui){
        log.info("call started:"+direction+":"+remoteID+":"+localID);
        this.host=ui.getBaseUri().toString().replace(":80", "");
        
        String adapterType="broadsoft";
        AdapterConfig config = AdapterConfig.findAdapterConfig(adapterType, localID);
        String sessionKey = adapterType+"|"+localID+"|"+remoteID+(direction.equals("outbound")?"@outbound":"");
        Session session = Session.getSession(sessionKey);
        
        // Remove retry counter because call is succesfull
        if(direction.equalsIgnoreCase("outbound")) {
            StringStore.dropString(sessionKey+"_retry");
            log.info("Removed retry!");
        }
        
        String url="";
        if (direction.equals("inbound")){
            url = config.getInitialAgentURL();
            session.setStartUrl(url);
            session.setDirection("inbound");
            session.setRemoteAddress(remoteID);
            session.setType(adapterType);
            session.setPubKey(config.getPublicKey());
            session.setTrackingToken(UUID.randomUUID().toString());
            session.setAdapterID(config.getConfigId());
            
            Broadsoft bs = new Broadsoft( config );
            bs.startSubscription();
        } 
        else 
        {
            if (session != null) {
                url = session.getStartUrl();
            } else {
                log.severe(String.format("Session %s not found", sessionKey));
                return null;
            }
        }
        
        String json = StringStore.getString("InitialQuestion_"+sessionKey);
        Question question = null;
        if(json!=null) {
            log.info("Getting question from cache");
            question = Question.fromJSON(json, session.getAdapterConfig().getConfigId());
            StringStore.dropString("InitialQuestion_"+sessionKey);
        } else {
            question = Question.fromURL(url,session.getAdapterConfig().getConfigId(),remoteID,localID);
        }
        DDRWrapper.log(question,session,"Start",config);
        session.storeSession();
        
        return handleQuestion(question,session.getAdapterConfig().getConfigId(),remoteID,sessionKey);
    }
	
    @Path( "answer" )
    @GET
    @Produces( "application/voicexml+xml" )
    public Response answer( @QueryParam( "question_id" ) String question_id,
        @QueryParam( "answer_id" ) String answer_id, @QueryParam( "answer_input" ) String answer_input,
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
            String json = StringStore.getString( "question_" + session.getRemoteAddress() + "_"
                + session.getLocalAddress() );
            if ( json != null )
            {
                Question question = Question.fromJSON( json, session.getAdapterConfig().getConfigId() );
                //String responder = StringStore.getString(question_id+"-remoteID");
                String responder = session.getRemoteAddress();
                if ( session.killed )
                {
                    log.warning( "session is killed" );
                    return Response.status( Response.Status.BAD_REQUEST ).build();
                }
                DDRWrapper.log( question, session, "Answer" );

                StringStore.dropString( question_id );
                StringStore.dropString( question_id + "-remoteID" );
                StringStore.dropString( "question_" + session.getRemoteAddress() + "_" + session.getLocalAddress() );
                question = question.answer( responder, session.getAdapterConfig().getConfigId(), answer_id,
                    answer_input, sessionKey );
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
    public Response timeout( @QueryParam( "question_id" ) String question_id,
        @QueryParam( "sessionKey" ) String sessionKey )
    {
        String reply = "<vxml><exit/></vxml>";
        String json = StringStore.getString( question_id + "_" + sessionKey );
        if ( json != null )
        {
            Session session = Session.getSession( sessionKey );
            Question question = Question.fromJSON( json, session.getAdapterConfig().getConfigId() );
            String responder = StringStore.getString( question_id + "-remoteID" + "_" + sessionKey );
            if ( session.killed )
            {
                return Response.status( Response.Status.BAD_REQUEST ).build();
            }
            DDRWrapper.log( question, session, "Timeout" );

            StringStore.dropString( question_id );
            StringStore.dropString( question_id + "-remoteID" );
            StringStore.dropString( "question_" + session.getRemoteAddress() + "_"
                + session.getLocalAddress() );

            String currentTimeInMillis = String.valueOf( ServerUtils.getServerCurrentTimeInMillis());
            getTimeMap( currentTimeInMillis, currentTimeInMillis, currentTimeInMillis );
            question = question.event( "timeout", "No answer received", null, responder );

            return handleQuestion( question, session.getAdapterConfig().getConfigId(), responder,
                                   sessionKey );
        }
        return Response.ok( reply ).build();
    }
	
	@Path("exception")
	@GET
	@Produces("application/voicexml+xml")
	public Response exception(@QueryParam("question_id") String question_id, @QueryParam("sessionKey") String sessionKey){
		String reply="<vxml><exit/></vxml>";
		String json = StringStore.getString(question_id + "_" + sessionKey);
		if (json != null){
			Session session = Session.getSession(sessionKey);
			Question question = Question.fromJSON(json, session.getAdapterConfig().getConfigId());
			String responder = StringStore.getString(question_id + "-remoteID" + "_" + sessionKey);
			if (session.killed){
				return Response.status(Response.Status.BAD_REQUEST).build();
			}
			DDRWrapper.log(question,session,"Timeout");
			
			StringStore.dropString(question_id);
			StringStore.dropString(question_id+"-remoteID");
			StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());

			question = question.event("exception", "Wrong answer received", null, responder);
			
			return handleQuestion(question,session.getAdapterConfig().getConfigId(),responder,sessionKey);
		}
		return Response.ok(reply).build();
	}

	@Path("hangup")
    @GET
    @Produces("application/voicexml+xml")
    public Response hangup( @QueryParam( "direction" ) String direction,
        @QueryParam( "remoteID" ) String remoteID, @QueryParam( "localID" ) String localID,
        @QueryParam( "startTime" ) String startTime, @QueryParam( "answerTime" ) String answerTime,
        @QueryParam( "releaseTime" ) String releaseTime, @QueryParam( "notPickedUp" ) Boolean notPickedUp ) 
        throws Exception
    {
        log.info("call hangup with:"+direction+":"+remoteID+":"+localID);
        
        String adapterType="broadsoft";
        
        String sessionKey = adapterType+"|"+localID+"|"+remoteID;
        Session session = Session.getSession(sessionKey);
        
        Question question = null;
        log.info( String.format( "Session key: %s with remote: %s and local %s", sessionKey,
            session.getRemoteAddress(), session.getLocalAddress() ) );
        String stringStoreKey = direction + "_" + session.getRemoteAddress() + "_" + session.getLocalAddress();
        String json = StringStore.getString( stringStoreKey );
        if ( json != null )
        {
            ObjectNode questionNode = ServerUtils.deserialize( json, ObjectNode.class );
            question = ServerUtils.deserialize( questionNode.get( "question" ).toString(), Question.class );
            remoteID = questionNode.get( "remoteCallerId" ).asText();
            //not deleting the remoteCallerIdQuestionMap as hangup (personality: Originator callState: Released) 
            //is received via the ccxml file 
            //            StringStore.dropString( direction + "_" + session.getRemoteAddress() + "_" + session.getLocalAddress() );
        }
        else
        {
            stringStoreKey = "question_" + session.getRemoteAddress() + "_" + session.getLocalAddress();
            json = StringStore.getString( stringStoreKey );
            if ( json == null )
            {
                stringStoreKey = "question_" + session.getRemoteAddress()
                    + ( session.getRemoteAddress().contains( "@outbound" ) ? "" : "@outbound" ) + "_"
                    + session.getLocalAddress();
                json = StringStore.getString( stringStoreKey );
            }
            question = Question.fromJSON( json, session.getAdapterConfig().getConfigId() );
        }
        log.info( String.format( "tried fetching question: %s from StringStore with id: %s", json, stringStoreKey ));
        if ( question == null )
        {
            question = Question.fromURL( session.getStartUrl(), session.getAdapterConfig().getConfigId(), remoteID,
                localID );
        }
        if ( question != null )
        {
            HashMap<String, Object> timeMap = getTimeMap( startTime, answerTime, releaseTime );
            if ( notPickedUp != null )
            {
                timeMap.put( "notPickedUp", notPickedUp );
            }
            question.event( "hangup", "Hangup", timeMap, remoteID );
            DDRWrapper.log( question, session, "Hangup" );
            handleQuestion( null, session.getAdapterConfig().getConfigId(), remoteID, sessionKey );
            //delete all session entities
            String question_id = question.getQuestion_id();
            StringStore.dropString(question_id);
            StringStore.dropString(question_id+"-remoteID");
        }
        else
        {
            log.info( "no question received" );
        }
        StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());
        StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress()+"@outbound");
        StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress()+"@outbound_retry");
        StringStore.dropString(sessionKey);
        StringStore.dropString(sessionKey+"@outbound");
        StringStore.dropString( stringStoreKey );
        return Response.ok("").build();
    }

    /**
     * used to trigger answered event unlike {@link VoiceXMLRESTProxy#answer(String, String, String, String, UriInfo)}
     * @return
     * @throws Exception 
     */
    public Response answered( String direction, String remoteID, String localID, String startTime,
        String answerTime, String releaseTime ) throws Exception
    {
        log.info( "call answered with:" + direction + "_" + remoteID + "_" + localID );
        String adapterType = "broadsoft";
        String sessionKey = adapterType+"|"+localID+"|"+remoteID;
        Session session = Session.getSession(sessionKey);
        log.info( "question from session got: "+ session.getSession_id() );
        Question question = null;
        String stringStoreKey = direction + "_" + remoteID + "_" + localID;
        String json = StringStore.getString( stringStoreKey );
        String responder = "";
        
        //for direction = transfer (redirect event), json should not be null        
        if ( json != null )
        {
            log.info( String.format( "question from string store with id: %s is: %s", direction + "_" + remoteID + "_"
                + localID, json ) );
            ObjectNode questionNode = ServerUtils.deserialize( json, false, ObjectNode.class );
            log.info( "questionNode at answered: " + questionNode.toString() );
            if ( questionNode != null && questionNode.get( "question" ) != null )
            {
                question = ServerUtils.deserialize( questionNode.get( "question" ).toString(), Question.class );
            }
            responder = questionNode.get( "remoteCallerId" ).asText();
        }
        //this is invoked when an outbound call is triggered and answered by the callee
        else 
        {
            stringStoreKey = "question_" + session.getRemoteAddress() + "_" + session.getLocalAddress();
            json = StringStore.getString( stringStoreKey );
            if ( json == null )
            {
                stringStoreKey = "question_" + session.getRemoteAddress()
                    + ( session.getRemoteAddress().contains( "@outbound" ) ? "" : "@outbound" ) + "_"
                    + session.getLocalAddress();
                json = StringStore.getString( stringStoreKey );
            }
            question = Question.fromJSON( json, session.getAdapterConfig().getConfigId() );
        }
        log.info( String.format( "tried fetching question: %s from StringStore with id: %s", json, stringStoreKey ));
        if(question != null)
        {
            HashMap<String, Object> timeMap = getTimeMap( startTime, answerTime, releaseTime );
            question.event( "answered", "Answered", timeMap, responder );
            DDRWrapper.log( question, session, "Answered" );
//            handleQuestion( null, session.getAdapterConfig().getConfigId(), remoteID, sessionKey );
        }
        return Response.ok( "" ).build();
    }
	
    @Path("cc")
    @POST
    public Response receiveCCMessage(String xml) {
        
        log.info("Received cc: "+xml);
        
        String reply="";
        
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
                            } else if(rpChild.getNodeName().equals("callType")) {
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
                            
                            String adapterType="broadsoft";
                            String sessionKey = adapterType+"|"+config.getMyAddress()+"|"+address;
                            String ses = StringStore.getString(sessionKey);
                            
                            log.info("Session key: "+sessionKey);
                            String direction="inbound";
                            if ( personality.getTextContent().equals( "Originator" )
                                && !address.contains( "outbound" ) )
                            {
                                //address += "@outbound";
                                direction = "transfer";
                                log.info( "Transfer detected????" );

                                //when the receiver hangs up, an active callstate is also triggered. 
                                // but the releaseCause is also set to Temporarily Unavailable
                                if ( callState.getTextContent().equals( "Active" ) )
                                {
                                    if ( releaseCause == null
                                        || ( releaseCause != null
                                            && !releaseCause.getTextContent().equalsIgnoreCase(
                                                "Temporarily Unavailable" ) && !releaseCause.getTextContent()
                                            .equalsIgnoreCase( "User Not Found" ) ) )
                                    {
                                        answered( direction, address, config.getMyAddress(), startTimeString,
                                            answerTimeString, releaseTimeString );
                                    }
                                }
                            }
                            else if ( personality.getTextContent().equals( "Originator" ) )
                            {
                                log.info( "Outbound detected?????" );
                                direction = "outbound";
                            }
                            else if ( personality.getTextContent().equals( "Click-to-Dial" ) )
                            {
                                log.info( "CTD hangup detected?????" );
                                direction = "outbound";

                                //TODO: move this to internal mechanism to check if call is started!
                                if ( releaseCause.getTextContent().equals( "Server Failure" ) )
                                {
                                    log.severe( "Need to restart the call!!!! ReleaseCause: "
                                        + releaseCause.getTextContent() );

                                    String retryKey = sessionKey + "_retry";
                                    int retry = ( StringStore.getString( retryKey ) == null ? 0 : Integer
                                        .parseInt( StringStore.getString( retryKey ) ) );
                                    if ( retry < MAX_RETRIES )
                                    {

                                        Broadsoft bs = new Broadsoft( config );
                                        String extSession = bs.startCall( address );
                                        log.info( "Restarted call extSession: " + extSession );
                                        retry++;
                                        StringStore.storeString( sessionKey + "_rety", retry + "" );
                                    }
                                    else
                                    {
                                        // TODO: Send mail to support!!!

                                        log.severe( "Retries failed!!!" );
                                        StringStore.dropString( retryKey );
                                    }
                                }
                                else if ( releaseCause.getTextContent().equals( "Request Failure" ) )
                                {
                                    log.severe( "Restart call?? ReleaseCause: "
                                        + releaseCause.getTextContent() );

                                    String retryKey = sessionKey + "_retry";
                                    int retry = ( StringStore.getString( retryKey ) == null ? 0 : Integer
                                        .parseInt( StringStore.getString( retryKey ) ) );
                                    if ( retry < MAX_RETRIES )
                                    {
                                        Broadsoft bs = new Broadsoft( config );
                                        String extSession = bs.startCall( address );
                                        log.info( "Restarted call extSession: " + extSession );
                                        retry++;
                                        StringStore.storeString( retryKey, retry + "" );
                                    }
                                    else
                                    {
                                        // TODO: Send mail to support!!!
                                        log.severe( "Retries failed!!!" );
                                        StringStore.dropString( retryKey );
                                    }
                                }
                            }
                            
                            if ( callState.getTextContent().equals( "Released" ) )
                            {
                                if ( ses != null && direction != "transfer"
                                    && !personality.getTextContent().equals( "Terminator" )
                                    && fullAddress.startsWith( "tel:" ) )
                                {
                                    log.info( "SESSSION FOUND!! SEND HANGUP!!!" );
                                    this.hangup( direction, address, config.getMyAddress(), startTimeString,
                                        answerTimeString, releaseTimeString, false );
                                }
                                else
                                {
                                    if ( personality.getTextContent().equals( "Originator" )
                                        && fullAddress.startsWith( "sip:" ) )
                                    {
                                        log.info( "Probably a disconnect of a sip. call hangup event" );
                                    }
                                    else if ( personality.getTextContent().equals( "Originator" )
                                        && fullAddress.startsWith( "tel:" ) )
                                    {
                                        log.info( "Probably a disconnect of a redirect. call hangup event" );
                                        hangup( direction, address, config.getMyAddress(), startTimeString,
                                            answerTimeString, releaseTimeString, null );
                                    }
                                    else if ( personality.getTextContent().equals( "Terminator" ) )
                                    {
                                        log.info( "No session for this inbound?????" );
                                    }
                                    else
                                    {
                                        log.info( "What the hell was this?????" );
                                        log.info( "Session already ended?" );
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
	
	public Return formQuestion(Question question, String adapterID,String address) {
		ArrayList<String> prompts = new ArrayList<String>();
		for (int count = 0; count<=LOOP_DETECTION; count++){
			if (question == null) break;
			log.info("Going to form question of type: "+question.getType());
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

		String handleTimeoutURL = "/vxml/timeout";
		String handleExceptionURL = "/vxml/exception";
		
		String redirectTimeoutProperty = question.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT );
        //assign a default timeout if one is not specified
        String redirectTimeout = redirectTimeoutProperty != null ? redirectTimeoutProperty : "40s";
        if(!redirectTimeout.endsWith("s"))
        {
            log.warning("Redirect timeout must be end with 's'. E.g. 40s. Found: "+ redirectTimeout);
            redirectTimeout += "s";
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
								outputter.attribute("bridge","true");
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
											outputter.attribute("next", handleTimeoutURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
										outputter.endTag();
									outputter.startTag("elseif");
										outputter.attribute("cond", "thisCall=='busy' || thisCall=='network_busy'");
									outputter.endTag();
										outputter.startTag("goto");
											outputter.attribute("next", handleExceptionURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
										outputter.endTag();	
									outputter.startTag("else");
									outputter.endTag();
										outputter.startTag("goto");
											outputter.attribute("next", getAnswerUrl()+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
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
										outputter.attribute("next", getAnswerUrl()+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
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

	private String renderClosedQuestion(Question question,ArrayList<String> prompts,String sessionKey){
		ArrayList<Answer> answers=question.getAnswers();
		
		String handleTimeoutURL = "/vxml/timeout";

		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				
				//remove the termchar operator when # is found in the answer
                for ( Answer answer : answers )
                {
                    if ( answers.size() > 11
                        || ( answer.getAnswer_text() != null && answer.getAnswer_text().contains( "dtmfKey://" ) ) )
                    {
                        outputter.startTag( "property" );
                        outputter.attribute( "name", "termchar" );
                        outputter.attribute( "value", "" );
                        outputter.endTag();
                        break;
                    }
                }
				outputter.startTag("menu");	
					for (String prompt : prompts){
						outputter.startTag("prompt");
							outputter.startTag("audio");
								outputter.attribute("src", prompt);
							outputter.endTag();
						outputter.endTag();
					}
					for ( int cnt = 0; cnt < answers.size(); cnt++ )
                    {
                        Integer dtmf = cnt + 1;
                        String dtmfValue = dtmf.toString();
                        if ( answers.get( cnt ).getAnswer_text() != null
                            && answers.get( cnt ).getAnswer_text().startsWith( "dtmfKey://" ) )
                        {
                            dtmfValue = answers.get( cnt ).getAnswer_text().replace( "dtmfKey://", "" ).trim();
                        }
                        else
                        {
                            if ( dtmf == 10 )
                            { // 10 translates into 0
                                dtmfValue = "0";
                            }
                            else if ( dtmf == 11 )
                            {
                                dtmfValue = "*";
                            }
                            else if ( dtmf == 12 )
                            {
                                dtmfValue = "#";
                            }
                            else if ( dtmf > 12 )
                            {
                                break;
                            }
                        }
                        outputter.startTag( "choice" );
                        outputter.attribute( "dtmf", dtmfValue );
                        outputter.attribute( "next", getAnswerUrl() + "?question_id=" + question.getQuestion_id()
                            + "&answer_id=" + answers.get( cnt ).getAnswer_id() + "&answer_input=" + URLEncoder.encode( dtmfValue, "UTF-8" ) + "&sessionKey="
                            + sessionKey );
                        outputter.endTag();
                    }
					outputter.startTag("noinput");
						outputter.startTag("goto");
							outputter.attribute("next", handleTimeoutURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("nomatch");
						outputter.startTag("goto");
							outputter.attribute("next", getAnswerUrl()+"?question_id="+question.getQuestion_id()+"&answer_id=-1&sessionKey="+sessionKey);
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
	
	protected String renderOpenQuestion(Question question,ArrayList<String> prompts,String sessionKey){

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
				if(typeProperty!=null && typeProperty.equalsIgnoreCase("audio")) {
				    
				    // Fetch the upload url
				    String storedAudiofile = this.host+"upload/"+UUID.randomUUID().toString()+".wav";
			        Client client = ParallelInit.getClient();
			        WebResource webResource = client.resource(storedAudiofile+"?url");
			        String uploadURL = "";
			        try {
			            uploadURL = webResource.type("application/json").get(String.class);
			        } catch(Exception e){
			        }
			        uploadURL = uploadURL.replace(this.host, "/");
			        
				    outputter.startTag("form");
                        outputter.attribute("id", "ComposeMessage");
                        outputter.startTag("record");
                            outputter.attribute("name", "file");
                            outputter.attribute("beep", "true");
                            outputter.attribute("maxtime", "15s");
                            outputter.attribute("dtmfterm", "true");
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
                                        outputter.attribute("next", getAnswerUrl()+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey+"&answer_input="+URLEncoder.encode(storedAudiofile, "UTF-8"));
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
				} else {
				    
    				outputter.startTag("var");
    					outputter.attribute("name","answer_input");
    				outputter.endTag();
    				outputter.startTag("var");
    					outputter.attribute("name","question_id");
    					outputter.attribute("expr", "'"+question.getQuestion_id()+"'");
    				outputter.endTag();
    				outputter.startTag("var");
    					outputter.attribute("name","sessionKey");
    					outputter.attribute("expr", "'"+sessionKey+"'");
    				outputter.endTag();
    				outputter.startTag("form");
    					outputter.startTag("field");
    						outputter.attribute("name", "answer");
    						outputter.startTag("grammar");
    							outputter.attribute("mode", "dtmf");
    							outputter.attribute("src", DTMFGRAMMAR);
    							outputter.attribute("type", "application/srgs+xml");
    						outputter.endTag();
    						for (String prompt: prompts){
    							outputter.startTag("prompt");
    								outputter.startTag("audio");
    									outputter.attribute("src", prompt);
    								outputter.endTag();
    							outputter.endTag();
    						}
    						outputter.startTag("noinput");
    							outputter.startTag("reprompt");
    							outputter.endTag();
    						outputter.endTag();
    					
    						outputter.startTag("filled");
    							outputter.startTag("assign");
    								outputter.attribute("name", "answer_input");
    								outputter.attribute("expr", "answer$.utterance.replace(' ','','g')");
    							outputter.endTag();
    							outputter.startTag("submit");
    								outputter.attribute("next", getAnswerUrl());
    								outputter.attribute("namelist","answer_input question_id sessionKey");
    							outputter.endTag();
    							outputter.startTag("clear");
    								outputter.attribute("namelist", "answer_input answer");
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
	
	private Response handleQuestion(Question question, String adapterID,String remoteID,String sessionKey)
	{
		String result="<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form><block><exit/></block></form></vxml>";
		Return res = formQuestion(question,adapterID,remoteID);
		if(question !=null && !question.getType().equalsIgnoreCase("comment"))
			question = res.question;
		
		log.info( "question formed at handleQuestion is: "+ question );
		log.info( "prompts formed at handleQuestion is: "+ res.prompts );

        if ( question != null )
        {
            question.generateIds();
            String questionJSON = question.toJSON();
            StringStore.storeString( question.getQuestion_id() + "_" +sessionKey, questionJSON );
            StringStore.storeString( question.getQuestion_id() + "-remoteID" + "_" + sessionKey, remoteID );

            Session session = Session.getSession( sessionKey );
            StringStore.storeString( "question_" + session.getRemoteAddress() + "_" + session.getLocalAddress(),
                questionJSON );

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
                    log.info( String.format( "session key at handle question is: %s and remoteId %s",
                                             sessionKey, remoteID ) );
                    String[] sessionKeyArray = sessionKey.split( "\\|" );
                    if ( sessionKeyArray.length == 3 )
                    {
                        try
                        {
                            String redirectedId = PhoneNumberUtils
                                .formatNumber( question.getUrl().replace( "tel:", "" ), null );
                            //update url with formatted redirecteId
                            question.setUrl( PhoneNumberUtils.formatNumber( redirectedId, PhoneNumberFormat.RFC3966 ) );
                            String transferKey = "transfer_" + redirectedId + "_" + sessionKeyArray[1];
                            log.info( String.format( "referral question %s stored with key: %s", questionJSON,
                                transferKey ) );
                            //store the remoteId as its lost while trying to trigger the answered event
                            HashMap<String, Object> questionMap = new HashMap<String, Object>();
                            questionMap.put( "question", question );
                            questionMap.put( "remoteCallerId", remoteID );
                            String questionMapString = null;
                            try
                            {
                                questionMapString = ServerUtils.serialize( questionMap );
                            }
                            catch ( Exception e )
                            {
                                log.severe( "Question map serialization failed" );
                                e.printStackTrace();
                            }
                            log.info( "question map: " + questionMapString );
                            //key format: transfer_requester_remoteId
                            StringStore.storeString( transferKey, questionMapString );
                        }
                        catch ( Exception e )
                        {
                            log.severe( String.format( "Phonenumber: %s is not valid",
                                question.getUrl().replace( "tel:", "" ) ) );
                        }
                    }
                    else
                    {
                        log.warning( "Could not save question in session: " + sessionKey
                            + " for answered event as sessionKeyArray length is: "
                            + sessionKeyArray.length );
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
		return "/vxml/answer";
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
}
