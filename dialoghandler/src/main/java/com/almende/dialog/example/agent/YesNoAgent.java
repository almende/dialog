package com.almende.dialog.example.agent;

import java.io.Serializable;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.Settings;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.util.ParallelInit;
import com.almende.util.twigmongo.QueryResultIterator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("yesno")
public class YesNoAgent {
	static final ObjectMapper om =ParallelInit.getObjectMapper();
	private static final String URL = "http://"+Settings.HOST+"/yesno/";
	private static final String SOUNDURL = "http://ask4604.ask46.customers.luna.net/rest/";
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	
	public Question getQuestion(int question_no, String preferred_medium, String phonenumber) {
		
		String questionURL = URL+"questions/"+question_no;
		String answerYesURL = URL+"answers/0";
		String answerNoURL = URL+"answers/1";
		
		if (preferred_medium != null && preferred_medium.startsWith("audio")){
			questionURL = this.getAudioFile(question_no);
			answerYesURL= SOUNDURL+"14.wav";
			answerNoURL= SOUNDURL+"14.wav";
		}
		
		Question question=new Question();
		question.setRequester(URL+"id/");
		question.setType("closed");
		question.setQuestion_text(questionURL);
		
		question.setAnswers(new ArrayList<Answer>(Arrays.asList(
				new Answer(answerYesURL, URL+"questions/"+question_no+"?preferred_medium="+preferred_medium+"&pn="+phonenumber+"&answer=yes"),
				new Answer(answerNoURL, URL+"questions/"+question_no+"?preferred_medium="+preferred_medium+"&pn="+phonenumber+"&answer=no"))));
		
		return question;
	}
	
	@GET
	@Path("/id/")
	public Response getId(@QueryParam("preferred_language") String preferred_language){
		ObjectNode node= om.createObjectNode();
		node.put("url", URL);
		node.put("nickname", "YesNo");
		
		return Response.ok(node.toString()).build();
	}
	
	@GET
	@Produces("application/json")
	public Response firstQuestion(@QueryParam("preferred_medium") String preferred_medium, @QueryParam("remoteAddress") String responder, @QueryParam("requester") String requester){
				
		int questionNo=0;
		if(requester.contains("live") || requester.contains("0107421217")){
			questionNo=1;
		}
		try {
			responder = URLDecoder.decode(responder, "UTF-8");
		} catch (Exception ex) {
			log.severe(ex.getMessage());
		}
		
		Question question = getQuestion(questionNo, preferred_medium, responder);
		return Response.ok(question.toJSON()).build();
	}
	
	@Path("/questions/{question_no}")
	@POST
	@Produces("application/json")
	@Consumes("*/*")
	public Response answerQuestion(@PathParam("question_no") String question_no, @QueryParam("preferred_medium") String preferred_medium,
										@QueryParam("pn") String phonenumber, @QueryParam("answer") String answer){
		
		Group group = this.getGroup("Group."+question_no+"."+answer);
		group.addMember(phonenumber);
		
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		datastore.store(group);
		
		int responseQuestion=99;
		
		String questionURL = URL+"questions/"+responseQuestion;
		if (preferred_medium != null && preferred_medium.startsWith("audio")){
			questionURL = this.getAudioFile(responseQuestion);
		}
		
		Question question=new Question();
		question.setRequester(URL+"id/");
		question.setType("comment");
		question.setQuestion_text(questionURL);
		
		return Response.ok( question.toJSON() ).build();
	}
	
	@Path("/questions/{question_no}")
	@GET
	@Produces("text/plain")
	@Consumes("*/*")
	public  Response getQuestionText(@PathParam("question_no") String question_no ){
		Integer questionNo = Integer.parseInt(question_no);
		String result = "";
		
		
		// These messages are now static but should be loaded from the LifeRay Database.
		switch (questionNo){
			case 0: result="Press 1 if you are available, press 2 if you are unavailable."; break;
			case 1: result="Are you available?"; break;
			case 99: result="Thank you for your input"; break;
			default: result="Sorry, for some strange reason I don't have that question text available...";
		}
		return Response.ok(result).build();
	}
	
	@Path("/answers/{answer_no}")
	@GET
	@Produces("text/plain")
	@Consumes("*/*")
	public Response getAnswerText(@PathParam("answer_no") String answer_no, @QueryParam("preferred_medium") String prefered_mimeType){
		Integer answerNo = Integer.parseInt(answer_no);
		String result="";
		
		// These messages can be static, because they are always the same.
		switch (answerNo){
			case 0: result="Yes"; break;
			case 1: result="No"; break;
			default: result="Sorry, for some strange reason I don't have that answer text available...";
		}
		return Response.ok(result).build();
	}
	
	// This urls will present the results
	@Path("result")
	@GET
	public Response getResults() {
		String result="";
		ArrayList<Group> groups = (ArrayList<Group>) this.getAllGroups();
		try {
			result = om.writeValueAsString(groups);
		} catch(Exception ex) {
			ex.printStackTrace();
		}
				
		return Response.ok( result ).build();
	}
	
	
	// These functions should get there data from the liferay database.
	// These are the audio files linked to the questions
	public String getAudioFile(int question_no) {
		
		switch(question_no) {
			
			case 0: return SOUNDURL+"571.wav";
			case 1: return SOUNDURL+"572.wav";
			case 99: return SOUNDURL+"567.wav";
			default: return SOUNDURL+"529.wav";
		}
	}
	
	// These 2 functions are the group management
	public Group getGroup(String id) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		
		Group group = datastore.load(Group.class, id);
		
		if(group!=null)
			return group;
		
		group = new Group();
		group.setId(id);
		
		return group;
	}
	
	public List<Group> getAllGroups() {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		
		RootFindCommand<Group> command = datastore.find()
				.type(Group.class);
		
		QueryResultIterator<Group> it = command.now();
		List<Group> groups = new ArrayList<Group>();
		while (it.hasNext()) {
			groups.add(it.next());
		}
		
		return groups;
	}
}

@SuppressWarnings("serial")
class Group implements Serializable {
	
	public Group() {
		this.members=new HashSet<String>();
	}
	
	public String getId(){
		return id;
	}
	
	public void setId(String id){
		this.id=id;
	}
	
	public Set<String> getMembers() {
		return this.members;
	}
	
	public void addMember(String member) {
		this.members.add(member);
	}
	
	@Id private String id=null;
	private Set<String> members=null;
}
