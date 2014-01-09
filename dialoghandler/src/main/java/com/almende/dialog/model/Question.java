package com.almende.dialog.model;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.impl.Q_fields;
import com.almende.dialog.model.intf.QuestionIntf;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.almende.util.TypeUtil;
import com.almende.util.uuid.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.WebResource;

public class Question implements QuestionIntf {
	private static final long							serialVersionUID			= -9069211642074173182L;
	protected static final com.almende.dialog.Logger	dialogLog					= new com.almende.dialog.Logger();
	private static final Logger							log							= Logger.getLogger("DialogHandler");
	static final ObjectMapper							om							= ParallelInit
																							.getObjectMapper();
	
	public static final int								DEFAULT_MAX_QUESTION_LOAD	= 5;
	public static final String							MEDIA_PROPERTIES			= "media_properties";
	private static HashMap<String, Integer>				questionRetryCounter		= new HashMap<String, Integer>();
	
	QuestionIntf										question;
	private String										preferred_language			= "nl";
	
	private Collection<MediaProperty>					media_properties;
	
	public Question() {
		this.question = new Q_fields(); // Default to simple in-memory class
	}
	
	// Factory functions:
	@JsonIgnore
	public static Question fromURL(String url, String adapterID) {
		return fromURL(url, adapterID, "");
	}
	
	@JsonIgnore
	public static Question fromURL(String url, String adapterID, String remoteID) {
		return fromURL(url, adapterID, remoteID, "");
	}
	
    public static String getJSONFromURL( String url, String adapterID, String remoteID, String fromID )
    {
        Client client = ParallelInit.getClient();
        WebResource webResource;
        try
        {
            webResource = client.resource( url ).queryParam( "responder", URLEncoder.encode( remoteID, "UTF-8" ) )
                .queryParam( "requester", fromID );
            return webResource.type( "text/plain" ).get( String.class );
        }
        catch ( UnsupportedEncodingException e )
        {
            log.severe( e.toString() );
            dialogLog.severe( adapterID, "ERROR loading question: " + e.toString() );
        }
        return null;
    }
	
    @JsonIgnore
    public static Question fromURL( String url, String adapterID, String remoteID, String fromID )
    {
        log.info( String.format( "Trying to parse Question from URL: %s with remoteId: %s and fromId: %s", url,
            remoteID, fromID ) );
        if ( remoteID == null )
            remoteID = "";
        String json = "";
        if ( url != null && !url.trim().isEmpty() )
        {
            Client client = ParallelInit.getClient();
            WebResource webResource = client.resource( url );
            try
            {
                webResource = webResource.queryParam( "responder", URLEncoder.encode( remoteID, "UTF-8" ) ).queryParam(
                    "requester", URLEncoder.encode( fromID, "UTF-8" ) );
                dialogLog.info( adapterID, "Loading new question from: " + webResource.toString() );
                json = webResource.type( "text/plain" ).get( String.class );
                dialogLog.info( adapterID, "Received new question: " + json );
            }
            catch ( Exception e )
            {
                log.severe( e.toString() );
                dialogLog.severe( adapterID, "ERROR loading question: " + e.toString() );
                if ( questionRetryCounter.get( url ) == null )
                {
                    questionRetryCounter.put( url, 0 );
                }
                while ( questionRetryCounter.get( url ) != null
                    && questionRetryCounter.get( url ) < DEFAULT_MAX_QUESTION_LOAD )
                {
                    try
                    {
                        dialogLog.info(
                            adapterID,
                            String.format( "Fetch question from URL: %s failed. Trying again (Count: %s) ",
                                webResource.toString(), questionRetryCounter.get( url ) ) );
                        json = webResource.type( "text/plain" ).get( String.class );
                        dialogLog.info( adapterID, "Received new question: " + json );
                        break;
                    }
                    catch ( Exception ex )
                    {
                        Integer retryCount = questionRetryCounter.get( url );
                        questionRetryCounter.put( url, ++retryCount );
                    }
                }
            }
            questionRetryCounter.remove( url );
            return fromJSON( json, adapterID );
        }
        else
        {
            return null;
        }
    }
	
	@JsonIgnore
	public static Question fromJSON(String json, String adapterID) {
		Question question = null;
		if (json != null) {
			try {
				question = om.readValue(json, Question.class);
				// question.setQuestion_text( URLDecoder.decode(
				// question.getQuestion_text(), "UTF-8" ) );
			} catch (Exception e) {
				log.severe(e.toString());
				dialogLog.severe(adapterID,
						"ERROR parsing question: " + e.getLocalizedMessage());
			}
		}
		return question;
	}
	
	@JsonIgnore
	public String toJSON() {
		return toJSON(false);
	}
	
	@JsonIgnore
	public String toJSON(boolean expanded_texts) {
		try {
			return ServerUtils.serialize(this);
		} catch (Exception e) {
			e.printStackTrace();
			log.severe("Question serialization failed. Message: "
					+ e.getLocalizedMessage());
			return null;
		}
	}
	
	@JsonIgnore
	public void generateIds() {
		if (this.getQuestion_id() == null || this.getQuestion_id().equals("")) {
			this.setQuestion_id(new UUID().toString());
		}
		if (this.getAnswers() != null) {
			for (Answer ans : this.getAnswers()) {
				if (ans.getAnswer_id() == null || ans.getAnswer_id().equals("")) {
					ans.setAnswer_id(new UUID().toString());
				}
			}
		}
	}
	
	/**
	 * used to fetch an answer for a question.
	 * 
	 * @param responder
	 *            the responder address from the incoming request
	 * @param adapterID
	 *            the adapterId used for the communication
	 * @param answer_id
	 *            the answerId that must be picked if any. If empty, the
	 *            {@link answer_input} is matched
	 * @param answer_input
	 *            input given by the user
	 * @param sessionKey
	 *            valid only for VoiceServlet.
	 * @return
	 */
	@JsonIgnore
	public Question answer(String responder, String adapterID,
			String answer_id, String answer_input, String sessionKey) {
		boolean answered = false;
		Answer answer = null;
		if (this.getType().equals("open")) {
			// updated as part of bug#16 at
			// https://github.com/almende/dialog/issues/16
			// Infinitely prepares the same question when an invalid format of
			// Open question is received.
			ArrayList<Answer> answers = getAnswers();
			answer = answers != null ? answers.get(0) : null;
		} else if (this.getType().equals("openaudio")) {
			answer = this.getAnswers().get(0);
			try {
				answer_input = URLDecoder.decode(answer_input, "UTF-8");
				log.info("Received answer: " + answer_input);
			} catch (Exception e) {
				log.severe(e.getLocalizedMessage());
			}
		} else if (this.getType().equals("comment")
				|| this.getType().equals("referral")) {
			if (this.getAnswers() == null || this.getAnswers().size() == 0) return null;
			answer = this.getAnswers().get(0);
		} else if (answer_id != null) {
			answered = true;
			Collection<Answer> answers = question.getAnswers();
			for (Answer ans : answers) {
				if (ans.getAnswer_id().equals(answer_id)) {
					answer = ans;
					break;
				}
			}
		} else if (answer_input != null) {
			answered = true;
			// check all answers of question to see if they match, possibly
			// retrieving the answer_text for each
			answer_input = answer_input.trim();
			ArrayList<Answer> answers = question.getAnswers();
			for (Answer ans : answers) {
				if (ans.getAnswer_text() != null
						&& ans.getAnswer_text().equals(answer_input)) {
					answer = ans;
					break;
				}
			}
			if (answer == null) {
				for (Answer ans : answers) {
					if (ans.getAnswer_expandedtext(this.preferred_language)
							.equalsIgnoreCase(answer_input)) {
						answer = ans;
						break;
					}
				}
			}
			if (answer == null) {
				try {
					int answer_nr = Integer.parseInt(answer_input);
					if (answer_nr <= answers.size()) answer = answers
							.get(answer_nr - 1);
				} catch (NumberFormatException ex) {
					log.severe(ex.getLocalizedMessage());
				}
			}
		} else if (answer_input == null) {
			return this;
		}
		
		Question newQ = null;
		if (!this.getType().equals("comment") && answer == null) {
			// Oeps, couldn't find/handle answer, just repeat last question:
			// TODO: somewhat smarter behavior? Should dialog standard provide
			// error handling?
			if (answered) newQ = this.event("exception",
					"Wrong answer received", null, responder);
			if (newQ != null) return newQ;
			
			return retryLoadingQuestion(adapterID, answer_input, sessionKey);
		} else {
			// flush any existing retry counters for this session
			flushRetryCount(sessionKey);
		}
		
		// Send answer to answer.callback.
		Client client = ParallelInit.getClient();
		WebResource webResource = client.resource(answer.getCallback());
		AnswerPost ans = new AnswerPost(this.getQuestion_id(),
				answer.getAnswer_id(), answer_input, responder);
		ans.getExtras().put("adapterId", adapterID);
		// Check if answer.callback gives new question for this dialog
		try {
			String post = om.writeValueAsString(ans);
			log.info("Going to send: " + post);
			String newQuestionJSON = null;
            newQuestionJSON = webResource.type( "application/json" ).post( String.class, post );
			
			log.info("Received new question (answer): " + newQuestionJSON);
			dialogLog.info(adapterID, "Received new question (answer): "
					+ newQuestionJSON);
			
			newQ = om.readValue(newQuestionJSON, Question.class);
			newQ.setPreferred_language(preferred_language);
		} catch (ClientHandlerException ioe) {
			dialogLog.severe(adapterID,
					"Unable to load question: " + ioe.getMessage());
			log.severe(ioe.toString());
			ioe.printStackTrace();
			newQ = this.event("exception", "Unable to load question", null,
					responder);
		} catch (Exception e) {
			dialogLog.severe(adapterID,
					"Unable to parse question json: " + e.getMessage());
			log.severe(e.toString());
			newQ = this.event("exception", "Unable to parse question json",
					null, responder);
		}
		return newQ;
	}
	
	public Question event(String eventType, String message, Object extras,
			String responder) {
		log.info(String.format(
				"Received: %s Message: %s Responder: %s Extras: %s", eventType,
				message, responder,
				ServerUtils.serializeWithoutException(extras)));
		Client client = ParallelInit.getClient();
		ArrayList<EventCallback> events = this.getEvent_callbacks();
		EventCallback eventCallback = null;
		if (events != null) {
			for (EventCallback e : events) {
				if (e.getEvent().toLowerCase().equals(eventType)) {
					eventCallback = e;
					break;
				}
			}
		}
		
		if (eventCallback == null) {
			// Oeps, couldn't find/handle event, just repeat last question:
			// TODO: somewhat smarter behavior? Should dialog standard provide
			// error handling?
			if (eventType.equals("hangup") || eventType.equals("exception")
					|| this.question.getType().equals("referral")) {
				return null;
			} else if (extras != null && extras instanceof Map) {
				TypeUtil<HashMap<String, String>> injector = new TypeUtil<HashMap<String, String>>() {
				};
				HashMap<String, String> extrasMap = injector.inject(extras);
				retryLoadingQuestion(null, null, extrasMap.get("sessionKey"));
			} else {
				log.warning("Unguardedly repeating question!");
				return this;
			}
		}
		
		Question newQ = null;
		WebResource webResource = client.resource(eventCallback.getCallback());
		EventPost event = new EventPost(responder, this.getQuestion_id(),
				eventType, message, extras);
		try {
			String post = om.writeValueAsString(event);
			String s = webResource.type("application/json").post(String.class,
					post);
			
			log.info("Received new question (event): " + s);
			
			if (s != null && !s.equals("")) {
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
	public HashMap<String, String> getExpandedRequester() {
		return question.getExpandedRequester(this.preferred_language);
	}
	
	@Override
	@JsonIgnore
	public HashMap<String, String> getExpandedRequester(String language) {
		this.preferred_language = language;
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
	public String getQuestion_expandedtext() {
		return question.getQuestion_expandedtext(this.preferred_language);
	}
	
	@Override
	@JsonIgnore
	public String getQuestion_expandedtext(String language) {
		this.preferred_language = language;
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
	
	public String getPreferred_language() {
		return preferred_language;
	}
	
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
	
	@JsonProperty("media_properties")
	public Collection<MediaProperty> getMedia_properties() {
		return media_properties;
	}
	
	@JsonProperty("media_properties")
	public void setMedia_Properties(Collection<MediaProperty> media_properties) {
		this.media_properties = media_properties;
	}
	
	@JsonIgnore
	public Map<MediaPropertyKey, String> getMediaPropertyByType(MediumType type) {
		return getMediaPropertyByType(this.media_properties, type);
	}
	
	public String getMediaPropertyValue(MediumType type, MediaPropertyKey key) {
		return getMediaPropertyValue(this.media_properties, type, key);
	}
	
	/**
	 * fetches the first MediaProperty value from teh collection of
	 * media_properties based on the type and key
	 * 
	 * @param media_properties
	 * @param type
	 * @param key
	 * @return
	 */
	public static String getMediaPropertyValue(
			Collection<MediaProperty> media_properties, MediumType type,
			MediaPropertyKey key) {
		Map<MediaPropertyKey, String> properties = getMediaPropertyByType(
				media_properties, type);
		if (properties != null) {
			if (properties.containsKey(key)) {
				return properties.get(key);
			}
		}
		return null;
	}
	
	@JsonIgnore
	public static Map<MediaPropertyKey, String> getMediaPropertyByType(
			Collection<MediaProperty> media_properties, MediumType type) {
		if (media_properties != null) {
			for (MediaProperty mediaProperties : media_properties) {
				if (mediaProperties.getMedium().equals(type)) {
					return mediaProperties.getProperties();
				}
			}
		}
		return null;
	}
	
	public void addMedia_Properties(MediaProperty mediaProperty) {
		media_properties = media_properties == null ? new ArrayList<MediaProperty>()
				: media_properties;
		media_properties.add(mediaProperty);
	}
	
	/**
	 * fetches the first event which matches the parameter
	 * 
	 * @param event
	 * @return
	 */
	public EventCallback getEventCallback(String event) {
		ArrayList<EventCallback> event_callbacks = getEvent_callbacks();
		if (event_callbacks != null) {
			for (EventCallback eventCallback : event_callbacks) {
				if (eventCallback.getEvent().equals(event)) {
					return eventCallback;
				}
			}
		}
		return null;
	}
	
	public void addEventCallback(String eventId, String event, String callback) {
		if (question.getEvent_callbacks() == null) {
			question.setEvent_callbacks(new ArrayList<EventCallback>());
		}
		EventCallback eventCallback = new EventCallback();
		eventCallback.setEvent(event);
		eventCallback.setEvent_id(eventId);
		eventCallback.setCallback(callback);
		question.getEvent_callbacks().add(eventCallback);
	}
	
	/**
	 * gets the retry Count of the number of times the Question has been loaded
	 * corresponding to the
	 * current sessionKey. If the sessionKey is not found, it will create one
	 * and set the value to 0.
	 * 
	 * @param sessionKey
	 * @return
	 */
	public static Integer getRetryCount(String sessionKey) {
		questionRetryCounter = questionRetryCounter != null ? questionRetryCounter
				: new HashMap<String, Integer>();
		if (sessionKey != null && !questionRetryCounter.containsKey(sessionKey)) {
			questionRetryCounter.put(sessionKey, 1);
		}
		return sessionKey != null ? questionRetryCounter.get(sessionKey) : null;
	}
	
	/**
	 * updates The retryCount based on the sessionKey.
	 * If sessionKey is null and questionRetryCounter has only one element, then
	 * increments it
	 * 
	 * @param sessionKey
	 * @return
	 */
	public static Integer updateRetryCount(String sessionKey) {
		Integer retryCount = getRetryCount(sessionKey);
		try {
			log.info("serialized retry map: "
					+ ServerUtils.serialize(questionRetryCounter));
		} catch (Exception e) {
			log.info("serialized retry map failed");
		}
		if (retryCount != null && questionRetryCounter != null) {
			questionRetryCounter.put(sessionKey, ++retryCount);
		}
		return retryCount;
	}
	
	/**
	 * flushes the retry count corresponding to hte session key in the
	 * questionRetryCounter
	 * 
	 * @param sessionKey
	 */
	public static void flushRetryCount(String sessionKey) {
		if (questionRetryCounter != null && sessionKey != null) {
			questionRetryCounter.remove(sessionKey);
		}
	}
	
	/**
	 * (intended for VOICE) simple retry mechanism to reload the question
	 * finitely.
	 * 
	 * @param adapterID
	 * @param answer_input
	 * @param sessionKey
	 * @return
	 */
	protected Question retryLoadingQuestion(String adapterID,
			String answer_input, String sessionKey) {
		String retryLoadLimit = getMediaPropertyValue(MediumType.BROADSOFT,
				MediaPropertyKey.RETRY_LIMIT);
		Integer retryCount = getRetryCount(sessionKey);
		if (retryLoadLimit != null && retryCount != null
				&& retryCount < Integer.parseInt(retryLoadLimit)) {
			updateRetryCount(sessionKey);
			log.info(String
					.format("returning the same question as RetryCount: %s < RetryLoadLimit: %s",
							retryCount, retryLoadLimit));
			if (adapterID != null && answer_input != null) {
				dialogLog
						.warning(
								adapterID,
								String.format(
										"Repeating question %s (count: %s) due to invalid answer: %s",
										ServerUtils
												.serializeWithoutException(this),
										retryCount, answer_input));
			}
			return this;
		} else if (retryCount != null && retryCount < DEFAULT_MAX_QUESTION_LOAD) {
			updateRetryCount(sessionKey);
			log.info(String
					.format("returning the same question as RetryCount: %s < DEFAULT_MAX: %s",
							retryCount, DEFAULT_MAX_QUESTION_LOAD));
			if (adapterID != null && answer_input != null) {
				dialogLog
						.warning(
								adapterID,
								String.format(
										"Repeating question %s (count: %s) due to invalid answer: %s",
										ServerUtils
												.serializeWithoutException(this),
										retryCount, answer_input));
			}
			return this;
		} else if (sessionKey != null) {
			flushRetryCount(sessionKey);
			log.warning(String
					.format("return null question as RetryCount: %s >= DEFAULT_MAX: %s or >= LOAD_LIMIT: %s",
							retryCount, DEFAULT_MAX_QUESTION_LOAD,
							retryLoadLimit));
			if (adapterID != null && answer_input != null) {
				dialogLog
						.warning(
								adapterID,
								String.format(
										"Return empty/null question as retryCount %s equals DEFAULT_MAX: %s or LOAD_LIMIT: %s, due to invalid answer: %s",
										retryCount, DEFAULT_MAX_QUESTION_LOAD,
										retryLoadLimit, answer_input));
			}
			return null;
		} else {
			log.info("returning the same question as its TextServlet request??");
			dialogLog
					.warning(
							adapterID,
							String.format(
									"Repeating question %s due to invalid answer: %s in the TextServlet",
									ServerUtils.serializeWithoutException(this),
									answer_input));
			return this;
		}
	}
}