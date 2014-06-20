package com.almende.util.twigmongo;

public class FilterOperator {
	private String operator=null;
	
	public static final FilterOperator EQUAL = new FilterOperator("");
	public static final FilterOperator NOT_EQUAL = new FilterOperator("$ne");
	public static final FilterOperator IN = new FilterOperator("$in");
	public static final FilterOperator NON_IN = new FilterOperator("$nin");
	public static final FilterOperator LESS_THAN_OR_EQUAL = new FilterOperator("$lte");
	public static final FilterOperator LESS_THAN = new FilterOperator("$lt");
	public static final FilterOperator GREATER_THAN_OR_EQUAL = new FilterOperator("$gte");
	public static final FilterOperator GREATER_THAN = new FilterOperator("$gt");
	
	public FilterOperator(){
		this.operator="";
	}
	public FilterOperator(String operator){
		this.operator=operator;
	}
	public String toString(){
		return this.operator;
	}
	public String getOperator() {
		return operator;
	}
	public void setOperator(String operator) {
		this.operator = operator;
	}
}
