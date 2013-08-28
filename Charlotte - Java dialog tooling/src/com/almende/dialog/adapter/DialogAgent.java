package com.almende.dialog.adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.state.StringStore;
import com.almende.dialog.util.KeyServerLib;
import com.almende.eve.agent.Agent;
import com.almende.eve.agent.annotation.Access;
import com.almende.eve.agent.annotation.AccessType;
import com.almende.eve.json.annotation.Name;
import com.almende.eve.json.annotation.Required;
import com.almende.eve.json.jackson.JOM;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.code.twig.annotation.AnnotationObjectDatastore;

public class DialogAgent extends Agent {
	
	private static final Logger log = Logger.getLogger(DialogAgent.class.getName());
	
	public DialogAgent(){
		super();
		ParallelInit.startThreads();
	}
	
	@Access(AccessType.UNAVAILABLE)
	public Question getQuestion(String url, String id, String json){
		Question question=null;
		if (id != null) question = Question.fromJSON(StringStore.getString(id));
		if (json != null) question = Question.fromJSON(json);
		if (question == null && url != null) question = Question.fromURL(url);
		question.generateIds();
		return question;
	}
	
	public ArrayList<String> getActiveCalls(@Name("adapterID") String adapterID) {
		
		try {
			AdapterConfig config = AdapterConfig.findAdapterConfigFromList(adapterID, null, null);
			if(config.getAdapterType().toLowerCase().equals("broadsoft")) {
				return VoiceXMLRESTProxy.getActiveCalls(config);
			}
		} catch(Exception ex) {
		}
		
		return null;
	}
	
	public ArrayList<String> getActiveCallsInfo(@Name("adapterID") String adapterID) {
		
		try {
			AdapterConfig config = AdapterConfig.findAdapterConfigFromList(adapterID, null, null);
			if(config.getAdapterType().toLowerCase().equals("broadsoft")) {
				return VoiceXMLRESTProxy.getActiveCallsInfo(config);
			}
		} catch(Exception ex) {
		}
		
		return null;
	}
	
	public boolean killActiveCalls(@Name("adapterID") String adapterID) {
		
		try {
			AdapterConfig config = AdapterConfig.findAdapterConfigFromList(adapterID, null, null);
			if(config.getAdapterType().toLowerCase().equals("broadsoft")) {
				
				return VoiceXMLRESTProxy.killActiveCalls(config);
			}
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
		return false;
	}
	
	public String killCall(@Name("session") String sessionKey){
		Session session = Session.getSession(sessionKey);
		if (session == null) return "unknown";
		session.kill();
		return "ok";
	}
	
	public String outboundCall(@Name("address") String address, 
							   @Name("url") String url, 
							   @Name("adapterType") @Required(false) String adapterType, 
							   @Name("adapterID") @Required(false) String adapterID, 
							   @Name("publicKey") String pubKey,
							   @Name("privateKey") String privKey) throws Exception{
		
		if(adapterType!=null && !adapterType.equals("") &&
				adapterID!=null && !adapterID.equals("")) {
			throw new Exception("Choose adapterType or adapterID not both");
		}
		log.setLevel(Level.INFO);
		log.info("Starting call with adapterID: "+adapterID+" pubKey: "+pubKey+" privKey: "+privKey);
		ArrayNode adapterList = null;
		log.info( String.format( "pub: %s pri %s adapterType %s", pubKey, privKey, adapterType ) );
		adapterList = KeyServerLib.getAllowedAdapterList(pubKey, privKey, adapterType);
		
		if(adapterList==null)
			throw new Exception("Invalid key provided");
		//try {
		log.info("Trying to find config");
		AdapterConfig config = AdapterConfig.findAdapterConfigFromList(adapterID, adapterType,adapterList);
		if(config!=null) {
			log.info("Config found: "+config.getConfigId());
			adapterType = config.getAdapterType();
			if (adapterType.toUpperCase().equals("XMPP")){
				return "{'sessionKey':'"+new XMPPServlet().startDialog(address,url,config)+"'}";
			} else if (adapterType.toUpperCase().equals("BROADSOFT")){
				return "{'sessionKey':'"+VoiceXMLRESTProxy.dial(address,url,config)+"'}";
			} else if (adapterType.toUpperCase().equals("MAIL")){
				return "{'sessionKey':'"+new MailServlet().startDialog(address,url,config)+"'}";
			} else if (adapterType.toUpperCase().equals("SMS")){
				return "{'sessionKey':'"+new MBSmsServlet().startDialog(address,url,config)+"'}";
			} else if (adapterType.toUpperCase().equals("CM")){
				return "{'sessionKey':'"+new CMSmsServlet().startDialog(address,url,config)+"'}";
			} else if (adapterType.toUpperCase().equals("TWITTER")){
				return "{'sessionKey':'"+new TwitterServlet().startDialog(address,url,config)+"'}";
			} else {
				throw new Exception("Unknown type given: either broadsoft or xmpp or phone or mail");
			}
		} else {
			throw new Exception("Invalid adapter found");
		}
		/*} catch(Exception ex) {
			ex.printStackTrace();
			log.warning(ex.getLocalizedMessage());
			return "Error in finding adapter: "+ex.getMessage();
		}*/
	}
	
	/**
	 * updated the outboundCall functionality to support broadcast functionality
	 * @param addressList list of addresses
	 * @throws Exception
	 */
        public String outboundCallWithList( @Name( "addressList" ) Collection<String> addressList,
            @Name( "url" ) String url, @Name( "adapterType" ) @Required( false ) String adapterType,
            @Name( "adapterID" ) @Required( false ) String adapterID,
            @Name( "publicKey" ) String pubKey, @Name( "privateKey" ) String privKey ) throws Exception
        {
            Map<String, String> addressNameMap = new HashMap<String, String>();
            addressNameMap.keySet().addAll( addressList );
            return outboundCallWithMap( addressNameMap, url, adapterType, adapterID, pubKey, privKey );
        }
        
        /**
         * updated the outboundCall functionality to support broadcast functionality.
         * @param addressMap Key: address and Value: name
         * @throws Exception
         */
        public String outboundCallWithMap( @Name( "addressMap" ) Map<String, String> addressMap,
            @Name( "url" ) String url, @Name( "adapterType" ) @Required( false ) String adapterType,
            @Name( "adapterID" ) @Required( false ) String adapterID,
            @Name( "publicKey" ) String pubKey, @Name( "privateKey" ) String privKey ) throws Exception
        {
    
            if ( adapterType != null && !adapterType.equals( "" ) && adapterID != null
                && !adapterID.equals( "" ) )
            {
                throw new Exception( "Choose adapterType or adapterID not both" );
            }
            log.setLevel( Level.INFO );
            ArrayNode adapterList = null;
            log.info( String.format( "pub: %s pri %s adapterType %s", pubKey, privKey, adapterType ) );
            adapterList = KeyServerLib.getAllowedAdapterList( pubKey, privKey, adapterType );
    
            if ( adapterList == null )
                throw new Exception( "Invalid key provided" );
            //try {
            log.info( "Trying to find config" );
            AdapterConfig config = AdapterConfig.findAdapterConfigFromList( adapterID, adapterType,
                                                                            adapterList );
            if ( config != null )
            {
                log.info( "Config found: " + config.getConfigId() );
                adapterType = config.getAdapterType();
                if ( adapterType.toUpperCase().equals( "XMPP" ) )
                {
                    return "{'sessionKey':'" + new XMPPServlet().startDialog( addressMap, url, config ) + "'}";
                }
                //                else if ( adapterType.toUpperCase().equals( "BROADSOFT" ) )
                //                {
                //                    return "{'sessionKey':'" + VoiceXMLRESTProxy.dial( addressList, url, config ) + "'}";
                //                }
                else if ( adapterType.toUpperCase().equals( "MAIL" ) )
                {
                    return "{'sessionKey':'" + new MailServlet().startDialog( addressMap, url, config )
                        + "'}";
                }
                else if ( adapterType.toUpperCase().equals( "SMS" ) )
                {
                    return "{'sessionKey':'"
                        + new AskSmsServlet().startDialog( addressMap, url, config ) + "'}";
                }
                else if ( adapterType.toUpperCase().equals( "CM" ) )
                {
                    return "{'sessionKey':'"
                        + new CMSmsServlet().startDialog( addressMap, url, config ) + "'}";
                }
                else if ( adapterType.toUpperCase().equals( "TWITTER" ) )
                {
                    return "{'sessionKey':'"
                        + new TwitterServlet().startDialog( addressMap, url, config ) + "'}";
                }
                else
                {
                    throw new Exception(
                        "Unknown type given: either broadsoft or xmpp or phone or mail" );
                }
            }
            else
            {
                throw new Exception( "Invalid adapter found" );
            }
            /*
             * } catch(Exception ex) { ex.printStackTrace();
             * log.warning(ex.getLocalizedMessage()); return
             * "Error in finding adapter: "+ex.getMessage(); }
             */
        }
	
	public String changeAgent(@Name("url") String url,
						   @Name("adapterType") @Required(false) String adapterType, 
						   @Name("adapterID") @Required(false) String adapterID, 
						   @Name("publicKey") String pubKey,
						   @Name("privateKey") String privKey) throws Exception {
		
		if(adapterType!=null && !adapterType.equals("") &&
				adapterID!=null && !adapterID.equals("")) {
			throw new Exception("Choose adapterType or adapterID not both");
		}
		log.setLevel(Level.INFO);
		ArrayNode adapterList = null;
		adapterList = KeyServerLib.getAllowedAdapterList(pubKey, privKey, adapterType);
		
		if(adapterList==null)
			throw new Exception("Invalid key provided");
		
		AdapterConfig config = AdapterConfig.findAdapterConfigFromList(adapterID, adapterType,adapterList);
		if(config!=null) {
			log.info("Config found: "+config.getConfigId());
			AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
			config.setInitialAgentURL(url);
			datastore.store(config);
			
			ObjectNode result = JOM.createObjectNode();
			result.put("id", config.getConfigId());
			result.put("type", config.getAdapterType());
			result.put("url", config.getInitialAgentURL());
			return result.toString();
		} else {
			throw new Exception("Invalid adapter found");
		}
	}
	
	public String startDialog(@Name("question_url") @Required(false) String url,
							  @Name("myid") @Required(false) String id, 
							  @Name("question_json") @Required(false) String json,
							  @Name("expanded_texts") @Required(false) String expanded_texts){
		String reply = "";
		Question question = getQuestion(url,id,json);
		if (expanded_texts == null) expanded_texts = "false";
		if (question != null) reply = question.toJSON(new Boolean(expanded_texts));
		if (id != null){
			if (question == null){
        		StringStore.dropString(id);
        	} else {
        		StringStore.storeString(id, reply);
        	}
		}
		return reply;
	}
	public String answer(@Name("question_url") @Required(false) String url,
						 @Name("myid") @Required(false) String id,
						 @Name("question_json") @Required(false) String json,
						 @Name("answer_input") @Required(false) String answer_input,
						 @Name("answer_id") @Required(false) String answer_id,
						 @Name("expanded_texts") @Required(false) String expanded_texts){
		String reply = "";
		Question question = getQuestion(url,id,json);
		if (question != null) question = question.answer("",answer_id, answer_input);
		if (expanded_texts == null) expanded_texts = "false";
		if (question != null) reply = question.toJSON(new Boolean(expanded_texts));
		if (id != null){
			if (question == null){
        		StringStore.dropString(id);
        	} else {
        		StringStore.storeString(id, reply);
        	}
		}
		return reply;
	}
	
	@Override
	public String getDescription() {
		return "Dialog handling agent";
	}

	@Override
	public String getVersion() {
		return "0.1";
	}

}

