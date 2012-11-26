package com.almende.dialog.adapter;

//import java.util.logging.Logger;

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

public class DialogAgent extends Agent {
//	private static final Logger log = Logger.getLogger("DialogHandler");
	
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
//	public String outboundCall(@Name("address") String address, @Name("url") String url, @Name("type") String adapterType, @Name("account") String accountId, @Name("token") String token){
		//log.warning("outboundCall called: "+address+" / "+ url + " / "+ type);
//		Account account = Account.checkAccount(accountId, token);
//		if (account == null) return "Incorrect account/token given!";
		
		AdapterConfig config = AdapterConfig.findAdapterConfig(adapterType,adapterID);
		if (adapterType.equals("gtalk")){
			return "{'sessionKey':'"+new XMPPServlet().startDialog(address,url,config)+"'}";
		} else if (adapterType.equals("phone")){
			return "{'sessionKey':'"+VoiceXMLRESTProxy.dial(address,url,config)+"'}";
		} else if (adapterType.equals("mail")){
			return "{'sessionKey':'"+new MailServlet().startDialog(address,url,config)+"'}";
		} else if (adapterType.equals("sms")){
			return "{'sessionKey':'"+new AskSmsServlet().startDialog(address,url,config)+"'}";
		} else {
			return "Unknown type given: either gtalk or phone or mail";
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

