package com.almende.dialog.agent;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.Settings;
import com.chap.memo.memoNodes.MemoNode;
import com.eaio.uuid.UUID;


@Path("/talktalk")
public class TalkTalkAgent {
	private static MemoNode startNode;
	private static final String URL="http://"+Settings.HOST+"/talktalk/";
	
	public TalkTalkAgent(){
		initDB();
	}
	
	@GET
	@Path("/id/")
	public Response getId(@QueryParam("preferred_language") String preferred_language){
		return Response.ok("{ url:\""+URL+"\",nickname:\"TalkTalk_imitator\"}").build();
	}
	
	private String formQuestion(MemoNode question){
		String result="{ requester:\""+URL+"id\",question_id:" +
				"\""+question.getId()+"\",question_text:\""+URL+"questions/"+question.getId()+"\",";
		MemoNode firstAnswer = question.getChildByStringValue("firstAnswer");
		if (firstAnswer == null){
			firstAnswer = question.getChildByStringValue("answer");
		} else {
			firstAnswer = firstAnswer.getChildByStringValue("answer");
		}
		if (firstAnswer != null){
			result+=" type:\"closed\",answers:[";
			MemoNode answer = firstAnswer;
			while (answer != null){
				if (answer != firstAnswer) result+=",";
				MemoNode nextQ = answer.getChildByStringValue("question");
				if (nextQ == null){
					System.out.println("Answer without next question?? '"+answer.getPropertyValue("answer_text")+"'");
					break;
				}
				result+="{ answer_id:\""+answer.getId()+"\",answer_text:\""+URL+"answers/"+answer.getId()+"\", callback:\""+URL+"questions/"+nextQ.getId()+"\" }";
				answer = answer.getChildByStringValue("answer");
			}
			result+="]}";
		} else {
			question = question.getChildByStringValue("question");
			if (question == null){
				result+=" type:\"comment\"}";
			} else {
				result+=" type:\"referral\",url:\""+URL+"?question_no="+question.getId()+"\"}";				
			}
		}
		return result;
	}
	
	@GET
	@Produces("application/json")
	public Response firstQuestion(@QueryParam("question_no") String question_no){
		MemoNode question = startNode.getChildByStringValue("question");
		if (question_no != null && !question_no.equals("")){
			question = new MemoNode(new UUID(question_no));
		}
		return Response.ok(formQuestion(question)).build();
	}
	@Path("/questions/{question_no}")
	@POST
	@Produces("application/json")
	public Response answerQuestion(String answer_json, @PathParam("question_no") String question_no){
		MemoNode question = new MemoNode(new UUID(question_no));
		return Response.ok(formQuestion(question)).build();
	}

	@Path("/questions/{question_no}")
	@GET
	@Produces("text/plain")
	public  Response getQuestionText(@PathParam("question_no") String question_no ){
		MemoNode question = new MemoNode(new UUID(question_no));
		return Response.ok(question.getPropertyValue("question_text")).build();
	}
	
	@Path("/answers/{answer_no}")
	@GET
	@Produces("text/plain")
	public Response getAnswerText(@PathParam("answer_no") String answer_no){
		MemoNode answer = new MemoNode(new UUID(answer_no));
		return Response.ok(answer.getPropertyValue("answer_text")).build();
	}
	
	private MemoNode addQuestion(MemoNode answer, String question_text){
		MemoNode question = new MemoNode("question")
								.setPropertyValue("question_text", question_text);
		answer.addChild(question);
		return question;
	}
	private MemoNode addAnswer(MemoNode question, MemoNode prev, String answer_text){
		MemoNode answer = new MemoNode("answer")
								.setPropertyValue("answer_text",answer_text);
		if (prev != null){
			prev.addChild(answer);
		} else {
			MemoNode firstAnswer = new MemoNode("firstAnswer");
			question.addChild(firstAnswer)
					.addChild(answer);
		}
		return answer;
	}
	
	private void initDB(){
		startNode= MemoNode.getRootNode().getChildByStringValue("talktalk");
		if (startNode == null){
			startNode = new MemoNode("talktalk");
			MemoNode.getRootNode().addChild(startNode);
		}
		if (startNode.getChildByStringValue("question") != null) return;
		MemoNode initQ = addQuestion(startNode,"Hallo, ik ben Joop Ringelberg");
		MemoNode campCoachA = addAnswer(initQ,null,"Kan ik je wat vragen over de Campus Coach Factory?");
		MemoNode campCoachQ = addQuestion(campCoachA,"Sorry, dit pad heb ik nog niet overgetikt! :(");
		campCoachQ.addChild(initQ);
		
		MemoNode lcnA = addAnswer(initQ,campCoachA,"Ben jij niet van LCN?");
		MemoNode lcnQ = addQuestion(lcnA,"Sorry, dit pad heb ik nog niet overgetikt! :(");
		lcnQ.addChild(initQ);
		
		MemoNode talkingHeadA = addAnswer(initQ,lcnA,"Ik wil wat meer weten over de Talking Head Factory.");

		MemoNode talkingHeadQ = addQuestion(talkingHeadA,"De Talking Head Factory is een abonnement op TalkTalk waarmee je een Talking Head kunt maken.");
		MemoNode talkingHeadA2 = addAnswer(talkingHeadQ,null,"OK, maar wat is dan een Talking Head?");
		MemoNode talkingHeadQ2 = addQuestion(talkingHeadA2,"Een Talking Head is een 'virtuele aanwezigheid' op je website, gebaseerd op TalkTalk (zoals wat je nu interactief leest ook gebaseerd is op TalkTalk).");
		MemoNode voorbeeldenA = addAnswer(talkingHeadQ2,null,"Kun je voorbeelden geven?");
		
		MemoNode voorbeeldenQ = addQuestion(voorbeeldenA,"Een advocatenkantoor zou Talking Heads in kunnen zetten, of een medische kliniek, of een opleiding, of een personal finance professional.");
		MemoNode voorbeeldenA1 = addAnswer(voorbeeldenQ,null,"Hoe zou een advocaat er dan gebruik van kunnen maken?");
		MemoNode voorbeeldenA2 = addAnswer(voorbeeldenQ,voorbeeldenA1,"Wat is het nut voor een medische kliniek dan?");
		MemoNode voorbeeldenA3 = addAnswer(voorbeeldenQ,voorbeeldenA2,"Wat moet een opleiding ermee?");
		MemoNode voorbeeldenA4 = addAnswer(voorbeeldenQ,voorbeeldenA3,"Voor zo'n personal finance professional is het dan een manier om direct contact te hebben.");
		MemoNode voorbeeldenE = addAnswer(voorbeeldenQ,voorbeeldenA4,"Ok, genoeg voorbeelden dus!");

		MemoNode voorbeeldenQ1 = addQuestion(voorbeeldenA1,"Veel websites van zulke kantoren geven hun partners een persoonlijke pagina. Op zo'n pagina zou je een interview met de persoon kunnen lezen, interactief.");
		MemoNode voorbeeldenA11 = addAnswer(voorbeeldenQ1,null,"Dus gewoon vraag en antwoord?");
		MemoNode voorbeeldenQ11 = addQuestion(voorbeeldenA11,"Precies, zoals je nu ook doet.");
		MemoNode voorbeeldenA12 = addAnswer(voorbeeldenQ11,null,"Maar wat voegt dat dan toe?");
		MemoNode voorbeeldenQ12 = addQuestion(voorbeeldenA12,"Het blijkt dat mensen deze manier van lezen als veel warmer ervaren.");
		MemoNode voorbeeldenA13 = addAnswer(voorbeeldenQ12,null,"Warmer dan gewone tekst?");
		MemoNode voorbeeldenQ13 = addQuestion(voorbeeldenA13,"Ja. Je zit als lezer 'achter het stuur'. Hoewel je weet dat het niet echt zo is, voelt het toch als interactie met een persoon.");
		MemoNode voorbeeldenA14 = addAnswer(voorbeeldenQ13,null,"Ok, ik begrijp het. Wat waren de voorbeelden ook alweer?");
		voorbeeldenA14.addChild(voorbeeldenQ);
		
		MemoNode voorbeeldenQ2 = addQuestion(voorbeeldenA2,"Patienten vinden het vaak prettig om de ervaringen van andere patienten te lezen. Je zou een Talking Head als een aanbeveling, of een getuigenis, kunnen inzetten.");
		MemoNode voorbeeldenA21 = addAnswer(voorbeeldenQ2,null,"Of een arts aan het woord laten?");
		MemoNode voorbeeldenQ21 = addQuestion(voorbeeldenA21,"Precies. De bezoeker kan dan in interactie met de arts (virtueel, natuurlijk)");
		voorbeeldenQ21.addChild(voorbeeldenA11);
		
		MemoNode voorbeeldenQ3 = addQuestion(voorbeeldenA3,"De beste reclame voor een opleiding is een tevreden student. Dus laat die hun verhaal doen als Talking Heads op je website.");
		MemoNode voorbeeldenA31 = addAnswer(voorbeeldenQ3,null,"Veel leuker dan een standaard stukje tekst!");
		MemoNode voorbeeldenQ31 = addQuestion(voorbeeldenA31,"Jazeker. Het is interactief. Foto erbij. De stijl kan ook veel meer ontspannen zijn dan bij gewoon proza.");
		MemoNode voorbeeldenA32 = addAnswer(voorbeeldenQ31,null,"Kan een opleiding het niet ook als vraagbaak gebruiken?");
		MemoNode voorbeeldenQ33 = addQuestion(voorbeeldenA32,"Daar heb ik een ander product voor: de Campus Coach Factory.");
		voorbeeldenQ33.addChild(campCoachQ);
		
		MemoNode voorbeeldenQ4 = addQuestion(voorbeeldenA4,"Ja. En vergeet niet dat het interactief is.");
		voorbeeldenQ4.addChild(voorbeeldenA11);
		
		MemoNode voorbeeldenQE = addQuestion(voorbeeldenE,"Een Talking Head is ook maar een bescheiden toevoeging aan een website.");
		MemoNode voorbeeldenAE1 = addAnswer(voorbeeldenQE,null,"Hoezo dan?");
		MemoNode voorbeeldenQE1 = addQuestion(voorbeeldenAE1,"'t Is een kleine dialoog (venster) dat ook nog eens geminimaliseerd kan worden tot alleen een titelbalk. Bovendien kun je het overal in je pagina plaatsen. En de gebruiker kan het verslepen.");
		MemoNode voorbeeldenAE2 = addAnswer(voorbeeldenQE1,null,"Is zo'n Talking Head altijd aanwezig op de pagina?");
		MemoNode voorbeeldenQE2 = addQuestion(voorbeeldenAE2,"Dat kan, maar hoeft niet. Je kunt bijvoorbeeld een knopje op de pagina opnemen en als de bezoeker daarop klikt, opent de Talking Head.");
		MemoNode voorbeeldenAE3 = addAnswer(voorbeeldenQE2,null,"Weet een bezoeker dan wel hoe hij het moet gebruiken?");
		MemoNode voorbeeldenQE3 = addQuestion(voorbeeldenAE3,"Jij kunt het toch ook? De ervaring is dat de interface voor zichzelf spreekt. Als je met de muis boven een van de knoppen hangt, verschijnt er bovendien vanzelf een tip. Dat gaat wel goed.");
		MemoNode voorbeeldenAE4 = addAnswer(voorbeeldenQE3,null,"Helder!");
		voorbeeldenAE4.addChild(initQ);
		
		MemoNode escape = addAnswer(initQ,talkingHeadA,"Duidelijk!");
		MemoNode verderQ = addQuestion(escape,"Is er verder nog iets dat je wilt weten?");
		MemoNode demoA = addAnswer(verderQ,null,"Wat is dit voor een demonstratie?");
		addQuestion(demoA,"Dit is een 'Talking Head'. Een ingeblikte conversatie, die je al klikkend door kunt lezen.");
		MemoNode.flushDB();
	}
}
