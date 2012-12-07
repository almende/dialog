package com.almende.dialog.adapter;

import java.util.logging.Logger;

import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.state.StringStore;
import com.almende.eve.agent.Agent;
import com.almende.eve.agent.annotation.Access;
import com.almende.eve.agent.annotation.AccessType;
import com.almende.eve.json.annotation.Name;
import com.almende.eve.json.annotation.Required;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public class DialogAgent extends Agent {
	private static final Logger log = Logger.getLogger("DialogHandler");
	
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
	
	public String killCall(String sessionKey){
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
							   @Name("privateKey") String privKey){
		
		// Authenticate keys with key-server
		ArrayNode adapterList = this.getAllowedAdapterList(pubKey, privKey, adapterType);
		
		if(adapterList==null)
			return "Invalid key provided";
		try {
			AdapterConfig config = AdapterConfig.findAdapterConfig(adapterID, adapterType,adapterList);
		
			if(config!=null) {
				if (adapterType.equals("XMPP")){
					return "{'sessionKey':'"+new XMPPServlet().startDialog(address,url,config)+"'}";
				} else if (adapterType.equals("BROADSOFT")){
					return "{'sessionKey':'"+VoiceXMLRESTProxy.dial(address,url,config)+"'}";
				} else if (adapterType.equals("MAIL")){
					return "{'sessionKey':'"+new MailServlet().startDialog(address,url,config)+"'}";
				} else if (adapterType.equals("SMS")){
					return "{'sessionKey':'"+new AskSmsServlet().startDialog(address,url,config)+"'}";
				} else {
					return "Unknown type given: either gtalk or phone or mail";
				}
			} else {
				return "Invalid adapter found";
			}
		} catch(Exception ex) {
			return "Error in finding adapter";
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
	
	private ArrayNode getAllowedAdapterList(String pubKey, String privKey, String adapterType) {
		
		String path="/askAnywaysServices/rest/keys/checkkey/"+pubKey+"/"+privKey+"/outbound";
		
		Client client = ParallelInit.getClient();
		WebResource webResource = client.resource(Settings.KEYSERVER+path);
		String res = webResource.get(String.class);
		
		ObjectMapper om = ParallelInit.getObjectMapper();
		try {
			JsonNode result = om.readValue(res, JsonNode.class);
			if(!result.get("valid").asBoolean())
				return null;
			
			return (ArrayNode) result.get("adapters");
			
		} catch(Exception ex) {
			log.warning("Unable to parse result");
		}
		
		return null;
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

