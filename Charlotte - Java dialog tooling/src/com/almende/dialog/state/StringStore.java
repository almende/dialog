package com.almende.dialog.state;

import java.util.HashMap;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;

public class StringStore {
	static DatastoreService datastore = DatastoreServiceFactory
			.getDatastoreService();
	static MemcacheService syncCache = MemcacheServiceFactory.getMemcacheService();
	static HashMap<String,Entity> local = new HashMap<String,Entity>(50);
	
	public static void dropString(String id){
		Entity entity = getEntity(id);
		if (entity != null){
			datastore.delete(entity.getKey());
			syncCache.delete(id);
			local.remove(id);
		}
	}
	public static void storeString(String id, String state){
		Entity entity = getEntity(id);
		if (entity == null) entity = new Entity("storedString");
		entity.setProperty("id", id);
		entity.setUnindexedProperty("string", new Text(state));
		datastore.put(entity);
		syncCache.put(id, entity);
		local.put(id, entity);
	}
	public static Entity getEntity(String id){
		Entity entity=null;
		if (local.containsKey(id)) entity = local.get(id);
		if (entity == null && syncCache.contains(id)){
			entity = (Entity) syncCache.get(id);
			if (entity != null) local.put(id, entity);
		}
		if (entity == null){
			Query q = new Query("storedString");
			q.addFilter("id", Query.FilterOperator.EQUAL, id);
			PreparedQuery pq = datastore.prepare(q);
			entity = pq.asSingleEntity();
			if (entity != null){
				syncCache.put(id, entity);
				local.put(id, entity);
			}
		}
		return entity;
	}
	public static String getString(String id){
		Entity entity = getEntity(id);
		if (entity == null) return null;
		return ((Text)entity.getProperty("string")).getValue();
	}
}
