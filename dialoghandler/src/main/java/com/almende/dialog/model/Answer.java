package com.almende.dialog.model;

import com.almende.dialog.model.impl.*;
import com.almende.dialog.model.intf.AnswerIntf;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class Answer implements AnswerIntf {
	private static final long serialVersionUID = -3770386829613266208L;
	
	AnswerIntf answer;
	
	public Answer(){
		this.answer = new A_fields();
	}
	public Answer(String answer_text, String callback){
		this.answer = new A_fields();
		this.answer.setAnswer_text(answer_text);
		this.answer.setCallback(callback);
	}
	@Override
	public String getAnswer_id() { return answer.getAnswer_id(); }
	@Override
	public String getAnswer_text() { return answer.getAnswer_text(); }
	@Override
	public String getCallback() { return answer.getCallback(); }
	
	@Override
	public String getAnswer_expandedtext() { return answer.getAnswer_expandedtext(); }
	@Override
	@JsonIgnore
	public String getAnswer_expandedtext(String language) {
		return answer.getAnswer_expandedtext(language); 
	}

	@Override
	public void setAnswer_id(String answer_id) { answer.setAnswer_id(answer_id); }
	@Override
	public void setAnswer_text(String answer_text) { answer.setAnswer_text(answer_text); }
	@Override
	public void setCallback(String callback) { answer.setCallback(callback); }
}
