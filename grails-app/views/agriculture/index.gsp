<!DOCTYPE html>
<html>
  <head>
    <link rel='stylesheet' href='css/bootstrap.css'/>
    <link rel='stylesheet' href='http://fonts.googleapis.com/css?family=Source+Sans+Pro' type='text/css'/>
    <link rel='shortcut icon' href='favicon.ico'/>
    <link rel='stylesheet' href='css/style.css'/>
    <script type="text/javascript" src="https://ajax.googleapis.com/ajax/libs/jquery/2.1.3/jquery.min.js"></script>
    <script src='js/justgage.1.0.1.min.js'></script>
    <script src='js/raphael.2.1.0.min.js'></script>
    <title>flowthings.io agriculture app</title>
  </head>
  <body>
    <div>
      <div class="text-center">
        <a style="display:inline-block;" target="_blank" href="http://flowthings.io" >
        <object type="image/svg+xml" data="images/flowthings.svg">     
          <img src="images/flowthings.png"/>     
        </object>
        </a> 
      </div>
      <h2 class="text-center" style="margin-top: 5px">Grails Smart-Agriculture demo</h2>
    </div>
  
    <div style="width:300px; margin-left:auto; margin-right:auto">
      <div id="tempGage" style="width:300px;height:200px"></div>
      <div id="moistureGage" style="width:300px;height:200px"></div>
      <div class="text-center">
        <span style="font-weight:bold;font-size:20px">Rain Forecasted? </span>
        <div id="rainForecasted" class="text-result">No</div></div>
      <div class="text-center">
        <span style="font-weight:bold;font-size:20px">Water On? </span>
        <div id="waterOn" class="text-result">No</div></div>
    </div>
  </body>
  <script>
    var connection;

    var tempGage;
    var moistureGage;

    var gageFontColor = "#ffffff";

    tempGage = new JustGage({
      id: 'tempGage',
      titleFontColor: gageFontColor,
      valueFontColor: gageFontColor,
      value: 0,
      min: 30,
      max: 80,
      title: 'Temperature'
    });
    moistureGage = new JustGage({
      id: 'moistureGage',
      titleFontColor: gageFontColor,
      valueFontColor: gageFontColor,
      value: 0,
      min: 0,
      max: 50,
      title: 'Soil Moisture'
    });
   

    
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

            if (category === "temperature"){
              tempGage.refresh(value);
            } else if (category === "soilMoisture"){
              moistureGage.refresh(value);  
            } else {
              $("#" + category).text(value ? "Yes" : "No")
            }  
          }            
        };
      }
    });
  </script>
</html>
