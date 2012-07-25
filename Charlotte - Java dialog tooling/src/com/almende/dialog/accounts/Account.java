package com.almende.dialog.accounts;

import static com.google.appengine.api.datastore.Query.FilterOperator.EQUAL;

import java.io.Serializable;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.eaio.uuid.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.code.twig.annotation.AnnotationObjectDatastore;
import com.google.code.twig.annotation.Id;

@Path("/accounts/")
public class Account implements Serializable {
	static final Logger log = Logger.getLogger(Account.class.getName());
	private static final long serialVersionUID = 8291748219696403349L;
	static final ObjectMapper om = new ObjectMapper();
	
	@Id String uuid;
	UUID token; //Usefull?
	String description="";
	int credits = 0;
	Boolean disabled;
	
	public Account(){
	}
	
	@POST
	@Consumes("application/json")
	@Produces("application/json")
	@JsonIgnore
	public Response createAccount(String json){
		AnnotationObjectDatastore datastore  = new AnnotationObjectDatastore();
		try {
			Account newAccount = new Account();
			
			newAccount.uuid = new UUID().toString();
			//newAccount.uuid = new UUID();
			newAccount.token = new UUID();
			newAccount.disabled=false;
			
			Account postAccount = om.readValue(json, Account.class);
			if (!postAccount.description.equals("")){
				newAccount.description = postAccount.description;
			}
			if (postAccount.disabled != null){
				newAccount.disabled = postAccount.disabled;
			}
			datastore.store(newAccount);

			return Response.ok(om.writeValueAsString(newAccount)).build();			
		} catch (Exception e) {
			log.severe("createAgent: Exception parsing JSON:"+e.getMessage());
		}
		return Response.status(Status.BAD_REQUEST).build();
	}
	
	@Path("/{uuid}/")
	@PUT
	@Consumes("application/json")
	@Produces("application/json")
	@JsonIgnore
	public Response updateAccount(@PathParam("uuid") String uuid, String json){
		
		return Response.ok().build();
	}

	@Path("/{uuid}/")
	@GET
	@Produces("application/json")
	@JsonIgnore
	public Response getAccount(@PathParam("uuid") String sid){
		AnnotationObjectDatastore datastore  = new AnnotationObjectDatastore();

		Account account = datastore.load(Account.class,sid);
		try {
			return Response.ok(om.writeValueAsString(account)).build();
		} catch (Exception e) {
			log.severe("getAccount: Exception creating JSON:"+e.getMessage());
		}
		return Response.serverError().build();
	}
	

	public String getId() {
		return uuid;
	}


	public void setId(String sid) {
		this.uuid = new UUID(sid).toString();
	}


	public String getToken() {
		return token.toString();
	}


	public void setToken(String token) {
		this.token = new UUID(token);
	}


	public String getDescription() {
		return description;
	}


	public void setDescription(String description) {
		this.description = description;
	}


	public int getCredits() {
		return credits;
	}


	public void setCredits(int credits) {
		this.credits = credits;
	}


	public boolean isDisabled() {
		return disabled;
	}


	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}
	
}
