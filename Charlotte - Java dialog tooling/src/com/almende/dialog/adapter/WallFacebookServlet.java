package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joda.time.DateTime;
import org.scribe.model.Token;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Facebook;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.state.StringStore;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class WallFacebookServlet extends TextServlet {

	private static final long serialVersionUID = -5418264483130072610L;
	private static final int WALL_MESSAGE_RENTENTION = 7; // Only process message older then 7 days
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		ObjectMapper om = ParallelInit.getObjectMapper();
		Facebook fb = null;
		PrintWriter out = resp.getWriter();
		ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters(getAdapterType(), null, null);
		for(AdapterConfig config : adapters) {
		
			ArrayNode allMessages = om.createArrayNode();
			fb = new Facebook(new Token(config.getAccessToken(), config.getAccessTokenSecret()));

			DateTime msgDate = DateTime.now().minusDays(WALL_MESSAGE_RENTENTION);
			
			ArrayNode messages = fb.getWallMessages((msgDate.toDate().getTime()/1000)+"");
			for(JsonNode message : messages) {

				//proccess message
				// Don't process messages from yourself
				if(!config.getMyAddress().equals(message.get("from").get("id").asText())) {
					
					String id = message.get("id").asText();
					String since = StringStore.getString(getAdapterType()+"_comment_"+id);
					
					if(since==null) {
						since="0";
					}
					
					DateTime last = new DateTime(since);
					
					if(message.get("message")!=null) {
						
						TextMessage msg = new TextMessage();
						msg.setAddress(id);
						msg.setRecipientName(message.get("from").get("name").asText());
						msg.setLocalAddress(config.getMyAddress());
						if(message.get("comments").get("count").asInt() > 0) {
							
							// Get the comments to respond to comment
							ArrayNode comments = fb.getComments(id, (last.toDate().getTime()/1000)+"");
							for(JsonNode comment : comments) {
								DateTime date = new DateTime(comment.get("created_time").asText());
								if(last.isBefore(date)) {
									if(!config.getMyAddress().equals(comment.get("from").get("id").asText())) {
										msg.setBody(comment.get("message").asText());
										processMessage(msg);
									}
										
									last = date;
								}
							}
						} else {						
							
							// Respond to this message
							msg.setBody(message.get("message").asText());
							DateTime date = new DateTime(message.get("created_time").asText());
							if(last.isBefore(date))
								last = date;
							
							processMessage(msg);
						}
					}
					
					StringStore.storeString(getAdapterType()+"_comment_"+id, last.toString());
				}
				
				allMessages.add(message);
			}
			
			out.println(allMessages.toString());
		}
	}

	@Override
	protected int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName, AdapterConfig config) {
		
		Facebook fb = new Facebook(new Token(config.getAccessToken(), config.getAccessTokenSecret()));
		fb.sendComment(message, to, toName);
		
		return 1;
	}
	
        @Override
        protected int broadcastMessage( String message, String subject, String from, String fromName,
            Map<String, String> addressNameMap, AdapterConfig config ) throws Exception
        {
            int count = 0;
            for ( String address : addressNameMap.keySet() )
            {
                String toName = addressNameMap.get( address );
                count = count + sendMessage( message, subject, from, fromName, address, toName, config );
            }
            return count;
        }

	@Override
	protected TextMessage receiveMessage(HttpServletRequest req,
			HttpServletResponse resp) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected String getServletPath() {
		return "/facebook_wall";
	}

	@Override
	protected String getAdapterType() {
		return "FACEBOOK";
	}

	@Override
	protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {
		
	}
}
