import java.net.*;
import org.eclipse.paho.client.mqttv3.*;
import java.io.*;
import java.util.*;
import java.util.Arrays;
import java.io.FileWriter;   // Import the FileWriter class
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class Application 
{
    public static String MQTT_BROKER = "";
    public static final String MQTT_TOPIC = "device/status";
    public static MqttClient client;
    public static final String deviceCategoryList[] = new String[]{"Sensor","Actuator","Controller","Application"};
    public static final String deviceTypeSensor[] = new String[]{"Temperature","Humidity","Light"};
    public static final String deviceTypeActuator[] = new String[]{"Fan","Pump","Light"};
    static volatile boolean finished = false;
    public static String messageNotify="";
    public static String adrress;
    public static boolean MqttInitialized = false;
    public static String sensorId;
    public static void main(String[] args)  throws InterruptedException
    {
        if(args.length == 2){
            try
            {
                ApplicationGUI.drawNewFrame("Application");

                String data = SocketFunctionsApplication.getIndexDevice(deviceCategoryList,args[1]);
                System.out.println(data);
                if(args[1].equals("Sensor")){
                    data = data + "|" + SocketFunctionsApplication.getIndexDevice(deviceTypeSensor,args[2]) + "|" + args[3];
                }
                else if(args[1].equals("Actuator")){
                    data = data + "|" + SocketFunctionsApplication.getIndexDevice(deviceTypeActuator,args[2]) + "|" + args[3];
                }
                else if(args[1].equals("Controller")){
                    data = data + "|2|0";
                }
                else
                    data = data + "|3|0";
                //adrress = SocketFunctionsSensor.getIpAddress();
                adrress = args[0];
                String messageMSearch = 
                    "HOST:"+ adrress +"\n"+
                    "ssdp:msearch\n"+ 
                    "type:Application";
                messageNotify = 
                    "HOST:" + adrress + "\n"+
                    "ssdp:notify\n"+
                    "type:Application\n"+
                    "data:" + data;
                String mqttData = "20";
                InetAddress group = InetAddress.getByName("239.255.255.250");
                int port = 1900;
                MulticastSocket socket = new MulticastSocket(port);
                //socket.setLoopbackMode(true);
                // Since we are deploying
                socket.setTimeToLive(1);
                //this on localhost only (For a subnet set it as 1)
                  
                socket.joinGroup(group);
                SocketFunctionsApplication.sendData(messageMSearch,group,port,socket);
                Thread t = new Thread(new
                ReadThreadApplication(socket,group,port));
                t.start();
                SocketFunctionsApplication.sendData(messageNotify,group,port,socket);
                while(true)
                {
                    Thread.sleep(3000);
                    SocketFunctionsApplication.sendData(messageNotify,group,port,socket);
                    if(!Application.MQTT_BROKER.equals("")){
                        MqttHelperApplication.sendMqttData();
                    }
                }
            }
            catch(SocketException se)
            {
                System.out.println("Error creating socket");
                se.printStackTrace();
            }
            catch(IOException ie)
            {
                System.out.println("Error reading/writing from/to socket");
                ie.printStackTrace();
            }
            // catch (MqttException e) {
            //     System.out.println("Error publishing message");
            //     e.printStackTrace();
            // }
        }
        else
            return ;
    }
    
}
class ReadThreadApplication implements Runnable
{
    private MulticastSocket socket;
    private InetAddress group;
    private int port;
    public static final int MAX_LEN = 1000;
    ReadThreadApplication(MulticastSocket socket,InetAddress group,int port)
    {
        this.socket = socket;
        this.group = group;
        this.port = port;
    }
      
    @Override
    public void run()
    {
        while(true){

            String message = SocketFunctionsApplication.recvData(group,port,socket);
            int numberOfLines = message.split("\n").length;
            if(numberOfLines < 5){
                String hostIp = message.split("\n")[0].split(":")[1];
                String messageType = message.split("\n")[1].split(":")[1];
                String messageSender = message.split("\n")[2].split(":")[1];
                if (!messageSender.equals("sensor")) {

                    if (messageType.equals("msearch")) {
                        System.out.println("msearch received");
                        SocketFunctionsApplication.sendData(Application.messageNotify, group, port, socket);

                    } else if (messageType.equals("notify") && messageSender.equals("controller")) {
                        Application.MQTT_BROKER = "tcp://" + hostIp + ":5000";
                        // Sensor.MQTT_BROKER = "tcp://localhost:5000";
                        if (!Application.MQTT_BROKER.equals("") && !Application.MqttInitialized) {
                            Application.MqttInitialized = true;
                            MqttHelperApplication.initMqtt();
                            MqttHelperApplication.subscribeToController();
                        }
                    }
                }
            }
        } 
    }



}
class SocketFunctionsApplication{
   
    
    public static void sendData(String message, InetAddress group, int port, MulticastSocket socket){
        try{
            byte[] buffer = message.getBytes();
            DatagramPacket datagram = new DatagramPacket(buffer,buffer.length,group,port);
            socket.send(datagram);
        }
        catch(IOException ie)
        {
            System.out.println("Error reading/writing from/to socket");
            ie.printStackTrace();
        
        }
    }
    public static String recvData(InetAddress group, int port, MulticastSocket socket){
            String message = "";
            byte[] buffer = new byte[ReadThreadApplication.MAX_LEN];
            DatagramPacket datagram = new
            DatagramPacket(buffer,buffer.length,group,port);
            try
            {
                socket.receive(datagram);
                message = new
                String(buffer,0,datagram.getLength(),"UTF-8");
            }
            catch(IOException e)
            {
                System.out.println("Socket closed!");
            }
            return message;
        
    }
    public static String getIndexDevice(String list[],String name){
        for(int i=0;i<list.length;i++){
            if(list[i].equals(name)){
                return String.valueOf(i);
            }
        }
        return "0";
    }
    public static String getIpAddress(){
        final Pattern pattern = Pattern.compile("192", Pattern.CASE_INSENSITIVE);
        try{ 
            Enumeration en = NetworkInterface.getNetworkInterfaces(); 
            while (en.hasMoreElements()) { 
                NetworkInterface ni = (NetworkInterface) en.nextElement(); 
                Enumeration ee = ni.getInetAddresses(); 
                while (ee.hasMoreElements()) { 
                    InetAddress ia = (InetAddress) ee.nextElement(); 
                    String ip = ia.getHostAddress(); 
                    Matcher matcher = pattern.matcher(ip);
                    boolean matched = matcher.find();
                    if(matched)
                      return ip;
                } 
            } 
        } 
        catch(Exception e){ 
        }
        return "";
  }
}
class MqttHelperApplication{

    public static void initMqtt(){
        try{
            Application.client = new MqttClient(Application.MQTT_BROKER, MqttClient.generateClientId(),null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            Application.client.connect(options);
      }
        catch (MqttException e) {
            System.out.println("Error publishing message");
            e.printStackTrace();
        }
    }
    public static void sendMqttData(){
        try{
            String messageTemp = ApplicationGUI.getUserTemp();
            String messageHumid = ApplicationGUI.getUserHumidity();
            String messageLight = ApplicationGUI.getUserLight();
            MqttMessage mqttMessageTemp = new MqttMessage(messageTemp.getBytes());
            MqttMessage mqttMessageHumid = new MqttMessage(messageHumid.getBytes());
            MqttMessage mqttMessageLight = new MqttMessage(messageLight.getBytes());
            Application.client.publish("plastenik/biljka/aplikacija/Temperature", mqttMessageTemp); 
            Application.client.publish("plastenik/biljka/aplikacija/Humidity", mqttMessageHumid); 
            Application.client.publish("plastenik/biljka/aplikacija/Light", mqttMessageLight); 
        }
        catch (MqttException e) {
            System.out.println("Error publishing message");
            e.printStackTrace();
        }
    }
    public static void subscribeToController(){
        try{
            
            Application.client.subscribe("plastenik/biljka/kontroler/Temperature",(topic,controllerMessage) -> {
                String mqttMessage = new String(controllerMessage.getPayload());
                //System.out.println("Value received from Controller for Temp : " + mqttMessage);
                ApplicationGUI.printTempOnFrame(mqttMessage);
            }); 
            Application.client.subscribe("plastenik/biljka/kontroler/Humidity",(topic,controllerMessage) -> {
                String mqttMessage = new String(controllerMessage.getPayload());
                //System.out.println("Value received from Controller for Humidity : " + mqttMessage);
                ApplicationGUI.printHumidityOnFrame(mqttMessage);
            }); 
            Application.client.subscribe("plastenik/biljka/kontroler/Light",(topic,controllerMessage) -> {
                String mqttMessage = new String(controllerMessage.getPayload());
                //System.out.println("Value received from Controller for Light : " + mqttMessage);
                ApplicationGUI.printLightOnFrame(mqttMessage);
            }); 
        }
        catch(MqttException e){
            e.printStackTrace();
        }
    }
}
class ApplicationGUI implements ItemListener, ChangeListener{
    public static JFrame frame;
    public static JPanel panel;
    public static JLabel temperatureLabel;
    public static JLabel humidityLabel;
    public static JLabel lightLabel;
    public static JLabel setTempLabel;
    public static JLabel setHumidityLabel;
    public static JLabel setLightLabel;
    public static JSlider tempSlider;
    public static JSlider humiditySlider;
    public static JToggleButton lightToggle;
    public static JSlider lightSlider;
    public static String tempValue = "-1";
    public static String humidValue = "-1";
    public static String lightValue = "-1";

    public static void drawNewFrame(String name){
        frame = new JFrame(name);
        panel = new JPanel();
        temperatureLabel = new JLabel();
        humidityLabel = new JLabel();
        lightLabel = new JLabel();
        setTempLabel = new JLabel();
        setHumidityLabel = new JLabel();
        setLightLabel = new JLabel();

        tempSlider = DrawSlider(15, 40, 25, 5);
        humiditySlider = DrawSlider(0, 100, 50, 10);
        lightSlider = DrawSlider(0, 100, 50, 10);
        
        panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        panel.add(temperatureLabel);
        panel.add(humidityLabel);
        panel.add(lightLabel);
        panel.add(setTempLabel);
        panel.add(tempSlider);
        panel.add(setHumidityLabel);
        panel.add(humiditySlider);
        panel.add(setLightLabel);
        panel.add(lightSlider);
        //setJToggleButton();
        setAction();
        frame.add(panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400,400); 
        frame.setVisible(true);

    }

    @Override
    public void itemStateChanged(ItemEvent e){
        if(lightToggle.isSelected()){
            lightToggle.setText("ON");
            lightValue = "1";
        }else{
            lightToggle.setText("OFF");
            lightValue = "0";
        }
    }

    @Override
    public void stateChanged(ChangeEvent e){
        if(e.getSource() == humiditySlider){
            humidValue = String.valueOf(humiditySlider.getValue());
        }else if(e.getSource() == tempSlider){
            tempValue = String.valueOf(tempSlider.getValue());
        }else{
            lightValue = String.valueOf(lightSlider.getValue());
        }
    }

    private static void setJToggleButton(){
        lightToggle = new JToggleButton("OFF");
        lightToggle.setBounds(150, 150, 150, 50);
        panel.add(lightToggle);
    }

    private static void setAction(){
        //lightToggle.addItemListener(new ApplicationGUI());
        tempSlider.addChangeListener(new ApplicationGUI());
        humiditySlider.addChangeListener(new ApplicationGUI());
        lightSlider.addChangeListener(new ApplicationGUI());

    }

    private static JSlider DrawSlider(int min, int max, int value, int majorTick){
        JSlider slider = new JSlider(JSlider.HORIZONTAL, min, max, value);
        slider.setMinorTickSpacing(1);  
        slider.setMajorTickSpacing(majorTick);         
        slider.setPaintTicks(true);         
        slider.setPaintLabels(true);        
        
        return slider;
    }

    public static void printTempOnFrame(String value){
        temperatureLabel.setText("Current temp value: " + value);
        setTempLabel.setText("Set the temperature(C): ");
    }
    public static void printHumidityOnFrame(String value){
        humidityLabel.setText("Current humidity value: " + value);
        setHumidityLabel.setText("Set the humidity(%): ");
    }
    public static void printLightOnFrame(String value){
        lightLabel.setText("Current light value: " + value);
        setLightLabel.setText("Set the light level: ");
    }

    public static String getUserTemp(){
        System.out.println("User set this temp: " + tempValue);
        return tempValue;
    }
    public static String getUserHumidity(){
        System.out.println("User set this humidity: " + humidValue);
        return humidValue;
    }
    public static String getUserLight(){
        System.out.println("User set this light: " + lightValue);
        return lightValue;
    }

}
