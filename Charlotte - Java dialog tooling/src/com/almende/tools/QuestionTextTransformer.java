package com.almende.tools;

import flexjson.BasicType;
import flexjson.TypeContext;
import flexjson.transformer.AbstractTransformer;

public class QuestionTextTransformer extends AbstractTransformer {
	
	boolean expanded_texts = false;
	public QuestionTextTransformer(boolean expanded_texts) { 
		this.expanded_texts = expanded_texts; 
	}
	public QuestionTextTransformer() { 
	}
	
	public void transform(Object o) { 
		boolean setContext = false; 
		TypeContext typeContext = getContext().peekTypeContext(); 
		String propertyName = typeContext != null ? typeContext.getPropertyName() : "";
		
		if (expanded_texts && propertyName.matches(".*_text")){
			return;
		}
		if (!expanded_texts && propertyName.matches(".*_expandedtext")){
			return;
		}
		if (propertyName.equals("question_expandedtext")) propertyName="question_text";
		if (propertyName.equals("answer_expandedtext")) propertyName="answer_text";
		
		if (typeContext == null || typeContext.getBasicType() != BasicType.OBJECT) { 
			typeContext = getContext().writeOpenObject(); 
			setContext = true; 
		} 
		if (!typeContext.isFirst()) getContext().writeComma(); 
		typeContext.setFirst(false);
		
		getContext().writeName(propertyName);
		getContext().writeQuoted((String)o);
		if (setContext) { 
			getContext().writeCloseObject(); 
		} 
	} 
	
	@Override public Boolean isInline() { return Boolean.TRUE; } 
}
