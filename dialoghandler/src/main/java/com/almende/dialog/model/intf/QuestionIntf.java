package com.almende.dialog.model.intf;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.EventCallback;
import com.almende.dialog.model.Session;
import com.fasterxml.jackson.annotation.JsonIgnore;

public interface QuestionIntf extends Serializable {
	
	public String getQuestion_id();
	public String getQuestion_text();
	public String getType();
	public List<String> getUrl();
	public String getRequester();
	public String getData();
	public String getTrackingToken();
	
	@JsonIgnore
	public HashMap<String,String> getExpandedRequester(Session session);
	@JsonIgnore
	public HashMap<String,String> getExpandedRequester(String language, Session session);
	
	public ArrayList<Answer> getAnswers();
	public ArrayList<EventCallback> getEvent_callbacks();
	
	@JsonIgnore
	public String getQuestion_expandedtext(Session session);
	@JsonIgnore
	public String getQuestion_expandedtext(String language, Session session);
	
	public void setQuestion_id(String question_id);
	public void setQuestion_text(String question_text);
	public void setType(String type);
	public void setUrl(Object url);
	public void setData(String data);
	public void setTrackingToken(String token);
	public void setRequester(String requester);
	public void setAnswers(ArrayList<Answer> answers);
	public void setEvent_callbacks(ArrayList<EventCallback> event_callbacks);
}
