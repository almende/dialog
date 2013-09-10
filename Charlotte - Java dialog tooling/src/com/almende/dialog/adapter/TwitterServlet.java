package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joda.time.DateTime;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.TwitterApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Twitter;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.state.StringStore;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Splitter;

public class TwitterServlet extends TextServlet {
	
	private static final long serialVersionUID = 6657877430445328774L;
	private static final Logger log = Logger.getLogger(TwitterServlet.class.getName());

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		OAuthService service = new ServiceBuilder()
        .provider(TwitterApi.class)
        .apiKey(Twitter.OAUTH_KEY)
        .apiSecret(Twitter.OAUTH_SECRET)
        .build();

		PrintWriter out = resp.getWriter();
		ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters(getAdapterType(), null, null);
		for(AdapterConfig config : adapters) {
			String tweetId = StringStore.getString("lasttweet_"+config.getConfigId());
			Token accessToken = new Token(config.getAccessToken(), config.getAccessTokenSecret());
			String url = "https://api.twitter.com/1.1/statuses/mentions_timeline.json";
			if(tweetId!=null && !tweetId.equals("0"))
				url+="?since_id="+tweetId;
			OAuthRequest request = new OAuthRequest(Verb.GET, url);
			service.signRequest(accessToken, request);
			Response response = request.send();
			ObjectMapper om = ParallelInit.getObjectMapper();
			
			try {
				String format="EEE MMM dd HH:mm:ss ZZZZZ yyyy";
				DateTime date=null;
				ArrayNode res = om.readValue(response.getBody(), ArrayNode.class);
				if(tweetId==null) {
					for(JsonNode tweet : res){
						String msgDate = tweet.get("created_at").asText();
						SimpleDateFormat sf = new SimpleDateFormat(format, Locale.ENGLISH);
						sf.setLenient(true);
						Date newDate = sf.parse(msgDate);
						if(date==null || date.isBefore(newDate.getTime())) {
							tweetId = tweet.get("id_str").asText();
							date = new DateTime(newDate.getTime());
						}
					}
				} else {
					for(JsonNode tweet : res){
						
						String message = tweet.get("text").asText();
						message = message.replace(config.getMyAddress(), "");
						
						TextMessage msg = new TextMessage();
						msg.setAddress("@"+tweet.get("user").get("screen_name").asText());
						msg.setRecipientName(tweet.get("user").get("name").asText());
						msg.setBody(message.trim());
						msg.setLocalAddress(config.getMyAddress());
						
						processMessage(msg);
						
						String msgDate = tweet.get("created_at").asText();
						SimpleDateFormat sf = new SimpleDateFormat(format, Locale.ENGLISH);
						sf.setLenient(true);
						Date newDate = sf.parse(msgDate);
						if(date==null || date.isBefore(newDate.getTime())) {
							date = new DateTime(newDate.getTime());
							tweetId = tweet.get("id_str").asText();
						}
							
					}
				}
				
				log.info("Set date: "+config.getConfigId()+" to: "+tweetId);
				
				StringStore.storeString("lasttweet_"+config.getConfigId(), tweetId+"");
			} catch(Exception ex) {
				log.warning("Failed to parse result");
			}
			
			out.print(response.getBody());
		}
		out.close();
	}

	@Override
	protected int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName, AdapterConfig config) {

		OAuthService service = new ServiceBuilder()
        .provider(TwitterApi.class)
        .apiKey(Twitter.OAUTH_KEY)
        .apiSecret(Twitter.OAUTH_SECRET)
        .build();		
		
		int count = 0;
		
		Token accessToken = new Token(config.getAccessToken(), config.getAccessTokenSecret());
		for(String messagepart : Splitter.fixedLength(140-(to.length()+1)).split(message)) {
			try {
				String status = URLEncoder.encode(to+" "+messagepart, "UTF8");
				String url = "https://api.twitter.com/1.1/statuses/update.json?status="+status;
				OAuthRequest request = new OAuthRequest(Verb.POST, url);
				service.signRequest(accessToken, request);
				
				Response response = request.send();
				log.info("Message send result: "+response.getBody());
				
				count++;
				
			} catch (Exception ex) {
				log.warning("Failed to send message");
			}
		}
		
		return count;
	}
	
        @Override
        protected int broadcastMessage( String message, String subject, String from, String fromName, String senderName,
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
		return null;
	}

	@Override
	protected String getServletPath() {
		return "/twitter";
	}

	@Override
	protected String getAdapterType() {
		return "TWITTER";
	}

	@Override
	protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {
		// TODO Auto-generated method stub
	}
}
