package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

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

import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Twitter;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.MediaProperty;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.state.StringStore;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Splitter;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public class TwitterServlet extends TextServlet implements Runnable {
	
	private static final long	serialVersionUID	= 6657877430445328774L;
	private static final Logger	log					= Logger.getLogger(TwitterServlet.class
															.getName());
	public static final String	STATUS_ID_KEY		= "inReptyTweetID";
	public static final String	TWEET_TYPE			= "tweetType";
	
	private AdapterConfig adapterConfig = null;
	private TwitterEndpoint twitterEndpoint = null;
	public enum TwitterEndpoint
	{
	    DIRECT_MESSAGE ( "/direct_messages"),
	    MENTIONS("/mentions");

	    String url;
	    private TwitterEndpoint(String url)
	    {
	        this.url = url;
	    }
	    
	    public String getUrl()
        {
            return url;
        }
	}
	
	public TwitterServlet()
	{
	    
	}
	
	public TwitterServlet( AdapterConfig adapterConfig, TwitterEndpoint endpoint )
    {
	    this.adapterConfig = adapterConfig;
	    this.twitterEndpoint = endpoint;
    }

    @Override
	public void service(HttpServletRequest req, HttpServletResponse res)
			throws IOException {
		if ("GET".equalsIgnoreCase(req.getMethod())) {
			doGet(req, res);
		} else if ("POST".equalsIgnoreCase(req.getMethod())) {
			doPost(req, res);
		}
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		if (req.getRequestURI().startsWith("/twitter/changeAgent")) {
			String adapterId = req.getParameter("adapterId");
			String agentURL = req.getParameter("agentURL");
			if (adapterId != null && agentURL != null
					&& agentURL.startsWith("http")) {
				try
                {
                    updateAgentURL(resp.getWriter(), adapterId, agentURL);
                }
                catch ( Exception e )
                {
                    log.severe( String.format( "Update agent failed! Message: %s", e.getLocalizedMessage() ) );
                }
			}
		}
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		OAuthService service = new ServiceBuilder().provider(TwitterApi.class)
				.apiKey(Twitter.OAUTH_KEY).apiSecret(Twitter.OAUTH_SECRET)
				.build();
		
		PrintWriter out = resp.getWriter();
		ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters(
				getAdapterType(), null, null);
		boolean isDirectMessageCall = false;
		String tweetOrDirectMesssageId = null;
        for ( AdapterConfig config : adapters )
        {
            Token accessToken = new Token( config.getAccessToken(), config.getAccessTokenSecret() );
            String url = null;

            if ( req.getPathInfo().equals( TwitterEndpoint.DIRECT_MESSAGE.getUrl() ) )
            {
                // make sure that the user follows the one, for whom the direct
                // message is intended for
                tweetOrDirectMesssageId = StringStore.getString( "lastdirectmessage_" + config.getConfigId() );
                url = "https://api.twitter.com/1.1/direct_messages.json";
                if ( tweetOrDirectMesssageId != null && !tweetOrDirectMesssageId.equals( "0" ) )
                    url += "?since_id=" + tweetOrDirectMesssageId;
                isDirectMessageCall = true;
            }
            else
            {
                tweetOrDirectMesssageId = StringStore.getString( "lasttweet_" + config.getConfigId() );
                url = "https://api.twitter.com/1.1/statuses/mentions_timeline.json";
                if ( tweetOrDirectMesssageId != null && !tweetOrDirectMesssageId.equals( "0" ) )
                    url += "?since_id=" + tweetOrDirectMesssageId;
            }
            OAuthRequest request = new OAuthRequest( Verb.GET, url );
            service.signRequest( accessToken, request );
            Response response = request.send();
            ObjectMapper om = ParallelInit.getObjectMapper();
            
            //initialize updatedTweetId with the previously seen one
            String updatedTweedOrDirectMesssageId = tweetOrDirectMesssageId;
            try
            {
                String format = "EEE MMM dd HH:mm:ss ZZZZZ yyyy";
                DateTime date = null;
                ArrayNode res = om.readValue( response.getBody(), ArrayNode.class );
                if ( tweetOrDirectMesssageId == null )
                {
                    for ( JsonNode tweet : res )
                    {
                        String msgDate = tweet.get( "created_at" ).asText();
                        SimpleDateFormat sf = new SimpleDateFormat( format, Locale.ENGLISH );
                        sf.setLenient( true );
                        Date newDate = sf.parse( msgDate );
                        if ( date == null || date.isBefore( newDate.getTime() ) )
                        {
                            tweetOrDirectMesssageId = tweet.get( "id_str" ).asText();
                            date = new DateTime( newDate.getTime() );
                        }
                    }
                }
                else
                {
                    String userKeyInResponseJSON = isDirectMessageCall ? "sender" : "user";
                    for ( JsonNode tweet : res )
                    {
                        String message = tweet.get( "text" ).asText();
                        message = message.replace( config.getMyAddress(), "" );

                        TextMessage msg = new TextMessage();
                        msg.setAddress( "@" + tweet.get( userKeyInResponseJSON ).get( "screen_name" ).asText() );
                        msg.setRecipientName( tweet.get( userKeyInResponseJSON ).get( "name" ).asText() );
                        msg.setBody( message.trim() );
                        msg.setLocalAddress( config.getMyAddress() );
                        msg.getExtras().put( STATUS_ID_KEY, tweet.get( "id" ) );
                        if ( isDirectMessageCall )
                        {
                            MediaProperty mediaProperty = new MediaProperty();
                            mediaProperty.setMedium( MediumType.TWITTER );
                            mediaProperty.addProperty( MediaPropertyKey.TYPE, "direct" );
                            msg.getExtras().put( Question.MEDIA_PROPERTIES, Arrays.asList( mediaProperty ) );
                        }
                        processMessage( msg );

                        String msgDate = tweet.get( "created_at" ).asText();
                        SimpleDateFormat sf = new SimpleDateFormat( format, Locale.ENGLISH );
                        sf.setLenient( true );
                        Date newDate = sf.parse( msgDate );
                        if ( date == null || date.isBefore( newDate.getTime() ) )
                        {
                            date = new DateTime( newDate.getTime() );
                            updatedTweedOrDirectMesssageId = tweet.get( "id_str" ).asText();
                        }
                    }
                }
                if ( updatedTweedOrDirectMesssageId != null
                    && !updatedTweedOrDirectMesssageId.equals( tweetOrDirectMesssageId ) )
                {
                    if ( req.getPathInfo().equals( TwitterEndpoint.DIRECT_MESSAGE.getUrl() ) )
                    {
                        StringStore.storeString( "lastdirectmessage_" + config.getConfigId(),
                            updatedTweedOrDirectMesssageId );
                    }
                    else
                    {
                        StringStore.storeString( "lasttweet_" + config.getConfigId(), updatedTweedOrDirectMesssageId );
                    }
                }
            }
            catch ( Exception ex )
            {
                log.warning( "Failed to parse result" );
                out.print( response.getBody() );
                out.close();
            }
            out.print( response.getBody() );
        }
        out.close();
    }
	
	@Override
	protected int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName,
			Map<String, Object> extras, AdapterConfig config) {
		
		OAuthService service = new ServiceBuilder().provider(TwitterApi.class)
				.apiKey(Twitter.OAUTH_KEY).apiSecret(Twitter.OAUTH_SECRET)
				.build();
		
		int count = 0;
		to = to.startsWith("@") ? to : "@" + to;
		Token accessToken = new Token(config.getAccessToken(),
				config.getAccessTokenSecret());
		for (String messagepart : Splitter.fixedLength(140 - (to.length() + 1))
				.split(message)) {
			try {
				to = URLEncoder.encode(to, "UTF-8");
				String url = null;
				String tweetType = null;
				if (extras != null
						&& extras.get(Question.MEDIA_PROPERTIES) != null
						&& extras.get(Question.MEDIA_PROPERTIES) instanceof Collection) {
					@SuppressWarnings("unchecked")
					Collection<MediaProperty> mediaProperties = (Collection<MediaProperty>) extras
							.get(Question.MEDIA_PROPERTIES);
					tweetType = Question.getMediaPropertyValue(mediaProperties,
							MediumType.TWITTER, MediaPropertyKey.TYPE);
				}
				if (tweetType != null && tweetType.equals("direct")) {
					// make sure the user for whom the direct message is for, is
					// followed
					followUser(service, accessToken, to);
					String directMessage = URLEncoder.encode(messagepart,
							"UTF8").replace("+", "%20");
					url = "https://api.twitter.com/1.1/direct_messages/new.json";
					url = ServerUtils.getURLWithQueryParams(url, "screen_name",
							to);
					url = ServerUtils.getURLWithQueryParams(url, "text",
							directMessage);
				} else {
					String inReptyTweetID = (extras != null && extras
							.get(STATUS_ID_KEY) != null) ? extras.get(
							STATUS_ID_KEY).toString() : "";
					String status = to
							+ URLEncoder.encode(" " + messagepart, "UTF8")
									.replace("+", "%20");
					url = "https://api.twitter.com/1.1/statuses/update.json";
					url = ServerUtils.getURLWithQueryParams(url, "status",
							status);
					url = ServerUtils.getURLWithQueryParams(url,
							"in_reply_to_status_id", inReptyTweetID);
				}
				
				OAuthRequest request = new OAuthRequest(Verb.POST, url);
				service.signRequest(accessToken, request);
				Response response = request.send();
				log.info("Message send result: " + response.getBody());
				count++;
			} catch (Exception ex) {
				log.warning("Failed to send message");
			}
		}
		
		return count;
	}
	
	@Override
	protected int broadcastMessage(String message, String subject, String from,
			String senderName, Map<String, String> addressNameMap,
			Map<String, Object> extras, AdapterConfig config) throws Exception {
		int count = 0;
		for (String toAddress : addressNameMap.keySet()) {
			String toName = addressNameMap.get(toAddress);
			count = count
					+ sendMessage(message, subject, from, senderName,
							toAddress, toName, extras, config);
		}
		return count;
	}
	
	/**
	 * updates the corresponding adapter with the agent URL
	 * 
	 * @param resp
	 * @param adapterId
	 * @param agentURL
	 * @throws IOException
	 */
	protected void updateAgentURL(PrintWriter out, String adapterId,
			String agentURL) throws Exception {
		AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
		adapterConfig.setDialogWithURL( "Twitter inbound Agent", agentURL );
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		datastore.store(adapterConfig);
		out.print("<html>"
				+ "<head>"
				+ "<title>Authorize Dialog Handler</title>"
				+ "<style>"
				+ "body {width: 700px;}"
				+ "body, th, td, input {font-family: arial; font-size: 10pt; color: #4d4d4d;}"
				+ "</style>"
				+ "</head>"
				+ "<body>"
				+ "<h1>Agent updated</h1>"
				+ "<p>"
				+ "All tweets mentioning you ("
				+ adapterConfig.getMyAddress()
				+ ") and all direct messages to you will be handled by the agent: <a href='"
				+ agentURL + "'>" + agentURL + "</a></p>" + "</body>"
				+ "</html>");
	}
	
	private boolean followUser(OAuthService service, Token accessToken,
			String userToBeFollowed) {
		try {
			String requestURL = "https://api.twitter.com/1.1/friendships/create.json";
			requestURL = ServerUtils.getURLWithQueryParams(requestURL,
					"screen_name", userToBeFollowed);
			OAuthRequest request = new OAuthRequest(Verb.POST, requestURL);
			service.signRequest(accessToken, request);
			request.send();
			return true;
		} catch (Exception exception) {
			log.severe(exception.getLocalizedMessage());
			return false;
		}
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
		return AdapterAgent.ADAPTER_TYPE_TWITTER;
	}
	
	@Override
	protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {
		// TODO Auto-generated method stub
	}

    @Override
    public void run()
    {
        Client client = ParallelInit.getClient();
        WebResource webResource;
        try
        {
            webResource = client.resource( Settings.DIALOG_HANDLER + getServletPath() + twitterEndpoint.getUrl() );
            webResource.type( "text/plain" ).get( String.class );
        }
        catch ( Exception e )
        {
            log.severe( String.format( "adapterId is: %s" + adapterConfig, "ERROR loading question: " + e.toString() ) );
        }
    }
}
