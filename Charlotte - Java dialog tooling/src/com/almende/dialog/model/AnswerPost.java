package com.almende.dialog.model;

import java.io.Serializable;

public class AnswerPost implements Serializable {
	private static final long serialVersionUID = -5739888017486408094L;
	
	String dialog_id;
	String question_id;
	String answer_id;
	String answer_text;
	public AnswerPost(){}
	public AnswerPost(String dialog_id,String question_id,String answer_id,String answer_text){
		this.dialog_id = dialog_id;
		this.question_id = question_id;
		this.answer_id = answer_id;
		this.answer_text = answer_text;
	}
	public String getDialog_id() {
		return dialog_id;
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
	public void setDialog_id(String dialog_id) {
		this.dialog_id = dialog_id;
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
}