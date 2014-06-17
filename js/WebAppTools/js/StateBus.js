/**
 * StateBus - An eventHandler-like state sharing mechanism. Copyright: Ludo
 * Stellingwerff - Almende B.V. License: Apache License 2.0
 * 
 * Features: 
 * >>State sink-instances 
 * >>Callback subscription (single call/scheduled call) 
 * >>Subscription on not-yet-existing states 
 * >>Chainable bus
 * >>Single instance, multiple states, plugin
 * 
 */

(function($) {
	$.StateBus = function(options) {
		var sb = this; 
		var states = {};
		var callbacks = {};
		var settings = {

		};
		if(options) {
			$.extend(settings, options);
		};

		function createUUID() {
			var chars = '0123456789abcdef'.split('');

			var uuid = [], rnd = Math.random, r;
			uuid[8] = uuid[13] = uuid[18] = uuid[23] = '-';
			uuid[14] = '4';
			// version 4

			for(var i = 0; i < 36; i++) {
				if(!uuid[i]) {
					r = 0 | rnd() * 16;

					uuid[i] = chars[(i == 19) ? (r & 0x3) | 0x8 : r & 0xf];
				}
			}

			return uuid.join('');
		}

		return {
			sink : function(state) {
				return {
					setState : function(value) {
						var callbacks=[];
						if (states[state]){
							callbacks = states[state].callbacks;
						}
						states[state] = {
							value : value,
							time : new Date(),
							callbacks:callbacks  //TODO: remove from state, Move to higher level? At least copy from old state
						}
						callbacks.map(function(cb){
							sb.callbacks[cb].callback(states[state].value,states[state].time);
						})
					}
				}
			},
			subscribe : function(state, callback, interval) {
				var callback_id = createUUID();
				if(states[state]) {
					var cur = states[state];
					var cb = {
						state:cur,
						callback:callback
					}
					if (interval != null && interval > 0){
						$.extend(cb,{interval:setInterval(function(){ callback(states[state].value,states[state].time)}, interval)});					
					}
					callbacks[callback_id]=cb;
					cur.callbacks.push(callback_id);
					return callback_id;
				} else {
					console.log("Subscribing on non-existing state");
				}
			},
			unsubscribe : function(callback_id) {
				if (callbacks[callback_id]){
					var cb = callbacks[callback_id];
					var cur = cb.state;
					delete(callbacks[callback_id]);
					delete(cur.callbacks[callback_id]);
					if (cb.interval){
						clearInterval(cb.interval);	
					}
				}
			}
		}
	}
})(jQuery);
