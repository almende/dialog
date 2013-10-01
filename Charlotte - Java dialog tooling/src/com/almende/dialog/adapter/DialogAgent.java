package com.almende.dialog.adapter;

import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.KeyServerLib;
import com.almende.dialog.util.ServerUtils;
import com.almende.eve.agent.Agent;
import com.almende.eve.json.annotation.Name;
import com.almende.eve.json.annotation.Required;
import com.almende.eve.json.jackson.JOM;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.code.twig.annotation.AnnotationObjectDatastore;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DialogAgent extends Agent {
	
	private static final Logger log = Logger.getLogger(DialogAgent.class.getName());
	
	public DialogAgent(){
		super();
		ParallelInit.startThreads();
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
	

    public HashMap<String, String> outboundCall( @Name( "address" ) String address,
        @Name("senderName") @Required( false ) String senderName, @Name( "url" ) String url,
        @Name( "adapterType" ) @Required( false ) String adapterType,
        @Name( "adapterID" ) @Required( false ) String adapterID,
        @Name( "publicKey" ) String pubKey, @Name( "privateKey" ) String privKey ) throws Exception
    {
        return outboundCallWithList( Arrays.asList( address ), senderName, url, adapterType, adapterID, pubKey, privKey );
    }
	
	/**
	 * updated the outboundCall functionality to support broadcast functionality
	 * @param addressList list of addresses
	 * @throws Exception
	 */
    public HashMap<String, String> outboundCallWithList( @Name( "addressList" ) Collection<String> addressList,
        @Name("senderName") @Required( false ) String senderName, @Name( "url" ) String url, 
        @Name( "adapterType" ) @Required( false ) String adapterType,
        @Name( "adapterID" ) @Required( false ) String adapterID,
        @Name( "publicKey" ) String pubKey, @Name( "privateKey" ) String privKey ) throws Exception
    {
        Map<String, String> addressNameMap = new HashMap<String, String>();
        ServerUtils.putCollectionAsKey( addressNameMap, addressList, "" );
        return outboundCallWithMap( addressNameMap, senderName, url, adapterType, adapterID, pubKey, privKey );
    }
    
    /**
     * updated the outboundCall functionality to support broadcast functionality.
     * @param addressMap Key: address and Value: name
     * @throws Exception
     */
    public HashMap<String, String> outboundCallWithMap( @Name( "addressMap" ) Map<String, String> addressMap,
        @Name("senderName") @Required( false ) String senderName, @Name( "url" ) String url, 
        @Name( "adapterType" ) @Required( false ) String adapterType,
        @Name( "adapterID" ) @Required( false ) String adapterID,
        @Name( "publicKey" ) String pubKey, @Name( "privateKey" ) String privKey ) throws Exception
    {
        HashMap<String, String> resultSessionMap;
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
                resultSessionMap = new XMPPServlet().startDialog( addressMap, url, senderName, config );
            }
            else if ( adapterType.toUpperCase().equals( "BROADSOFT" ) )
            {
                //fetch the first address in the map
                if( !addressMap.keySet().isEmpty() )
                {
                    resultSessionMap = VoiceXMLRESTProxy.dial(addressMap, url, senderName, config);
                }
                else 
                {
                    throw new Exception( "Address should not be empty to setup a call" );
                }
            }
            else if ( adapterType.toUpperCase().equals( "MAIL" ) )
            {
                resultSessionMap = new MailServlet().startDialog( addressMap, url, senderName, config );
            }
            else if ( adapterType.toUpperCase().equals( "SMS" ) )
            {
                resultSessionMap = new MBSmsServlet().startDialog( addressMap, url, senderName, config );
            }
            else if ( adapterType.toUpperCase().equals( "CM" ) )
            {
                resultSessionMap = new CMSmsServlet().startDialog( addressMap, url, senderName, config );
            }
            else if ( adapterType.toUpperCase().equals( "TWITTER" ) )
            {
                resultSessionMap = new TwitterServlet().startDialog( addressMap, url, senderName, config );
            }
            else
            {
                throw new Exception("Unknown type given: either broadsoft or xmpp or phone or mail" );
            }
        }
        else
        {
            throw new Exception( "Invalid adapter found" );
        }
        return resultSessionMap;
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
	
	public List<AdapterConfig> getOwnAdapters(@Name("adapterType") @Required(false) String adapterType,
                           @Name("publicKey") String pubKey,
                           @Name("privateKey") String privKey) throws Exception {

        log.setLevel(Level.INFO);
        ArrayNode adapterList = KeyServerLib.getAllowedAdapterList(pubKey, privKey, adapterType);
        
        if(adapterList==null)
            throw new Exception("Invalid key provided");
        if(adapterList.size() == 0)
            throw new Exception( "No adapters found at portal: "+ Settings.KEYSERVER );
        
        List<AdapterConfig> adapterConfigs = AdapterConfig.findAdapterConfigFromList(adapterType, adapterList);
        return adapterConfigs;
	}
	
	@Override
	public String getDescription() {
		return "Dialog handling agent";
	}

	@Override
	public String getVersion() {
		return "0.4.1";
	}

}

