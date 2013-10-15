package com.almende.dialog.model;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.almende.dialog.TestFramework;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.impl.Q_fields;
import com.almende.dialog.model.intf.QuestionIntf;
import com.almende.dialog.util.QuestionTextTransformer;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.eaio.uuid.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.thetransactioncompany.cors.HTTPMethod;

import flexjson.JSON;
import flexjson.JSONSerializer;

public class Question implements QuestionIntf {
	private static final long serialVersionUID = -9069211642074173182L;
	protected static final com.almende.dialog.Logger dialogLog =  new com.almende.dialog.Logger();
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	static final ObjectMapper om =ParallelInit.getObjectMapper();
	
	QuestionIntf question;
	private String preferred_language = "nl";

    private Collection<MediaProperty> media_properties;

	public Question() {
		this.question = new Q_fields(); // Default to simple in-memory class
	}

	// Factory functions:
	@JSON(include = false)
	@JsonIgnore
	public static Question fromURL(String url,String adapterID) {
		return fromURL(url, adapterID,"");
	}
	@JSON(include = false)
	public static Question fromURL(String url,String adapterID,String remoteID)  {
		return fromURL(url, adapterID, remoteID, "");
	}
	@JSON(include = false)
	public static Question fromURL(String url,String adapterID,String remoteID,String fromID)  {
            
	    log.info( String.format( "Trying to parse Question from URL: %s with remoteId: %s and fromId: %s", url, remoteID, fromID  ));
	    if(remoteID==null)
	        remoteID="";
        String json = "";            
        if( !ServerUtils.isInUnitTestingEnvironment() )
        {
            Client client = ParallelInit.getClient();
            WebResource webResource = client.resource(url);
			try {
				webResource = webResource.queryParam("responder", URLEncoder.encode(remoteID, "UTF-8")).queryParam("requester", URLEncoder.encode(fromID, "UTF-8"));
				dialogLog.info(adapterID,"Loading new question from: "+webResource.toString());
				json = webResource.type("text/plain").get(String.class);
				dialogLog.info(adapterID,"Received new question: "+json);
			} catch (ClientHandlerException e) {
				log.severe(e.toString());
				dialogLog.severe(adapterID,"ERROR loading question: "+e.toString());
			} catch (UniformInterfaceException e) {
				log.severe(e.toString());
				dialogLog.severe(adapterID,"ERROR loading question: "+e.toString());
			} catch (UnsupportedEncodingException e) {
				log.severe(e.toString());
				dialogLog.severe(adapterID,"ERROR loading question: "+e.toString());
			}
        }
        else
        {
            try
            {
                url = ServerUtils.getURLWithQueryParams( url, "responder", URLEncoder.encode(remoteID, "UTF-8") );
                url = ServerUtils.getURLWithQueryParams( url, "requester", URLEncoder.encode(fromID, "UTF-8") );
                json = TestFramework.fetchResponse( HTTPMethod.GET, url, null );
            }
            catch ( UnsupportedEncodingException e )
            {
                log.severe(e.toString());
            }
        }
        return fromJSON(json, adapterID);
	}

	@JSON(include = false)
	public static Question fromJSON(String json, String adapterID) {
		Question question = null;
		if(json!=null) {
			try {
				question = om.readValue(json, Question.class);
				question.setQuestion_text( URLDecoder.decode( question.getQuestion_text(), "UTF-8" ) );
			    log.info( "question from JSON: %s" + json );	
			} catch (Exception e) {
				log.severe(e.toString());
				dialogLog.severe(adapterID,"ERROR parsing question: "+e.getLocalizedMessage());
			}
		}
		return question;
	}

	@JSON(include = false)
	@JsonIgnore
	public String toJSON() {
		return toJSON(false);
	}
	
	@JSON(include = false)
	@JsonIgnore
	public String toJSON(boolean expanded_texts) {
		return new JSONSerializer().exclude("*.class").transform(new QuestionTextTransformer(expanded_texts), "question_text", "question_expandedtext", "answer_text", "answer_expandedtext", "answers.answer_text", "answers.answer_expandedtext")
				.include("answers", "event_callbacks").serialize(this);
	}

	@JsonIgnore
	@JSON(include = false)
	public void generateIds() {
		if (this.getQuestion_id() == null || this.getQuestion_id().equals("")) {
			this.setQuestion_id(new UUID().toString());
		}
		if(this.getAnswers()!=null) {
			for (Answer ans : this.getAnswers()) {
				if (ans.getAnswer_id() == null || ans.getAnswer_id().equals("")) {
					ans.setAnswer_id(new UUID().toString());
				}
			}
		}
	}

	@JsonIgnore
	@JSON(include = false)
	public Question answer(String responder, String adapterID, String answer_id, String answer_input) {
		Client client = ParallelInit.getClient();
		boolean answered=false;
		Answer answer = null;
		if (this.getType().equals("open")) {
			answer = this.getAnswers().get(0); // TODO: error handling, what if
												// answer doesn't exist, or
												// multiple answers
												// (=out-of-spec)
		} else if(this.getType().equals("openaudio")) {
			answer = this.getAnswers().get(0);
			try {
				answer_input = URLDecoder.decode(answer_input, "UTF-8");
				log.info("Received answer: "+answer_input);
			} catch (Exception e) {
			}
		} else if (this.getType().equals("comment") || this.getType().equals("referral")) {
			if(this.getAnswers() == null || this.getAnswers().size()==0)
				return null;
			answer = this.getAnswers().get(0);
			
		} else if (answer_id != null) {
			answered=true;
			Collection<Answer> answers = question.getAnswers();
			for (Answer ans : answers) {
				if (ans.getAnswer_id().equals(answer_id)) {
					answer = ans;
					break;
				}
			}
		} else if (answer_input != null) {
			answered=true;
			// check all answers of question to see if they match, possibly
			// retrieving the answer_text for each
			answer_input = answer_input.trim();
			ArrayList<Answer> answers = question.getAnswers();
			for (Answer ans : answers) {
				if (ans.getAnswer_text()!=null && ans.getAnswer_text().equals(answer_input)) {
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
			if (answer == null) {
				try {
					int answer_nr = Integer.parseInt(answer_input);
					if(answer_nr <= answers.size())
						answer = answers.get(answer_nr-1);
				} catch(NumberFormatException ex) {
					
				}
			}
		}
		Question newQ = null;
		if (!this.getType().equals("comment") && answer == null) {
			// Oeps, couldn't find/handle answer, just repeat last question:
			// TODO: somewhat smarter behavior? Should dialog standard provide
			// error handling?
			if(answered)
				newQ = this.event("exception","Wrong answer received", null, responder);
			if(newQ!=null)
				return newQ;
			
			return this;
		}
		// Send answer to answer.callback.
		WebResource webResource = client.resource(answer.getCallback());
		AnswerPost ans = new AnswerPost(this.getQuestion_id(),
				answer.getAnswer_id(), answer_input, responder);
		// Check if answer.callback gives new question for this dialog
		try {
			String post = om.writeValueAsString(ans);
			log.info("Going to send: "+post);
			String newQuestionJSON = null;
			if( !ServerUtils.isInUnitTestingEnvironment() )
			{
			    newQuestionJSON = webResource.type("application/json").post(String.class, post);
			}
			else
			{
			    newQuestionJSON = TestFramework.fetchResponse( HTTPMethod.POST, answer.getCallback(), post );
			}
			
			log.info("Received new question (answer): "+ newQuestionJSON);
			dialogLog.info(adapterID, "Received new question (answer): "+newQuestionJSON);
			
			newQ = om.readValue(newQuestionJSON, Question.class);
			newQ.setPreferred_language(preferred_language);
		} catch (ClientHandlerException ioe) {
			dialogLog.severe(adapterID, "Unable to load question: "+ioe.getMessage());
			log.severe(ioe.toString());
			ioe.printStackTrace();
			newQ = this.event("exception", "Unable to load question", null, responder);
		} catch (Exception e) {
			dialogLog.severe(adapterID, "Unable to parse question json: "+e.getMessage());
			log.severe(e.toString());
			newQ = this.event("exception", "Unable to parse question json", null, responder);
		}
		return newQ;
	}
	
	public Question event(String eventType, String message, Object extras, String responder) 
	{
        log.info( String.format( "Received: %s Message: %s Responder: %s", eventType, message,
                                 responder ) );
		Client client = ParallelInit.getClient();
		ArrayList<EventCallback> events = this.getEvent_callbacks();
		EventCallback eventCallback=null;
		if(events!=null) {
			for(EventCallback e : events) {
				if(e.getEvent().toLowerCase().equals(eventType)){
					eventCallback = e;
					break;
				}
			}
		}
		
		if(eventCallback==null) {
			// Oeps, couldn't find/handle event, just repeat last question:
			// TODO: somewhat smarter behavior? Should dialog standard provide
			// error handling?
			if(eventType.equals("hangup") || eventType.equals("exception") || this.question.getType().equals("referral") ) {
				return null;
			}
			
			return this;
		}
		Question newQ = null;
		WebResource webResource = client.resource(eventCallback.getCallback());
		EventPost event = new EventPost(responder, this.getQuestion_id(), eventType, message, extras);
		try {
			String post = om.writeValueAsString(event);
			String s = webResource.type("application/json").post(
					String.class, post);
			
			log.info("Received new question (event): "+s);

			if(s!=null && !s.equals("")) { 
				newQ = om.readValue(s, Question.class);
				newQ.setPreferred_language(preferred_language);
			}
		} catch (Exception e) {
			log.severe(e.toString());
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
	@JsonIgnore
	@JSON(include = false)
	public HashMap<String,String> getExpandedRequester() {
		return question.getExpandedRequester(this.preferred_language);
	}
	@Override
	@JsonIgnore
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
	@JsonIgnore
	@JSON(include = false)
	public String getQuestion_expandedtext() {
		return question.getQuestion_expandedtext(this.preferred_language);
	}

	@Override
	@JsonIgnore
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
	@JsonIgnore
	public String getPreferred_language() {
		return preferred_language;
	}

	@JSON(include = false)
	@JsonIgnore
	public void setPreferred_language(String preferred_language) {
		this.preferred_language = preferred_language;
	}

	@Override
	public String getData() {
		return this.question.getData();
	}

	@Override
	public void setData(String data) {
		this.question.setData(data);
		
	}

	@Override
	public String getTrackingToken() {
		return this.question.getTrackingToken();
	}

	@Override
	public void setTrackingToken(String token) {
		this.question.setTrackingToken(token);
	}

    public Collection<MediaProperty> getMedia_properties()
    {
        return media_properties;
    }
    
    @JSON(include = false)
    public Map<MediaPropertyKey, String> getMediaPropertyByType( MediumType type ) {

        if(this.media_properties!=null) {
            for ( MediaProperty mediaProperties : this.media_properties )
            {
                if ( mediaProperties.getMedium().equals(type) )
                {
                    return mediaProperties.getProperties();
                }
            }
        }
        return null;
    }
    
    public String getMediaPropertyValue( MediumType type, MediaPropertyKey key) {

        Map<MediaPropertyKey, String> properties = getMediaPropertyByType(type);
        if(properties!=null) {
            if(properties.containsKey(key)) {
                return properties.get( key );
            }
        }
        return null;
    }

    public void setMedia_Properties( Collection<MediaProperty> media_properties )
    {
        this.media_properties = media_properties;
    }

    public void addMedia_Properties( MediaProperty mediaProperty )
    {
        media_properties = media_properties == null ? new ArrayList<MediaProperty>() : media_properties;
        media_properties.add( mediaProperty );
    }
}