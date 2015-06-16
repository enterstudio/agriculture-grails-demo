<!DOCTYPE html>
<html>
	<head>
		<script type="text/javascript" src="https://ajax.googleapis.com/ajax/libs/jquery/2.1.3/jquery.min.js"></script>
		<title>flowthings.io agriculture app</title>
	</head>
	<body>
		<div><span>Rain Forecasted? </span><span id="rainForecasted"></span></div>
		<div><span>Temperature: </span><span id="temperature"></span></div>
		<div><span>Soil Moisture: </span><span id="soilMoisture"></span></div>
		<div><span>Water On? </span><span id="waterOn"></span></div>
	</body>
	<script language="JavaScript">
		var connection;

		/**
		 * Send a periodic heartbeat to the WS connection, to
		 * keep it alive
		 */
		function heartbeatWS() {
		  connection.send(JSON.stringify({
		    "type": "heartbeat"
		  }));
		}
	
		function subscribe(path){
			return JSON.stringify({
                "msgId": "subscribe-request",
                "object": "drop",
                "type": "subscribe",
                "path": path
            });
		}

		/**
		 * Set up the WebSocket connection
		 */
		$.ajax({
			url: "https://ws.flowthings.io/session",
	        beforeSend: function(req) {
	        	req.setRequestHeader("X-Auth-Account", "${account}");
	        	req.setRequestHeader("X-Auth-Token", "${token}");
	        	req.withCredentials = true
	        },
	        type: "post",
	        dataType: 'json',
	        success: function(data) {
	
	          var sessionId = data["body"]["id"]
	          var url = "wss://ws.flowthings.io/session/" + sessionId + "/ws";
	
	          connection = new WebSocket(url);
	
	          connection.onopen = function() {

	        	/**
	      		 * Subscribe to input and output events, so we can
	      		 * render them in the GUI
	      		 */
	            connection.send(subscribe("${inputPath}"));
	            connection.send(subscribe("${outputPath}"));
	            var counter = setInterval(heartbeatWS, 10000);
	          };
	          connection.onerror = function(error) {
	            console.log('WebSocket Error ' + error);
	          };
	          connection.onmessage = function(e) {
	            var message = JSON.parse(e.data);
	            if (message.value){

	            	/**
	        		 * When an event is received, render it on screen
	        		 */
					var category = message.value.fhash;
					var value = category === "waterOn" ? message.value.elems.command.value : message.value.elems.reading.value;
					$("#" + category).text(value)	
	            }            
	          };
	        }
	      });
	</script>
</html>
