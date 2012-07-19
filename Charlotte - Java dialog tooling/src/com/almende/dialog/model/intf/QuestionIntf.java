package com.almende.dialog.model.intf;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import com.almende.dialog.model.Answer;
import com.almende.dialog.model.EventCallback;

import flexjson.JSON;

public interface QuestionIntf extends Serializable {
	
	public String getQuestion_id();
	public String getQuestion_text();
	public String getType();
	public String getUrl();
	public String getRequester();
	public String getData();
	
	@JSON(include = false)
	public HashMap<String,String> getExpandedRequester();
	@JSON(include = false)
	public HashMap<String,String> getExpandedRequester(String language);
	
	public ArrayList<Answer> getAnswers();
	public ArrayList<EventCallback> getEvent_callbacks();
	
	@JSON(include = false)
	public String getQuestion_expandedtext();
	@JSON(include = false)
	public String getQuestion_expandedtext(String language);
	
	public void setQuestion_id(String question_id);
	public void setQuestion_text(String question_text);
	public void setType(String type);
	public void setUrl(String url);
	public void setData(String data);
	public void setRequester(String requester);
	public void setAnswers(ArrayList<Answer> answers);
	public void setEvent_callbacks(ArrayList<EventCallback> event_callbacks);
}
