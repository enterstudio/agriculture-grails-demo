package flowthings.grails.demo

import static com.flowthings.client.api.Flowthings.*

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.flowthings.client.Credentials
import com.flowthings.client.QueryOptions
import com.flowthings.client.api.RestApi
import com.flowthings.client.api.WebsocketApi
import com.flowthings.client.domain.Drop
import com.flowthings.client.domain.Flow
import com.flowthings.client.domain.Token
import com.flowthings.client.domain.TokenPermissions
import com.flowthings.client.domain.Track
import com.flowthings.client.exception.FlowthingsException

/**
 * Simple controller for the Smart Agriculture flowthings.io Grails app.
 * 
 * This makes use of the flowthings.io Java Client library
 */
class AgricultureController {



  /**
   * Set up to use the flowthings.io REST api.
   */
  def creds = Credentials.fromBluemixOrDefault(new Credentials("<your flowthings.io account name>", "<your master token>"))
  def api = new RestApi(creds)

  def account = creds.account

  def basePath = "/$account/agriculture"
  def inputPath = "/$account/agriculture/inputs"
  def outputPath = "/$account/agriculture/commands"

  Flow baseFlow;
  Flow inputFlow;
  Flow outputFlow;
  Token deviceToken;

  def rainSimulator, moistureSimulator, temperatureSimulator;
  def scheduler = Executors.newScheduledThreadPool(5);


  /**
   * On app startup, check if the flowthings.io objects have been created. If they have,
   * go and grab references to them. Otherwise we need to create... 
   */
  def getFlowsAndToken () {
    api.send(flow().find(new QueryOptions().filter("path==\"${inputPath}\""))).each { flow ->
      inputFlow = flow;
      println "Got Input Flow : ${inputFlow.getId()}"
    }

    if (inputFlow == null){
      setup()
    } else {
      api.send(flow().find(new QueryOptions().filter("path==\"${outputPath}\""))).each { flow ->
        outputFlow = flow;
        println "Got Output Flow : ${outputFlow.getId()}"
      }
      api.send(token().find(new QueryOptions().filter("description==\"device token\""))).each { token ->
        deviceToken = token;
        println "Got Device Token : ${deviceToken.getId()}"
      }
    }

    if (rainSimulator == null){
      startSimulatingData()
    }
  }

  /**
   * Create the flowthings.io event processing application 
   */
  def setup () {

    /**
     * Create Flow objects
     */
    baseFlow = new Flow.Builder().setPath(basePath).get()
    inputFlow = new Flow.Builder().setPath(inputPath).get()
    outputFlow = new Flow.Builder().setPath(outputPath).get()

    try {
      baseFlow = api.send(flow().create(baseFlow))
      inputFlow = api.send(flow().create(inputFlow))
      outputFlow = api.send(flow().create(outputFlow))

      /**
       * Define JS code to be executed on Flowthings.io in the cloud
       * The logic works like this:
       * 
       * 1. Readings from soil, temperature, and weather sensors (simulated)
       * are sent directly to the /<account>/agriculture/input Flow
       * 
       * 2. Each time a reading arrives, the below JS is run on flowthings.io
       * to decide whether or not to switch on the watering system.
       * 
       * 3. A command is sent to turn on or off the watering system to
       * the /<account>/agriculture/commands Flow
       *
       */
      def js = """
			function (input) {		
				var thisCategory = input.fhash;
				
				var allInputs = Flow.Drop.find ("${inputPath}")
						
				var readings = {}
				
				allInputs.forEach (function (item) {
					readings[item.fhash] = item.elems.reading.value;	
				});
				
				readings[thisCategory] = input.elems.reading.value;
				
				// Some demo logic to determine whether or not to water plants
				var shouldWater = false;
				
				// If rain expected, water only at last resort
				if (readings.rainForecasted){
					if (readings.soilMoisture < 5) shouldWater = true;
				} else {
					shouldWater = (readings.temperature >= 50 && readings.soilMoisture <= 20)
						|| readings.soilMoisture < 10
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

      commandTrack = api.send(track().create(commandTrack))


      /**
       * Create a limited token for our devices, so they
       * don't have unlimited access to our app which would
       * be dangerous
       */
      deviceToken = new Token.Builder()
          .addPath(inputFlow.getPath(),new TokenPermissions(true, true))
          .addPath(outputFlow.getPath(),new TokenPermissions(true, false))
          .setDescription("device token").get();
      deviceToken = api.send(token().create(deviceToken))
    } catch (FlowthingsException e) {
      e.printStackTrace()
      render "There was a problem!"
    }
  }

  /**
   * This is just a demo - we don't have real sensors sending us data. So
   * start some tasks to periodically send sample datsa. 
   */
  def startSimulatingData(){
    def credentials = new Credentials(account, deviceToken.getTokenString())
    rainSimulator = scheduler.scheduleAtFixedRate(
        new RainForecastSimulator(credentials, inputFlow, outputFlow), 0, 2, TimeUnit.SECONDS)
    moistureSimulator = scheduler.scheduleAtFixedRate(
        new MoistureSimulator(credentials, inputFlow, outputFlow), 0, 2, TimeUnit.SECONDS)
    temperatureSimulator = scheduler.scheduleAtFixedRate(
        new TemperatureSimulator(credentials, inputFlow, outputFlow), 0, 2, TimeUnit.SECONDS)
  }

  /**
   * Render the main page
   */
  def index(){
    if (inputFlow == null){
      getFlowsAndToken()
    }

    render(view : "index", model : [account : account, token : deviceToken.getTokenString(),
      inputPath : inputPath, outputPath : outputPath])
  }
}

/**
 * Task to send simulated Moisture Sensor readings. 
 * When a command is received to turn on the watering system, 
 * the moisture value is reset to maximum
 */
class MoistureSimulator extends Simulator {

  def moisture = 50;

  def MoistureSimulator(Credentials credentials, Flow inputFlow, Flow outputFlow){
    super(credentials, inputFlow, outputFlow)
    websocketApi.send(drop(outputFlow.getId()).subscribe({ Drop drop ->
      if ("waterOn".equals(drop.getFhash()) && drop.getElems().get("command") == true){
        // The plant has been watered
        moisture = 50;
        println "Received water command, resetting moisture"
      }
    }));
  }

  @Override
  public void run() {
    try {
      // Decrease moisture
      moisture = Math.max(0, moisture - 5);

      // Send
      println "Sending Moisture: ${moisture}"
      websocketApi.send(drop(inputFlow.getId()).create(moistureData())).get()
    } catch (Exception e) {
      e.printStackTrace()
    }
  }

  def moistureData() {
    return new Drop.Builder().setFhash("soilMoisture").addElem("reading",moisture).get()
  }
}

/**
 * Task to send simulated Temperature Sensor readings
 */
class TemperatureSimulator extends Simulator {

  def temperature = 60;
  def random = new Random()

  def TemperatureSimulator(Credentials credentials, Flow inputFlow, Flow outputFlow){
    super(credentials, inputFlow, outputFlow)
  }

  @Override
  public void run() {
    try {
      // Vary temperature
      temperature = temperature + random.nextInt(11) - 5
      temperature = Math.max(30, Math.min(temperature, 80));

      // Send
      println "Sending Temperature: ${temperature}"
      websocketApi.send(drop(inputFlow.getId()).create(temperatureData())).get()
    } catch (Exception e) {
      e.printStackTrace()
    }
  }

  def temperatureData() {
    return new Drop.Builder().setFhash("temperature").addElem("reading",temperature).get()
  }
}

/**
 * Task to send simulated Rain Forecast readings
 */
class RainForecastSimulator extends Simulator {

  def rainForecasted = false;
  def random = new Random()

  def RainForecastSimulator (Credentials credentials, Flow inputFlow, Flow outputFlow){
    super(credentials, inputFlow, outputFlow)
  }

  @Override
  public void run() {
    try {
      // Flip rain forecast (20% chance of doing so)?
      def shouldFlip = random.nextInt(100) > 80;
      rainForecasted = rainForecasted ^ shouldFlip

      // Send
      println "Sending Rain Forecasted: ${rainForecasted}"
      websocketApi.send(drop(inputFlow.getId()).create(forecastData())).get()
    } catch (Exception e){
      e.printStackTrace();
    }
  }

  def forecastData() {
    return new Drop.Builder().setFhash("rainForecasted").addElem("reading",rainForecasted).get()
  }
}

abstract class Simulator implements Runnable {

  def websocketApi;
  def inputFlow;
  def outputFlow;

  def Simulator(Credentials credentials, Flow inputFlow, Flow outputFlow){
    this.websocketApi = new WebsocketApi(credentials);
    this.inputFlow = inputFlow;
    this.outputFlow = outputFlow;
  }
}
