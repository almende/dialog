package com.almende.dialog.model;

import com.almende.dialog.model.impl.*;
import com.almende.dialog.model.intf.AnswerIntf;

import flexjson.JSON;

public class Answer implements AnswerIntf {
	private static final long serialVersionUID = -3770386829613266208L;
	
	AnswerIntf answer;
	
	public Answer(){
		this.answer = new A_fields();
	}
	@Override
	public String getAnswer_id() { return answer.getAnswer_id(); }
	@Override
	public String getAnswer_text() { return answer.getAnswer_text(); }
	@Override
	public String getCallback() { return answer.getCallback(); }
	
	@Override
	@JSON(include = false)
	public String getAnswer_expandedtext() { return answer.getAnswer_expandedtext(); }
	@Override
	@JSON(include = false)
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
