import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import java.util.ResourceBundle;
import java.util.Date;
import java.util.prefs.Preferences;
import java.text.SimpleDateFormat;
import javafx.application.Platform;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;

import com.google.gson.Gson;


public class FXMLTempAppController implements Initializable {

   // All of the variables referenced from the FXML file
   // The annotations are required to tie to Scene Builder   
   @FXML 
   private Button refreshForecast;
    
   @FXML 
   private RadioButton celsiusRadio;
   @FXML 
   private RadioButton fahrenheitRadio;
    
   @FXML 
   private Label updateLabel;
   @FXML 
   private Label tempLabel;
   @FXML 
   private Label locationLabel;
   @FXML 
   private Label highTempLabel;
   @FXML 
   private Label lowTempLabel;

   // Variables used internally, not tied to FXML document

   // Used to retrieve data from the API   
   private HttpClient client;
   
   // Top-level class heirarchy that saves the GSON processed JSON
   private Temperature temperature; 
   
   // An enum that represents what units to be used for the temperature
   private enum Unit { CELSIUS, FAHRENHEIT };
   private Unit unit;
   
   // Keeps track of last time the weather data was updated
   private Date updateTime;
   
   // Used as the key to persist the temperature unit in preferences
   public static final String TEMP_UNIT = "temp_unit_key";
  

   // Action to perform when the refresh button is pressed
   @FXML 
   protected void handleRefreshButtonAction(ActionEvent event) {
      updateWeatherData();       
   }

   // Action to perform when the unit radio buttons are changed
   @FXML
   protected void handleUnitConversionRadioButtonAction(ActionEvent event) {
       
      // Update units based on which radio button was pressed
      if(event.getSource() == celsiusRadio)
         this.unit = Unit.CELSIUS;
      else if(event.getSource() == fahrenheitRadio)
         this.unit = Unit.FAHRENHEIT;
      
      // Save new preference to key/value store
      Preferences p = Preferences.userNodeForPackage(FXMLTempAppController.class);
      p.put(TEMP_UNIT, this.unit.toString() );
      
      // Update just the UI to show changes, no need to make another API call
      updateUI();
   }

   // Update the GUI to reflect changes. 
   // Simple app, update anything and everything all of the time
   protected void updateUI() {

      // Set the temp label
      tempLabel.setText( String.format("%d\u00B0", Math.round( getTempInProperUnit(this.temperature.current.temp) ) ) );

      // Update min & max temperatures (hi and low for the day)
      highTempLabel.setText( String.format("%d\u00B0", Math.round( getTempInProperUnit(temperature.daily[0].temp.min) ) ) );    
      lowTempLabel.setText( String.format("%d\u00B0", Math.round( getTempInProperUnit(temperature.daily[0].temp.max) ) ) );     
   
      // Update the time data was refreshed.
      SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yy hh:mm a");
      updateLabel.setText( fmt.format(this.updateTime) );
   }
   
   // Helper method to convert the temperature to proper units
   // The API gives me the temp in Kelvin
   // I could specify this in the API call to get back the 
   //    proper unit but this allows me to avoid making extra API calls.
   private double getTempInProperUnit(double t) {
      
      if(this.unit == Unit.CELSIUS)
         return t - 273.15;
      else if(this.unit == Unit.FAHRENHEIT)
         return (t - 273.15) * 9.0/5.0  + 32;
       else
         return t; //Kelvin, in case we ever need this.         
   
   }

   // Once the weather data is downloaded, this method is called
   //    to parse the JSON and create a plain old java object (POJO)
   protected void processWeatherData(String data) {
            
      // Save the time this data was retrieved to be displayed in the GUI
      this.updateTime = new Date();
      
      // Some debugging text for the console. Allows us to view returned JSON
      System.out.println(data);      
      
      // Use GSON to convert the JSON to a POJO (whew, that's a lot of acronyms!)
      Gson gson = new Gson();
      this.temperature = gson.fromJson(data, Temperature.class);      
            
      // Schedule UI updates on the GUI thread
      // This is important because the data download happens in the background
      Platform.runLater( new Runnable() {
                           public void run() {
                              updateUI();
                           }
                        });
   }
   
   // This method is run when the user hits the refresh button and the app is initialized
   protected void updateWeatherData() {
   
      // Only create the HttpClient one time
      // The app can use the same client for its entire life
      if(this.client == null)
         this.client = HttpClient.newHttpClient();
    
      // Make the api call on a background thread
      // This is an asynchronous call
      // This allows the app to be responsive while the data is being retrieved
      // In reality, we should show a spinner or other update UI element to let the user know they are waiting.
      //
      // My API call is hardcoded, yours may need to be built from data you collect from the user
      try {
            // Don't store your API key in your source code to avoid it being committed to version control
            // Set this in your environment. When you distribute the app, how you store and distribute this value is something to address.
            // We will review how I set this value in class.
            HttpRequest request = HttpRequest.newBuilder()
               .uri(new URI("https://api.openweathermap.org/data/2.5/onecall?lat=40.17423766234042&lon=-75.27796262961655&exclude=minutely,hourly&appid=" + System.getenv("APIKEY") ))
               .GET()
               .build();
                             
           // Use Java's new :: method reference syntax to schedule callbacks to handle the data once retrieved
           // A callback is code to run later when the data is ready.
           // Note that HttpResponse::body is the same as calling the HttpResponse classes method body() on the response instance returned
           // Note that this::processWeatherData is the same as this.processWeatherData() 
           client.sendAsync(request, BodyHandlers.ofString())
               .thenApply(HttpResponse::body)
               .thenAccept(this::processWeatherData);     
           
          } catch(URISyntaxException e) { 
            // This message can be more informative if your API is more complex
            System.out.println("Issue with request");
          }
          
      // Some debugging text for the console.      
      // Again, this should also be reflected in the GUI
      System.out.println("Updating Weather...");
      
   }
   
   // This method implements the Initializable interface
   // This is how we can respond to the scene "waking up"
   @Override
   public void initialize(URL location, ResourceBundle resources) {
   
      //This runs as soon as this view comes to life
      // Use this to get preferences, set defaults, and make first call for data

      // Read which unit to use from preferences
      // App currently defaults to fahrenheit
      Preferences p = Preferences.userNodeForPackage(FXMLTempAppController.class);
      this.unit = Unit.valueOf( p.get(TEMP_UNIT, Unit.FAHRENHEIT.toString() ) );
      
       if(this.unit == Unit.CELSIUS)
         this.celsiusRadio.setSelected(true);
       else
         this.fahrenheitRadio.setSelected(true);

      // Get new weather data
      // This is the same method called when the refresh button is pressed.
      updateWeatherData();
   
   }
   
   
}