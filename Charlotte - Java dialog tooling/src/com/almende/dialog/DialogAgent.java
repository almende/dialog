package com.almende.dialog;

import com.almende.dialog.model.Question;
import com.almende.dialog.state.StringStore;
import com.almende.eve.agent.Agent;
import com.almende.eve.json.annotation.ParameterName;
import com.almende.eve.json.annotation.ParameterRequired;

import com.almende.eve.agent.annotation.*;

public class DialogAgent extends Agent {
	private static final long serialVersionUID = 3874598521367745811L;

	@Access(AccessType.UNAVAILABLE)
	public Question getQuestion(String url, String id, String json){
		Question question=null;
		if (id != null) question = Question.fromJSON(StringStore.getString(id));
		if (json != null) question = Question.fromJSON(json);
		if (question == null && url != null) question = Question.fromURL(url);
		return question;
	}
	
	public String startDialog(@ParameterName("question_url") @ParameterRequired(false) String url,
							  @ParameterName("myid") @ParameterRequired(false) String id, 
							  @ParameterName("question_json") @ParameterRequired(false) String json){
		String reply = "";
		Question question = getQuestion(url,id,json);
		if (question != null) reply = question.toJSON();
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
						 @ParameterName("answer_id") @ParameterRequired(false) String answer_id){
		String reply = "";
		Question question = getQuestion(url,id,json);
		if (question != null) question = question.answer("",answer_id, answer_input);
		if (question != null) reply = question.toJSON();
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

