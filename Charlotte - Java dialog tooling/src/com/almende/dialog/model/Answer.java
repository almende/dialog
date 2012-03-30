package com.almende.dialog.model;

import com.almende.dialog.model.impl.*;
import com.almende.dialog.model.intf.AnswerIntf;

public class Answer implements AnswerIntf {
	private static final long serialVersionUID = -3770386829613266208L;
	
	AnswerIntf answer;
	
	public Answer(){
		this.answer = new A_fields();
	}
	public String getAnswer_id() { return answer.getAnswer_id(); }
	public String getAnswer_text() { return answer.getAnswer_text(); }
	public String getCallback() { return answer.getCallback(); }
	public String getAnswer_expandedtext() { return answer.getAnswer_expandedtext(); }
	public void setAnswer_id(String answer_id) { answer.setAnswer_id(answer_id); }
	public void setAnswer_text(String answer_text) { answer.setAnswer_text(answer_text); }
	public void setCallback(String callback) { answer.setCallback(callback); }
}
