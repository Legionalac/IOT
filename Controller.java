import org.eclipse.paho.client.mqttv3.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.lang.Integer;
import java.util.ArrayList;
import java.io.FileWriter;  
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
public class Controller
{
    public static MqttClient client;
    public static String MQTT_BROKER = "";
    static String messageMSearch;
    static String messageNotify;
    static volatile boolean finished = false;
    public static  ArrayList<Device> deviceList;
    public static String adrress;
    public static void main(String[] args) throws InterruptedException
    {
    
        try
        {
            adrress = SocketFunctionsServer.getIpAddress();
            MQTT_BROKER = "tcp://" + adrress + ":4000";
            messageMSearch = 
            "HOST:"+ adrress +"\n"+
            "ssdp:msearch\n"+ 
            "type:controller";
            messageNotify = 
            "HOST:"+ adrress +"\n"+
            "ssdp:notify\n"+
            "type:controller";
            InetAddress group = InetAddress.getByName("239.255.255.250");
            int port = 1900;
            MulticastSocket socket = new MulticastSocket(port);
            deviceList = new ArrayList<Device>();
            socket.setTimeToLive(1);
            //socket.setLoopbackMode(true);
            socket.joinGroup(group);
            Thread t = new Thread(new
            ReadThreadServer(socket,group,port));
            t.start(); 
            SocketFunctionsServer.sendData(messageMSearch,group,port,socket);
            MqttHelperServer.initMqtt();
            while(true)
            {
                 
                Thread.sleep(2000);
                System.out.println("#####################");  
                Device.printAllDevices();
                SocketFunctionsServer.sendData(messageNotify,group,port,socket);
                MqttHelperServer.subscribeToSensors();
                MqttHelperServer.publishToActuators();
                System.out.println("Number of devices:" + deviceList.size());
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
}
class ReadThreadServer implements Runnable
{
    private MulticastSocket socket;
    private InetAddress group;
    private int port;
    public static final int MAX_LEN = 1000;
   
    ReadThreadServer(MulticastSocket socket,InetAddress group,int port)
    {
        this.socket = socket;
        this.group = group;
        this.port = port;
    }
      
    @Override
    public void run()
    {
        
        while(true){
            String message = SocketFunctionsServer.recvData(group,port,socket);
            String hostIp = message.split("\n")[0].split(":")[1];
            String messageType = message.split("\n")[1].split(":")[1];
            String deviceType = message.split("\n")[2].split(":")[1];

             if(!deviceType.equals("controller")){
                 if(messageType.equals("notify")){
                    Device device = new Device(message);
                    Device.addDevice(device);
                 }
                 else if(messageType.equals("msearch")){
                   SocketFunctionsServer.sendData(Controller.messageNotify,group,port,socket);
                 }
             }

        }
    }
}



class SocketFunctionsServer{
   
    
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
            byte[] buffer = new byte[ReadThreadServer.MAX_LEN];
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

class Device{
    public static final String deviceCategoryList[] = new String[]{"Sensor","Actuator","Controller","Aplication"};
    public static final String deviceTypeSensor[] = new String[]{"Temperature","Humidity","Light"};
    public static final String deviceTypeActuator[] = new String[]{"Fan","Pump","Light"};
    private String ipAddress;
    private String deviceCategory;
    private String deviceType;
    private int deviceId;
    private long timeAlive;
    private int value;
    private boolean subscribed;
    public Device(String message){
        String lines[] = message.split("\n");
        this.ipAddress = lines[0].split(":")[1];
        String header[] = lines[3].split(":");
        String data[] = header[1].split("\\|");
        
        this.deviceCategory = deviceCategoryList[Integer.parseInt(data[0])];
        if(this.deviceCategory.equals("Sensor") ){
            this.deviceType = deviceTypeSensor[Integer.parseInt(data[1])];
            this.deviceId = Integer.parseInt(data[2]);

        }
        else if(this.deviceCategory.equals("Actuator"))
        {
            this.deviceType = deviceTypeActuator[Integer.parseInt(data[1])];
            this.deviceId = Integer.parseInt(data[2]);
        }
        else{
            this.deviceType = "Controller/Aplication";
            this.deviceId=0;
        }
        timeAlive = System.currentTimeMillis();
        subscribed = false;
        value = -1;
    }
    public void printDevice(){
        System.out.println(ipAddress);
        System.out.println(deviceCategory);
        System.out.println(deviceType);
        System.out.println(deviceId);
    }
    public String getCategory(){
        return this.deviceCategory;
    }
    public String getType(){
        return this.deviceType;
    }
    public String getIp(){
        return this.ipAddress;
    }
    public int getId(){
        return this.deviceId;
    }
    public long getTime(){
        return this.timeAlive;
    }
    public void setTime(long time){
        this.timeAlive = time;
    }
    public void setValue(int value){
        this.value = value;
    }
    public int getValue(){
        return this.value;
    }
    public void setSubscribed(boolean subscribed){
        this.subscribed = subscribed;
    }
    public boolean getSubscribed(){
        return this.subscribed;
    }
    @Override
    public boolean equals(Object o) {
        Device compDevice = (Device) o;
        boolean deviceCategorySame = this.deviceCategory.equals(compDevice.getCategory());
        boolean deviceTypeSame= this.deviceType.equals(compDevice.getType());
        boolean deviceIDSame = this.deviceId == compDevice.getId();
        boolean deviceIpSame = this.ipAddress.equals(compDevice.getIp());

        if(deviceCategorySame && deviceTypeSame && deviceIDSame && deviceIpSame)
            return true;
        else
            return false;
    }
    public static void addDevice(Device device){
        boolean inList=false;
        for(int i=0;i<Controller.deviceList.size();i++){
            if(Controller.deviceList.get(i).equals(device)){
                inList=true;
                break;
            }
        }
        if(!inList){
            Controller.deviceList.add(device);
         
        }
        else{
            for(int i=0;i<Controller.deviceList.size();i++){
                if(Controller.deviceList.get(i).equals(device)){
                     Controller.deviceList.get(i).setTime(System.currentTimeMillis());
                     break;
                }
            }
        }
    }
    public static void printAllDevices(){
        for(int i=0;i<Controller.deviceList.size();i++){
            if((System.currentTimeMillis() - Controller.deviceList.get(i).getTime()) < 5000){
                if(Controller.deviceList.get(i).deviceCategory.equals("Sensor")){
                    System.out.println("Value of " + Controller.deviceList.get(i).deviceType + " Sensor : " + Controller.deviceList.get(i).getValue());
                }
            }
            else{
                Controller.deviceList.remove(i);
                i--;
            }
            
        }
        // String output ="";
        // for(int i=0;i<Controller.deviceList.size();i++){
        //     output = output + Controller.deviceList.get(i).getCategory() + " " + Controller.deviceList.get(i).getType() + "\n";
        // }
    }

}
class MqttHelperServer{

    public static void initMqtt(){
        try{
            Controller.client = new MqttClient(Controller.MQTT_BROKER, MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            Controller.client.connect(options);
      }
        catch (MqttException e) {
            System.out.println("Error publishing message");
            e.printStackTrace();
        }
    }
    public static void subscribeToSensors(){
        try{
            for(Device i : Controller.deviceList){
                if(!i.getSubscribed() && i.getCategory().equals("Sensor")){
                    i.setSubscribed(true);
                    Controller.client.subscribe("plastenik/biljka/" + i.getType() + "/" + i.getId(),(topic,message)->{
                    String mqttMessage = new String(message.getPayload());
                    i.setValue(Integer.valueOf(mqttMessage));
                    });
                }
            }
        }
        catch (MqttException e) {
            System.out.println("Error publishing message");
            e.printStackTrace();
        }
    }
    public static void publishToActuators(){
        List<Integer> tempValues = new ArrayList<Integer>();
        List<Integer> humidityValues= new ArrayList<Integer>();
        List<Integer> lightValues= new ArrayList<Integer>();
        int tempAverage = 0;
        int humidityAverage = 0;
        int lightAverage = 0;
        for(Device i: Controller.deviceList){
            if(i.getCategory().equals("Sensor")){
                if(i.getType().equals("Temperature")){
                    tempValues.add(i.getValue());
                }
                else if(i.getType().equals("Humidity")){
                    humidityValues.add(i.getValue());
                }
                else{
                    lightValues.add(i.getValue());
                }
            }
        }
        for(int x:tempValues){
            tempAverage += x;
        }
        if(tempValues.size() > 0){
            tempAverage /= tempValues.size();
        }
        for(int x:humidityValues){
            humidityAverage += x;
        }
        if(humidityValues.size()>0){
            humidityAverage /= humidityValues.size();
        }
        for(int x:lightValues){
            lightAverage += x;
        }
        if(lightValues.size() > 0){
            lightAverage /= lightValues.size();
        }
        try{
            for(Device i : Controller.deviceList){
                if(i.getCategory().equals("Actuator")){
                    if(i.getType().equals("Fan") && tempAverage > 0){
                        MqttMessage mqttMessage = new MqttMessage(String.valueOf(tempAverage).getBytes());
                        Controller.client.publish("plastenik/biljka/" + i.getType(), mqttMessage);
                    }
                    else if(i.getType().equals("Pump") && humidityAverage > 0){
                        MqttMessage mqttMessage = new MqttMessage(String.valueOf(humidityAverage).getBytes());
                        Controller.client.publish("plastenik/biljka/" + i.getType(), mqttMessage);
                    }
                    else if(lightAverage > 0){
                        MqttMessage mqttMessage = new MqttMessage(String.valueOf(lightAverage).getBytes());
                        Controller.client.publish("plastenik/biljka/" + i.getType(), mqttMessage);
                    }
                }
            }
        }
        catch (MqttException e) {
            System.out.println("Error publishing message");
            e.printStackTrace();
        }

    }
}
