package com.almende.dialog.model.intf;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import flexjson.JSON;

public interface AnswerIntf extends Serializable {
	public String getAnswer_id();
	public String getAnswer_text();
	public String getCallback();
	
	@JSON(include = false)
	@JsonIgnore
	public String getAnswer_expandedtext();
	@JSON(include = false)
	@JsonIgnore
	public String getAnswer_expandedtext(String language);
	
	public void setAnswer_id(String answer_id);
	public void setAnswer_text(String answer_text);
	public void setCallback(String callback);
}
