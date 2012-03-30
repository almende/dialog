package com.almende.dialog.model.intf;

import java.io.Serializable;
import java.util.ArrayList;

import com.almende.dialog.model.Answer;
import com.almende.dialog.model.EventCallback;

import flexjson.JSON;

public interface QuestionIntf extends Serializable {
	
	public String getQuestion_id();
	public String getQuestion_text();
	public String getType();
	public String getUrl();
	public ArrayList<Answer> getAnswers();
	public ArrayList<EventCallback> getEvent_callbacks();
	
	@JSON(include = false)
	public String getQuestion_expandedtext();
	
	public void setQuestion_id(String question_id);
	public void setQuestion_text(String question_text);
	public void setType(String type);
	public void setUrl(String url);
	public void setAnswers(ArrayList<Answer> answers);
	public void setEvent_callbacks(ArrayList<EventCallback> event_callbacks);
}
