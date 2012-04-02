package com.almende.dialog.model.impl;

import java.util.ArrayList;
import java.util.logging.Logger;

import com.almende.dialog.model.Answer;
import com.almende.dialog.model.ClientCon;
import com.almende.dialog.model.EventCallback;
import com.almende.dialog.model.intf.QuestionIntf;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public class Q_fields implements QuestionIntf {
	private static final long serialVersionUID = 748817624285821262L;
	private static final Logger log = Logger.getLogger(com.almende.dialog.model.impl.Q_fields.class.getName()); 	
	
	String question_id;
	String question_text;
	String type;
	String url;
	ArrayList<Answer> answers;
	ArrayList<EventCallback> event_callbacks;
	
	public Q_fields(){}
	
	public String getQuestion_id() {
		return question_id;
	}
	public String getQuestion_text() {
		return question_text;
	}
	public String getType() {
		return type;
	}
	public String getUrl() {
		return url;
	}
	public ArrayList<Answer> getAnswers() {
		return answers;
	}
	public ArrayList<EventCallback> getEvent_callbacks() {
		return event_callbacks;
	}
	public void setQuestion_id(String question_id) {
		this.question_id = question_id;
	}
	public void setQuestion_text(String question_text) {
		this.question_text = question_text;
	}
	public void setType(String type) {
		this.type = type;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public void setAnswers(ArrayList<Answer> answers) {
		this.answers = answers;
	}
	public void setEvent_callbacks(ArrayList<EventCallback> event_callbacks) {
		this.event_callbacks = event_callbacks;
	}
	@Override
	public String getQuestion_expandedtext(String language) {
		Client client = ClientCon.client;
		String url = this.getQuestion_text();
		if (language != null && !language.equals("")) url+="?preferred_language="+language;
		WebResource webResource = client.resource(url);
		String text = "";
		try {
			text = webResource.type("text/plain").get(String.class);
		} catch (Exception e){
			log.severe(e.toString());
		}
		return text;
	}
	@Override
	public String getQuestion_expandedtext() {
		return getQuestion_expandedtext(null);
	}

	
}
