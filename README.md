## flowthings.io Smart Agriculture Grails Demo

A simple smart-agriculture demo using [flowthings.io](https://flowthings.io) and Grails 2.4.x

To see it live, visit:

[http://grails-ag.mybluemix.net](http://grails-ag.mybluemix.net)

#### Self-deploy installation instructions
Add your flowthings.io account name and master token to the following file:
```
grails-app/controllers/flowthings/grails/demo/AgricultureController.groovy
```

Run the application:
```sh
grails run-app
```

Navigate to:
```
http://localhost:8080/flowthings-grails-demo
```

#### IBM Bluemix installation instructions

*Note:* You may need to increase the app memory limit to 1GB if deployment failures occur

Build the war: 
```sh
grails war
```

Login to the Bluemix space:
```sh
cf login -u <ibm username> -o <organization name> -s <space name>
```

If it doesn't already exist in this space, create a flowthings.io service
```sh
cf create-service flowthings free flowthings
```

Push using the custom Tomcat buildpack
```sh
cf push grails-ag -p target/work/tomcat/webapps/flowthings-grails-demo-0.1.war -b https://github.com/cloudfoundry/java-buildpack.git -f manifest.yml --random-route
```

Follow the progress by tailing the logs (in another terminal):
```sh
cf logs grails-ag
```

Go to the bluemix dashboard, then navigate to the route given

### Summary
The flowthings.io Grails Smart-Agriculture demo demonstrates [flowthings.io](flowthings.io) realtime event processing to create a smart hydration system for crops based on soil moisture, ambient temperature, and weather forecasts.

The demo has been built using:

* Grails 2.4
* IBM Bluemix 
* The [flowthings.io Java Client](https://github.com/flowthings/java-client)

The flowthings.io Java Client wraps REST and WebSockets usage in a type-safe, idiomatic library for Java.

This demo consists of the following:

* An input [Flow](https://flowthings.io/docs/flow-object-overview), which serves as an event stream for all incoming sensor readings and weather data
* An output Flow, from which the a hypothetical device would receive commands to turn on or off the hydration system
* A [Track](https://flowthings.io/docs/track-object-overview) object, which connects input and output, and contains the JavaScript code to determine when the hydration system should be turned on. This code is run in the cloud whenever new readings are received.
* A restricted [Token](https://flowthings.io/docs/token-object-overview), which grants limited access to the hypothetical sensor devices. This is to protect other areas of the application.
* Recurring tasks to simulate the sending of sensor data. Every 2 seconds, a new reading will be sent to flowthings.io

The main components of the demo are:

* A Grails Controller : `grails-app/controllers/flowthings/grails/demo/AgricultureController.groovy`
* A Grails View : `grails-app/views/agriculture/index.gsp`

#### Authenticating with flowthings.io
To begin, we create a `RestApi` object, which the controller will use to send commands to flowthings.io.

```groovy
def creds = Credentials.fromBluemixOrDefault(
    new Credentials("<your flowthings account name>", "<your master token>"))
  
def api = new RestApi(creds)
```

If the demo is being hosted on [IBM Bluemix](http://console.ng.bluemix.net), the credentials for the flowthings.io Bluemix service will be automatically retrieved. Otherwise, an account name and token can be supplied once we've registered a new [flowthings.io account](https://auth.flowthings.io/register).

#### Querying for flowthings.io objects
On load, the controller checks to see if we've already created the Flow and Track objects on flowthings.io. If not, it will use the Java Client to do so.

```groovy
import static com.flowthings.client.api.Flowthings.*

// ...
def basePath = "/$account/agriculture"
def inputPath = "/$account/agriculture/inputs"
def outputPath = "/$account/agriculture/commands"
// ...

api.send(flow().find(new QueryOptions().filter("path==\"${inputPath}\""))).each { 
    flow ->
        inputFlow = flow;
        println "Got Input Flow : ${inputFlow.getId()}"
}

if (inputFlow == null){
    setup()
}
```

Interacting with the client involves building `Request<>` objects and sending through either the REST or WebSockets APIs.

In the code above, we create a new [Flow Find](https://flowthings.io/docs/http-flow-find) request. We supply the `path` we are looking for, using [flowthings.io Filter Language](https://flowthings.io/docs/flowthings-filter-language). The API will return a `List<Flow>` object. If empty, we need to bootstrap the application.

#### Creating the flowthings.io application
To create objects such as Flows and Tracks, we can use the `Builder` interface:

```groovy
def baseFlow = new Flow.Builder().setPath(basePath).get()
def inputFlow = new Flow.Builder().setPath(inputPath).get()
def outputFlow = new Flow.Builder().setPath(outputPath).get()
```

We then issue a [Flow Create](https://flowthings.io/docs/http-flow-create) request to the API. The response object will be the full Flow object (including attributes such as `id`, `creationDate` and `capacity`). Every flowthings.io object has an `id`, and it is important for making future requests.

```groovy
baseFlow = api.send(flow().create(baseFlow))
inputFlow = api.send(flow().create(inputFlow))
outputFlow = api.send(flow().create(outputFlow))
```

If there is a problem during this operation, e.g. if a Flow already exists with that path, a `FlowthingsException` will be thrown.

The Track object contains a JavaScript function that will execute whenever a new input reading arrives, and will push command events to the output Flow.

```groovy
def js = """
    function (input) {

        // What type of reading is this? Temperature / moisture etc.      
        var thisCategory = input.fhash;
        
        // Collect all the latest readings
        var allInputs = Flow.Drop.find ("${inputPath}")
                
        var readings = {}
        
        allInputs.forEach (function (item) {
            readings[item.fhash] = item.elems.reading.value;    
        });
        
        readings[thisCategory] = input.elems.reading.value;
        
        // Some simple logic to determine whether or not to water plants
        var shouldWater = false;
        
        // If rain expected, water only at last resort
        if (readings.rainForecasted){
            if (readings.soilMoisture < 5) shouldWater = true;
        } else {
            shouldWater = (readings.temperature >= 50 && readings.soilMoisture <= 20) || readings.soilMoisture < 10
        }
        
        var alert = { fhash : "waterOn", elems : { command : shouldWater } };
        return alert;
    }
"""

def commandTrack = new Track.Builder()
    .setSource(inputPath)
    .setDestination(outputPath)
    .setJs(js)
    .get()

api.send(track().create(commandTrack))
```

Finally we will create a limited Token object. When devices are released into the wild, we want to be sure that they only have access to a small part of the application. In this case, they only need access to the input and output Flows. They also won't be able to delete any Flow objects.

```groovy
// Read + Write access to the input
// Read-only access to the output

deviceToken = new Token.Builder()
    .addPath(inputFlow.getPath(), new TokenPermissions(true, true))
    .addPath(outputFlow.getPath(), new TokenPermissions(true, false))
    .setDescription("device token").get();

deviceToken = api.send(token().create(deviceToken))
```

#### Sending data
The controller spawns multiple recurring tasks to represent our devices. Each one establishes a WebSockets connection to send readings to the input Flow. 

In flowthings.io, events are called [Drops](https://flowthings.io/docs/drop-object-overview). Any data can be put inside a Drop. Each Drop has an attribute `elems`. On the flowthings.io platform, `elems` is just a JSON object. In the Java Client, `elems` is of type `Map<String, Object>`. This is where we put our readings.

```groovy
def moisture = 50;
def websocketApi = new WebsocketApi(credentials);

// ...

 // Decrease moisture
moisture = Math.max(0, moisture - 5);

// Send
def drop = new Drop.Builder()
    .setFhash("soilMoisture")
    .addElem("reading",moisture)
    .get()

Future<Response<Drop>> response = 
    websocketApi.send(drop(inputFlow.getId()).create(drop))
```

The WebSocket API works almost in a similar way to the REST API, except that all requests return a `Future<Response<T>>`. This is because a WebSocket is naturally asynchronous as opposed to request/response.

#### Receiving realtime commands
We want to receive commands from the output Flow as soon as they are available. The WebSocket API allows us to "subscribe" to all incoming Drops for a particular Flow. When a command to turn on / off the hydration system is generated, we want to receive it immediately.

```groovy
websocketApi.send(drop(outputFlow.getId()).subscribe({ 
    Drop drop ->
      if ("waterOn".equals(drop.getFhash()) 
            && drop.getElems().get("command")){

        // The crop shall be watered
        moisture = 50;
      }
}));
```

The code above shows how we pass a callback to the subscribe command. This will be executed whenever a new Drop is received.
