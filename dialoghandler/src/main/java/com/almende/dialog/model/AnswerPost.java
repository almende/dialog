package com.almende.dialog.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class AnswerPost implements Serializable {
	private static final long serialVersionUID = -5739888017486408094L;
	
	String question_id;
	String answer_id;
	String answer_text;
	String responder;
	Map<String, Object> extras;  

	public AnswerPost(){}
	public AnswerPost(String question_id,String answer_id,String answer_text,String responder){
		this.question_id = question_id;
		this.answer_id = answer_id;
		this.answer_text = answer_text;
		this.responder = responder;
	}
	public String getQuestion_id() {
		return question_id;
	}
	public String getAnswer_id() {
		return answer_id;
	}
	public String getAnswer_text() {
		return answer_text;
	}
	public String getResponder() {
		return responder;
	}
	public void setQuestion_id(String question_id) {
		this.question_id = question_id;
	}
	public void setAnswer_id(String answer_id) {
		this.answer_id = answer_id;
	}
	public void setAnswer_text(String answer_text) {
		this.answer_text = answer_text;
	}
	public void setResponder(String responder) {
		this.responder = responder;
	}
    
	public Map<String, Object> getExtras()
    {
	    extras = extras != null ? extras : new HashMap<String, Object>();
        return extras;
    }
	
    public void setExtras( Map<String, Object> extras )
    {
        this.extras = extras;
    }
}