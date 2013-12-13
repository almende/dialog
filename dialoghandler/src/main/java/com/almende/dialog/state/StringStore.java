package com.almende.dialog.state;

import java.util.logging.Logger;

import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;

// import java.util.logging.Logger;

public class StringStore {
	private static final Logger			log			= Logger.getLogger(StringStore.class
															.getName());
	
	static TwigCompatibleMongoDatastore	datastore	= new TwigCompatibleMongoDatastore();
	
	public static boolean dropEntity(String id) {
		Entity entity = datastore.load(Entity.class, id);
		if (entity != null) {
			// log.warning("drop :"+id+ " : "+ entity.getKey());
			datastore.delete(entity);
			log.info("StringStore entity with id: " + id + " is deleted");
			return true;
		}
		return false;
	}
	
	public static void dropString(String id) {
		while (dropEntity(id))
			;
	}
	
	public static void storeString(String id, String text) {
		Entity entity = getEntity(id);
		
		if (entity == null) entity = new Entity("storedString");
		// log.warning("store :"+id+" : "+entity.getKey());
		
		entity.setId(id);
		entity.setString(text);
		
		datastore.store(entity);
		log.info(String.format("String with id: %s and text: %s saved", id,
				text));
	}
	
	public static Entity getEntity(String id) {
		return datastore.load(Entity.class, id); 		
	}
	
	public static String getString(String id) {
		Entity entity = getEntity(id);
		if (entity == null) return null;
		return entity.getString();
	}
}
