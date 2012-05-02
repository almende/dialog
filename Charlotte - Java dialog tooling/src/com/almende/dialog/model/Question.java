package com.almende.dialog.model;

import com.almende.dialog.model.impl.*;
import com.almende.dialog.model.intf.QuestionIntf;
import com.almende.tools.ParallelInit;
import com.eaio.uuid.UUID;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.WebResource;

import flexjson.JSON;
import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

public class Question implements QuestionIntf {
	private static final long serialVersionUID = -9069211642074173182L;
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	QuestionIntf question;
	private String preferred_language = "nl";

	public Question() {
		this.question = new Q_fields(); // Default to simple in-memory class
	}

	// Factory functions:
	@JSON(include = false)
	public static Question fromURL(String url) {
		Client client = ParallelInit.getClient();
		WebResource webResource = client.resource(url);
		String json = "";
		try {
			json = webResource.type("text/plain").get(String.class);
		} catch (ClientHandlerException e) {
			log.severe(e.toString());
		}
		return fromJSON(json);
	}

	@JSON(include = false)
	public static Question fromJSON(String json) {
		Question question = null;
		try {
			question = new JSONDeserializer<Question>().use(null,
					Question.class).deserialize(json);
		} catch (Exception e) {
			log.severe(e.toString());
		}
		return question;
	}

	// TODO: implement fromStorage/fromID/fromSessionState
	@JSON(include = false)
	public String toJSON() {
		return new JSONSerializer().exclude("*.class")
				.include("answers", "event_callbacks").serialize(this);
	}

	@JSON(include = false)
	public void generateIds() {
		if (this.getQuestion_id() == null || this.getQuestion_id().equals("")) {
			this.setQuestion_id(new UUID().toString());
		}
		for (Answer ans : this.getAnswers()) {
			if (ans.getAnswer_id() == null || ans.getAnswer_id().equals("")) {
				ans.setAnswer_id(new UUID().toString());
			}
		}
	}

	@JSON(include = false)
	public Question answer(String responder, String answer_id, String answer_input) {
		Client client = ParallelInit.getClient();
		Answer answer = null;
		if (this.getType().equals("open")) {
			answer = this.getAnswers().get(0); // TODO: error handling, what if
												// answer doesn't exist, or
												// multiple answers
												// (=out-of-spec)
		} else if (answer_id != null) {
			ArrayList<Answer> answers = question.getAnswers();
			for (Answer ans : answers) {
				if (ans.getAnswer_id().equals(answer_id)) {
					answer = ans;
					break;
				}
			}
		} else if (answer_input != null) {
			// check all answers of question to see if they match, possibly
			// retrieving the answer_text for each
			answer_input = answer_input.trim();
			ArrayList<Answer> answers = question.getAnswers();
			for (Answer ans : answers) {
				if (ans.getAnswer_text().equals(answer_input)) {
					answer = ans;
					break;
				}
			}
			if (answer == null) {
				for (Answer ans : answers) {
					if (ans.getAnswer_expandedtext(this.preferred_language).equalsIgnoreCase(
							answer_input)) {
						answer = ans;
						break;
					}
				}
			}
		}
		if (!this.getType().equals("comment") && answer == null) {
			// Oeps, couldn't find/handle answer, just repeat last question:
			// TODO: somewhat smarter behavior? Should dialog standard provide
			// error handling?
			return this;
		}
		Question newQ = null;
		// Send answer to answer.callback.
		if (!this.getType().equals("comment")) {
			WebResource webResource = client.resource(answer.getCallback());
			AnswerPost ans = new AnswerPost(null, this.getQuestion_id(),
					answer.getAnswer_id(), answer_input, responder);
			String post = new JSONSerializer().exclude("*.class")
					.serialize(ans);

			// Check if answer.callback gives new question for this dialog
			try {
				String s = webResource.type("application/json").post(
						String.class, post);

				newQ = new JSONDeserializer<Question>().use(null,
						Question.class).deserialize(s);
				newQ.setPreferred_language(preferred_language);
			} catch (Exception e) {
				log.severe(e.toString());
			}
		}
		return newQ;
	}

	// Getters/Setters:
	@Override
	public String getQuestion_id() {
		return question.getQuestion_id();
	}

	@Override
	public String getQuestion_text() {
		return question.getQuestion_text();
	}

	@Override
	public String getType() {
		return question.getType();
	}

	@Override
	public String getUrl() {
		return question.getUrl();
	}

	@Override
	public String getRequester() {
		return question.getRequester();
	}
	@Override
	@JSON(include = false)
	public HashMap<String,String> getExpandedRequester() {
		return question.getExpandedRequester(this.preferred_language);
	}
	@Override
	@JSON(include = false)
	public HashMap<String,String> getExpandedRequester(String language) {
		this.preferred_language=language;
		return question.getExpandedRequester(language);
	}	
	@Override
	public ArrayList<Answer> getAnswers() {
		return question.getAnswers();
	}

	@Override
	public ArrayList<EventCallback> getEvent_callbacks() {
		return question.getEvent_callbacks();
	}

	@Override
	@JSON(include = false)
	public String getQuestion_expandedtext() {
		return question.getQuestion_expandedtext(this.preferred_language);
	}

	@Override
	@JSON(include = false)
	public String getQuestion_expandedtext(String language) {
		this.preferred_language=language;
		return question.getQuestion_expandedtext(language);
	}

	@Override
	public void setQuestion_id(String question_id) {
		question.setQuestion_id(question_id);
	}

	@Override
	public void setQuestion_text(String question_text) {
		question.setQuestion_text(question_text);
	}

	@Override
	public void setType(String type) {
		question.setType(type);
	}

	@Override
	public void setUrl(String url) {
		question.setUrl(url);
	}
	@Override
	public void setRequester(String requester) {
		question.setRequester(requester);
	}

	@Override
	public void setAnswers(ArrayList<Answer> answers) {
		question.setAnswers(answers);
	}

	@Override
	public void setEvent_callbacks(ArrayList<EventCallback> event_callbacks) {
		question.setEvent_callbacks(event_callbacks);
	}

	@JSON(include = false)
	public String getPreferred_language() {
		return preferred_language;
	}

	@JSON(include = false)
	public void setPreferred_language(String preferred_language) {
		this.preferred_language = preferred_language;
	}




}
