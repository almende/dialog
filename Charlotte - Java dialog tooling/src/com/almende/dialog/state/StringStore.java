package com.almende.dialog.state;

import java.util.Iterator;
//import java.util.logging.Logger;

import com.almende.util.ParallelInit;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;

public class StringStore {
	//private static final Logger log = Logger.getLogger(com.almende.dialog.state.StringStore.class.getName()); 	
	
	static DatastoreService datastore = ParallelInit.getDatastore();
	static MemcacheService syncCache = MemcacheServiceFactory
			.getMemcacheService();
	
	public static boolean dropEntity(String id){
		Entity entity = getEntity(id);
		if (entity != null){
			//log.warning("drop :"+id+ " : "+ entity.getKey());
			datastore.delete(entity.getKey());
			syncCache.delete(id);
			return true;
		}
		return false;
	}
	public static void dropString(String id){
		while (dropEntity(id));
	}
	public static void storeString(String id, String text){
		Entity entity = getEntity(id);
		
		if (entity == null) entity = new Entity("storedString");
		//log.warning("store :"+id+" : "+entity.getKey());

		entity.setProperty("id", id);
		entity.setUnindexedProperty("string", new Text(text));
		datastore.put(entity);
		syncCache.put(id, entity);

	}
	public static Entity getEntity(String id){
		Entity entity=null;
		if (syncCache.contains(id)){
			entity = (Entity) syncCache.get(id);
		}
		if (entity == null){
			Query q = new Query("storedString");
			q.addFilter("id", Query.FilterOperator.EQUAL, id);
			PreparedQuery pq = datastore.prepare(q);
			try {
				entity = pq.asSingleEntity();
			} catch (PreparedQuery.TooManyResultsException e){
				Iterator<Entity> iter = pq.asIterator();
				while (iter.hasNext()){
					Entity ent = iter.next();
					if (entity == null){
						entity = ent;
					} else {
						datastore.delete(ent.getKey());
					}
				}
			}
			if (entity != null){
				//log.warning("store (because found in datastore) :"+id+" : "+entity.getKey());
				syncCache.put(id, entity);
			}
		}
		//log.warning("return :"+id+" : "+(entity!=null?entity.getKey():""));

		return entity;
	}
	public static String getString(String id){
		Entity entity = getEntity(id);
		if (entity == null) return null;
		return ((Text)entity.getProperty("string")).getValue();
	}
}
