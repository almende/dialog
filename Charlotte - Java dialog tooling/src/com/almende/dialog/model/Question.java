package com.almende.dialog.model;

import com.almende.dialog.model.impl.*;
import com.almende.dialog.model.intf.QuestionIntf;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.WebResource;

import flexjson.JSON;
import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;

import java.util.ArrayList;
import java.util.logging.Logger;

public class Question implements QuestionIntf {
	private static final long serialVersionUID = -9069211642074173182L;
	private static final Logger log = Logger.getLogger(com.almende.dialog.model.Question.class.getName()); 	
	
	QuestionIntf question;
	
	public Question() {
		this.question = new Q_fields(); //Default to simple in-memory class 
	}
	
	//Factory functions:
	@JSON(include = false)
	public static Question fromURL(String url){
		Client client = ClientCon.client;
		WebResource webResource = client.resource(url);
		String json = "";
		try {
			json = webResource.type("text/plain").get(String.class);
		} catch (ClientHandlerException e){
			log.severe(e.toString());
		}
		return fromJSON(json);
	}
	@JSON(include = false)
	public static Question fromJSON(String json){
		Question question = null;
		try {
			question = new JSONDeserializer<Question>().
					use(null, Question.class).
					deserialize(json);
		} catch (Exception e){
			log.severe(e.toString());
		}
		return question;
	}
	//TODO: implement fromStorage/fromID/fromSessionState
	@JSON(include = false)
	public String toJSON(){
		return new JSONSerializer().
				   exclude("*.class").
				   include("answers","event_callbacks").
				   serialize(this);
	}
	
	@JSON(include = false)
	public Question answer(String answer_id, String answer_input){
		Client client = ClientCon.client;
		Answer answer = null;
		if (this.getType().equals("open")){
			answer = this.getAnswers().get(0); //TODO: error handling, what if answer doesn't exist, or multiple answers (=out-of-spec)
		} else if (answer_id != null){
			ArrayList<Answer> answers = question.getAnswers();
			for (Answer ans : answers){
				if (ans.getAnswer_id().equals(answer_id)) {
					answer = ans;
					break;
				}
			}
		} else if (answer_input != null){
			//check all answers of question to see if they match, possibly retrieving the answer_text for each
			ArrayList<Answer> answers = question.getAnswers();
			for (Answer ans : answers){
				if (ans.getAnswer_text().equals(answer_input)) {
					answer = ans;
					break;
				}
			}
			if (answer == null){
				for (Answer ans: answers){
					if (ans.getAnswer_expandedtext().equalsIgnoreCase(answer_input)){
						answer = ans;
						break;
					}
				}
			}
		}
		if (!this.getType().equals("comment") && answer == null){
			//Oeps, couldn't find/handle answer, just repeat last question:
			//TODO: somewhat smarter behavior? Should dialog standard provide error handling?
			return this;
		}
		Question newQ = null;
		//Send answer to answer.callback.
		if (!this.getType().equals("comment")){
			WebResource webResource = client.resource(answer.getCallback());
			AnswerPost ans = new AnswerPost(null,this.getQuestion_id(),answer.getAnswer_id(),answer_input);
			String post = new JSONSerializer().
				   exclude("*.class").
				   serialize(ans);
		
			//Check if answer.callback gives new question for this dialog
			try {
				String s = webResource.type("application/json").post(String.class,post );
			
				newQ = new JSONDeserializer<Question>().
						use(null, Question.class).
						deserialize(s);
			} catch (Exception e){
				log.severe(e.toString());
			}
		}
		return newQ;
	}
	
	//Getters/Setters:
	public String getQuestion_id() { return question.getQuestion_id(); }
	public String getQuestion_text() { return question.getQuestion_text(); }
	public String getType() { return question.getType(); }
	public String getUrl() { return question.getUrl(); }
	public ArrayList<Answer> getAnswers() { return question.getAnswers(); }
	public ArrayList<EventCallback> getEvent_callbacks() { return question.getEvent_callbacks(); }
	
	@JSON(include = false)
	public String getQuestion_expandedtext() { return question.getQuestion_expandedtext(); }

	public void setQuestion_id(String question_id) { question.setQuestion_id(question_id); }
	public void setQuestion_text(String question_text) { question.setQuestion_text(question_text); }
	public void setType(String type) { question.setType(type); }
	public void setUrl(String url) { question.setUrl(url); }
	public void setAnswers(ArrayList<Answer> answers) { question.setAnswers(answers); }
	public void setEvent_callbacks(ArrayList<EventCallback> event_callbacks) { question.setEvent_callbacks(event_callbacks); }
}
