package org.bstats.bukkit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import javax.net.ssl.HttpsURLConnection;
import javax.print.attribute.standard.MediaSize;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

/**
 * bStats collects some data for plugin authors.
 * <p>
 * Check out https://bStats.org/ to learn more about bStats!
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class MetricsLite {


    public static final String NAME = "Disposable Software";
    public static final String VERSION = "1.0";
    // The version of this bStats class
    public static final int B_STATS_VERSION = 1;

    // The url to which the data is sent
    private static final String URL = "https://bStats.org/submitData/bukkit";

    // Is bStats enabled on this server?
    private boolean enabled;

    // Should failed requests be logged?
    private static boolean logFailedRequests;

    // Should the sent data be logged?
    private static boolean logSentData;

    // Should the response text be logged?
    private static boolean logResponseStatusText;

    // The uuid of the server
    private static String serverUUID;

    // The plugin



    public MetricsLite() {


        // Get the config file
        File bStatsFolder = new File( System.getProperty("user.dir"),"Settings");
        File configFile = new File(bStatsFolder, "config.yml");
        if(! bStatsFolder.exists())
            bStatsFolder.mkdir();
      try {
          if (!configFile.exists()) {
              configFile.createNewFile();
              writeUUID(configFile);
          }
      }catch (IOException ex){
          ex.printStackTrace();
      }
        // Load the data
        serverUUID = getUUID(configFile);

        logFailedRequests = false;
        enabled = true;
        logSentData = false;
        logResponseStatusText = false;
        if (enabled) {
            System.out.println("사용 통계를 보내는 중입니다. 잠시만 기다려주세요.");
                startSubmitting();
        }

    }
    public void writeUUID(File f){
        try{
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
            writer.write("UUID:"+UUID.randomUUID().toString());
            writer.flush();
            writer.close();
        }catch (IOException ex){
            ex.printStackTrace();
        }
    }
    public String getUUID(File f){
       try{
           BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
           String line;
           while ((line = reader.readLine()) != null){
               if(line.trim().startsWith("UUID"))
               return line.split(":")[1];
               else
                   return null;
           }
       }catch (IOException ex){

       }
       return null;
    }

    /**
     * Checks if bStats is enabled.
     *
     * @return Whether bStats is enabled or not.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Starts the Scheduler which submits our data every 30 minutes.
     */
    private void startSubmitting() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                submitData();
            }
        });
        t.start();
    }

    /**
     * Gets the plugin specific data.
     * This method is called using Reflection.
     *
     * @return The plugin specific data.
     */
    public JsonObject getPluginData() {
        JsonObject data = new JsonObject();

        String pluginName = NAME;
        String pluginVersion = VERSION;

        data.addProperty("pluginName", pluginName); // Append the name of the plugin
        data.addProperty("pluginVersion", pluginVersion); // Append the version of the plugin
        data.add("customCharts", new JsonArray());

        return data;
    }

    /**
     * Gets the server specific data.
     *
     * @return The server specific data.
     */
    private JsonObject getServerData() {
        // Minecraft specific data
        int playerAmount;

        String bukkitVersion = "NONE";
        String bukkitName = "NONE";

        // OS/Java specific data
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String osVersion = System.getProperty("os.version");
        int coreCount = Runtime.getRuntime().availableProcessors();

        JsonObject data = new JsonObject();

        data.addProperty("serverUUID", serverUUID);

        data.addProperty("playerAmount", 0);
        data.addProperty("onlineMode", false);
        data.addProperty("bukkitVersion", bukkitVersion);
        data.addProperty("bukkitName", bukkitName);

        data.addProperty("javaVersion", javaVersion);
        data.addProperty("osName", osName);
        data.addProperty("osArch", osArch);
        data.addProperty("osVersion", osVersion);
        data.addProperty("coreCount", coreCount);

        return data;
    }

    /**
     * Collects the data and sends it afterwards.
     */
    private void submitData() {
        final JsonObject data = getServerData();
        JsonObject temp = getPluginData();
        JsonArray pluginData = new JsonArray();
        for(String key : temp.keySet()){
            pluginData.add(temp.get(key));
        }

        // Search for all other bStats Metrics classes to get their plugin data
        data.add("plugins", pluginData);

        // Create a new thread for the connection to the bStats server
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Send the data
                    sendData( data);
                } catch (Exception e) {
                    // Something went wrong! :(
                    if (logFailedRequests) {
                        e.printStackTrace();
                        System.out.println( "Could not submit plugin stats of " + NAME);
                    }
                }
            }
        }).start();
    }

    /**
     * Sends the data to the bStats server.
     *
     * @param
     * @param data The data to send.
     * @throws Exception If the request failed.
     */
    private static void sendData( JsonObject data) throws Exception {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null!");
        }


        if (logSentData) {
           System.out.println("Sending data to bStats: " + data.toString());
        }
        HttpsURLConnection connection = (HttpsURLConnection) new URL(URL).openConnection();

        // Compress the data to save bandwidth
        byte[] compressedData = compress(data.toString());

        // Add headers
        connection.setRequestMethod("POST");
        connection.addRequestProperty("Accept", "application/json");
        connection.addRequestProperty("Connection", "close");
        connection.addRequestProperty("Content-Encoding", "gzip"); // We gzip our request
        connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
        connection.setRequestProperty("Content-Type", "application/json"); // We send our data in JSON format
        connection.setRequestProperty("User-Agent", "MC-Server/" + B_STATS_VERSION);

        // Send data
        connection.setDoOutput(true);
        DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
        outputStream.write(compressedData);
        outputStream.flush();
        outputStream.close();

        InputStream inputStream = connection.getInputStream();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            builder.append(line);
        }
        bufferedReader.close();
        if (logResponseStatusText) {
            System.out.println("Sent data to bStats and received response: " + builder.toString());
        }
    }

    /**
     * Gzips the given String.
     *
     * @param str The string to gzip.
     * @return The gzipped String.
     * @throws IOException If the compression failed.
     */
    private static byte[] compress(final String str) throws IOException {
        if (str == null) {
            return null;
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(outputStream);
        gzip.write(str.getBytes(StandardCharsets.UTF_8));
        gzip.close();
        return outputStream.toByteArray();
    }

}
