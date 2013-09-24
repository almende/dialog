package com.almende.dialog.model;

import java.io.Serializable;

public class EventPost implements Serializable {

	private static final long serialVersionUID = -388855545127069511L;
	
	private String responder;
	private String question_id;
	private String event;
	private String message;
	
	public EventPost(){}
	public EventPost(String responder, String question_id, String event, String message) {
		this.responder = responder;
		this.question_id = question_id;
		this.event = event;
		this.message = message;
	}
	
	public String getResponder() {
		return responder;
	}
	public String getQuestion_id() {
		return question_id;
	}
	public String getEvent() {
		return event;
	}
	public String getMessage() {
		return message;
	}
	
	public void setResponder(String responder) {
		this.responder = responder;
	}
	public void setQuestion_id(String question_id) {
		this.question_id = question_id;
	}
	public void setEvent(String event) {
		this.event = event;
	}
	public void setMessage(String message) {
		this.message = message;
	}
}
