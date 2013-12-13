package com.almende.util.twigmongo;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.almende.util.AnnotationUtil;
import com.almende.util.AnnotationUtil.AnnotatedClass;
import com.almende.util.AnnotationUtil.AnnotatedField;
import com.almende.util.ParallelInit;
import com.almende.util.twigmongo.annotations.Id;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class TwigCompatibleMongoDatastore {
	
	private AnnotatedField getKeyField(Class<?> clazz) {
		AnnotatedClass ac = AnnotationUtil.get(clazz);
		List<AnnotatedField> ids = ac.getAnnotatedFields(Id.class);
		if (ids == null || ids.size() != 1) {
			throw new IllegalArgumentException(
					"The class should have one Id annotation:"
							+ clazz.getCanonicalName());
		}
		return ids.get(0);
	}
	
	public void store(Object document) {
		getKeyField(document.getClass());
		String collectionName = document.getClass().getCanonicalName()
				.toLowerCase()
				+ "s";
		DBCollection table = ParallelInit.getDatastore().getCollection(
				collectionName);
		BasicDBObject doc = JOM.getInstance().convertValue(document,
				BasicDBObject.class);
		table.insert(doc);
	}
	
	public <T> T load(Class<T> clazz, Object key) {
		AnnotatedField keyField = getKeyField(clazz);
		String collectionName = clazz.getCanonicalName().toLowerCase() + "s";
		DBCollection table = ParallelInit.getDatastore().getCollection(
				collectionName);
		BasicDBObject searchQuery = new BasicDBObject();
		searchQuery.put(keyField.getName(), key);
		
		T val = JOM.getInstance().convertValue(table.findOne(searchQuery),
				clazz);
		return val;
	}
	
	public void update(Object document) {
		AnnotatedField keyField = getKeyField(document.getClass());
		try {
			Object key = keyField.getField().get(document);
			String collectionName = document.getClass().getCanonicalName()
					.toLowerCase()
					+ "s";
			DBCollection table = ParallelInit.getDatastore().getCollection(
					collectionName);
			BasicDBObject searchQuery = new BasicDBObject();
			searchQuery.put(keyField.getName(), key);
			
			BasicDBObject doc = JOM.getInstance().convertValue(document,
					BasicDBObject.class);
			table.update(searchQuery, doc);
		} catch (Exception e) {
			System.err.println("Warning: Couldn't get key field from:"
					+ document.getClass());
			e.printStackTrace();
		}
	}
	
	public void delete(Object document) {
		AnnotatedField keyField = getKeyField(document.getClass());
		try {
			Object key = keyField.getField().get(document);
			String collectionName = document.getClass().getCanonicalName()
					.toLowerCase()
					+ "s";
			DBCollection table = ParallelInit.getDatastore().getCollection(
					collectionName);
			BasicDBObject searchQuery = new BasicDBObject();
			searchQuery.put(keyField.getName(), key);
			
			table.remove(searchQuery);
		} catch (Exception e) {
			System.err.println("Warning: Couldn't get key field from:"
					+ document.getClass());
			e.printStackTrace();
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public <V, T> Map<V, T> loadAll(Class<T> clazz, Collection<V> keys) {
		Map<V, T> result = new HashMap<V, T>();
		AnnotatedField keyField = getKeyField(clazz);
		try {
			String collectionName = clazz.getCanonicalName().toLowerCase()
					+ "s";
			DBCollection table = ParallelInit.getDatastore().getCollection(
					collectionName);
			BasicDBObject searchQuery = new BasicDBObject(keyField.getName(),
					new BasicDBObject("$in", keys));
			DBCursor cursor = table.find(searchQuery);
			while (cursor.hasNext()) {
				DBObject entry = cursor.next();
				T res = JOM.getInstance().convertValue(entry, clazz);
				result.put((V) keyField.getField().get(res), res);
			}
		} catch (Exception e) {
			System.err.println("Warning: Couldn't get key field from:"
					+ clazz.getCanonicalName());
			e.printStackTrace();
		}
		return result;
	}
	
	public FindCommand find(){
		return new FindCommand();
	}
	public <T> QueryResultIterator<T> find(Class<T> clazz){
		RootFindCommand<T> fc = find().type(clazz);
		return fc.now();
	}
	public class FindCommand{
		public <T> RootFindCommand<T> type(Class<T> clazz){
			return new RootFindCommand<T>(clazz);
		}
	}
	public class RootFindCommand<T>{
		private Class<T> clazz = null;
		private BasicDBObject searchQuery = new BasicDBObject();
		public RootFindCommand(Class<T> clazz) {
			this.clazz=clazz;
		}
		public RootFindCommand<T> addFilter(String fieldName, String operator, Object value){
			if (operator == null || operator.equals("")){
				searchQuery.append(fieldName, value); 
			} else {
				searchQuery.append(fieldName, new BasicDBObject(operator,value));
			}
			return this;
		}
		public QueryResultIterator<T> now(){
			String collectionName = clazz.getCanonicalName().toLowerCase()
					+ "s";
			DBCollection table = ParallelInit.getDatastore().getCollection(
					collectionName);
			//TODO: Spannend:)
			DBCursor cursor = table.find(searchQuery);
			return new QueryResultIterator<T>(clazz,cursor);
		}
	}
	
}
