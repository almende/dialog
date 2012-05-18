package com.almende.dialog.model.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import com.almende.dialog.model.Answer;
import com.almende.dialog.model.EventCallback;
import com.almende.dialog.model.intf.QuestionIntf;
import com.almende.util.ParallelInit;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

import flexjson.JSONDeserializer;

public class Q_fields implements QuestionIntf {
	private static final long serialVersionUID = 748817624285821262L;
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	String question_id;
	String question_text;
	String type;
	String url;
	String requester;
	ArrayList<Answer> answers;
	ArrayList<EventCallback> event_callbacks;
	
	public Q_fields(){}
	
	@Override
	public String getQuestion_id() {
		return question_id;
	}
	@Override
	public String getQuestion_text() {
		return question_text;
	}
	@Override
	public String getType() {
		return type;
	}
	@Override
	public String getUrl() {
		return url;
	}
	@Override
	public String getRequester() {
		return requester;
	}
	@Override
	public HashMap<String,String> getExpandedRequester(String language) {
		Client client = ParallelInit.getClient();
		HashMap<String,String> result = new HashMap<String,String>(0);
		String url = this.getRequester();
		if (url == null || url.equals("")) return result;
		if (language != null && !language.equals("")) url+="?preferred_language="+language;
		try {
			WebResource webResource = client.resource(url);
			String text = "";
			text = webResource.type("text/plain").get(String.class);
			result = new JSONDeserializer<HashMap<String,String>>().use(null,HashMap.class).deserialize(text);
		} catch (Exception e){
			log.severe(e.toString());
			log.severe(e.getMessage());
		}
		return result;
	}
	@Override
	public HashMap<String,String> getExpandedRequester() {
		return getExpandedRequester(null);
	}
	@Override
	public ArrayList<Answer> getAnswers() {
		return answers;
	}
	@Override
	public ArrayList<EventCallback> getEvent_callbacks() {
		return event_callbacks;
	}
	@Override
	public void setQuestion_id(String question_id) {
		this.question_id = question_id;
	}
	@Override
	public void setQuestion_text(String question_text) {
		this.question_text = question_text;
	}
	@Override
	public void setType(String type) {
		this.type = type;
	}
	@Override
	public void setUrl(String url) {
		this.url = url;
	}
	@Override
	public void setAnswers(ArrayList<Answer> answers) {
		this.answers = answers;
	}
	@Override
	public void setEvent_callbacks(ArrayList<EventCallback> event_callbacks) {
		this.event_callbacks = event_callbacks;
	}
	@Override
	public void setRequester(String requester) {
		this.requester = requester;
	}

	@Override
	public String getQuestion_expandedtext(String language) {
		Client client = ParallelInit.getClient();
		String url = this.getQuestion_text();
		if (url == null || url.equals("")) return "";
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
