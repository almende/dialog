package com.almende.dialog.adapter;

import java.util.logging.Logger;

import com.almende.dialog.model.Question;
import com.almende.dialog.state.StringStore;
import com.almende.eve.agent.Agent;
import com.almende.eve.json.annotation.ParameterName;
import com.almende.eve.json.annotation.ParameterRequired;

import com.almende.eve.agent.annotation.*;
import com.almende.util.ParallelInit;

public class DialogAgent extends Agent {
	private static final long serialVersionUID = 3874598521367745811L;
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	
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
	
	//TODO: Move the dialer to a separate Servlet...
	public boolean outboundCall(@ParameterName("address") String address, @ParameterName("url") String url, @ParameterName("type") String type){
		log.warning("outboundCall called: "+address+" / "+ url + " / "+ type);
		Question question = Question.fromURL(url, address);
		if (type.equals("gtalk")){
			XMPPServlet.startDialog(address,question);
		} else if (type.equals("phone")){
			//TODO: implement dialer
		} else {
			return false;
		}
		return true;
	}
	
	public String startDialog(@ParameterName("question_url") @ParameterRequired(false) String url,
							  @ParameterName("myid") @ParameterRequired(false) String id, 
							  @ParameterName("question_json") @ParameterRequired(false) String json,
							  @ParameterName("expanded_texts") @ParameterRequired(false) String expanded_texts){
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
	public String answer(@ParameterName("question_url") @ParameterRequired(false) String url,
						 @ParameterName("myid") @ParameterRequired(false) String id,
						 @ParameterName("question_json") @ParameterRequired(false) String json,
						 @ParameterName("answer_input") @ParameterRequired(false) String answer_input,
						 @ParameterName("answer_id") @ParameterRequired(false) String answer_id,
						 @ParameterName("expanded_texts") @ParameterRequired(false) String expanded_texts){
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

