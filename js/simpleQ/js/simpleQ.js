/**
 *   SimpleQ Question widget
 *   Copyright: Ludo Stellingwerff - Almende B.V.
 *   License: Apache License 2.0
 */

(function($) {
	$.fn.simpleQ = function(options) {
		var settings = {
			question_text:"",
			answers:[], //array of {answer_text:'',callback:function()}
			urls:false,
			removeOnAnswer:true,
			close_callback:null,
			vertical_answers:false,
		};
		if(options) {
			$.extend(settings, options);
		};
		
		function dataurl2string(url){
			if (url){
				return decodeURIComponent(url.replace(/data:,?/,""));
			} else {
				console.log("Error, dataurl2string called with null URL");
				return "";
			}
			
		}
		return this.each(function(){
		      var $this = $(this);
		      var question = $("<div class='dialog_question'></div>");
		      if (settings.answers && settings.answers.length > 0){
		    	  settings.answers.map(function(answer){
		    		  var answerObj = $("<div class='dialog_answerBox'></div");
		    		  if (settings.urls){ 
		    			  answerObj.html(dataurl2string(answer.answer_text));
		    		  } else { 
		    			  answerObj.html(answer.answer_text);
		    		  }
		    		  answerObj.bind('click',function(event){
		    			  answer.callback();
		    			  if(settings.removeOnAnswer) question.remove();
		    		  });
		    		  question.append(answerObj);		    		  
		    	  });
		    	  question.wrapInner('<div class="dialog_answersBox"></div>');
		      }
		      var closeButton = $("<div class='dialog_closeButton'/>").bind('click',function(event){
		    	 if (typeof settings.close_callback == "function") settings.close_callback(); 
		    	 question.remove(); 
		      });
		      var questionObj = $("<div class='dialog_questionBox'/>");
    		  if (settings.urls){ 
    			  questionObj.html(dataurl2string(settings.question_text)); //should be .load but cross-domain scripting rules prevent that.....:(
    		  } else { 
    			  questionObj.html(settings.question_text);
    		  }
    		  questionObj.append(closeButton);
		      question.prepend("<div class='dialog_questionLip'></div>").prepend(questionObj);
		      $this.append(question);
		})
	}
})(jQuery);


