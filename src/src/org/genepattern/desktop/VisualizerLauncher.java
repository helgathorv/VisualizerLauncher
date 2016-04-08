package org.genepattern.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;


/**
 * Created by nazaire on 4/3/16.
 */
public class VisualizerLauncher
{
    private static String REST_API_JOB_PATH  = "/rest/v1/jobs";
    private static String REST_API_TASK_PATH = "/rest/v1/tasks";

    private JTextField serverField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField jobNumberField;

    private String gpServer;
    private JobInfo jobInfo;
    private String basicAuthString;

    VisualizerLauncher(String jobNumber)
    {
        this.jobInfo = new JobInfo();
        jobInfo.setJobNumber(jobNumber);
    }

    protected Thread copyStream(final InputStream is, final PrintStream out) {
        // create thread to read from the a process output or error stream
        Thread copyThread = new Thread(new Runnable() {
            public void run() {
                BufferedReader in = new BufferedReader(new InputStreamReader(is));
                String line;
                try {
                    while ((line = in.readLine()) != null) {
                        out.println(line);
                    }
                } catch (IOException ioe) {
                    System.err.println("Error reading from process stream.");
                }
            }
        });
        copyThread.setDaemon(true);
        copyThread.start();
        return copyThread;
    }

    private void login()
    {
        JPanel panel = new JPanel(new GridLayout(4, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        serverField = new JTextField();
        TextPrompt serverFieldPrompt = new TextPrompt("http://localhost:8080/gp", serverField);
        serverFieldPrompt.changeAlpha(0.4f);
        final JLabel serverLabel = new JLabel("server: ");
        serverLabel.setLabelFor(serverField);
        panel.add(serverLabel);
        panel.add(serverField);

        usernameField = new JTextField();
        final JLabel usernameLabel = new JLabel("username: ");
        usernameLabel.setLabelFor(usernameField);
        panel.add(usernameLabel);
        panel.add(usernameField);

        passwordField = new JPasswordField();
        final JLabel passwordLabel = new JLabel("password: ");
        passwordLabel.setLabelFor(passwordField);
        panel.add(passwordLabel);
        panel.add(passwordField);

        jobNumberField = new JTextField("");
        JLabel jobNumberLabel = new JLabel("job number: ");
        jobNumberLabel.setLabelFor(jobNumberField);
        panel.add(jobNumberLabel);
        panel.add(jobNumberField);

        JButton submit = new JButton("Submit");
        submit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String serverName = serverField.getText();
                String userName = usernameField.getText();
                char[] password = passwordField.getPassword();
                String jobNumber = jobNumberField.getText();

                if(serverName == null || serverName.length() == 0)
                {
                    displayErrorMsg("Please enter a server");
                    return;
                }

                if (userName != null && userName.length() > 0) {
                    String authorizationString = userName + ":";

                    if (password != null && password.length != 0) {
                        authorizationString += String.valueOf(password);
                    }
                    byte[] authEncBytes = Base64.encodeBase64(authorizationString.getBytes());
                    basicAuthString = new String(authEncBytes);
                    basicAuthString = "Basic " + basicAuthString;
                }
                else
                {
                    displayErrorMsg("Please enter a username");
                    return;
                }

                if(jobNumber == null || jobNumber.length() == 0)
                {
                    displayErrorMsg("Please enter a job number");
                    return;
                }

                jobInfo = new JobInfo();
                jobInfo.setJobNumber(jobNumber);
                gpServer = serverName;
                JDialog dialog = showProgress();
                exec();
                dialog.dispose();
            }
        });

        JPanel submitPanel = new JPanel();
        submitPanel.add(submit);

        JFrame frame = new JFrame("GenePattern Java Visualizer Launcher");
        frame.setLayout(new BorderLayout());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setPreferredSize(new Dimension(520, 250));
        frame.add(panel, BorderLayout.CENTER);
        frame.add(submitPanel, BorderLayout.SOUTH);
        frame.pack();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation((screenSize.width - frame.getWidth()) / 2, (screenSize.height - frame.getHeight()) / 2);
        frame.setVisible(true);
    }

    public void run()
    {
        login();
    }

    private JDialog showProgress() {
        JDialog dialog = new JDialog();
        JLabel label = new JLabel("Launching visualizer...");
        JProgressBar progressBar = new JProgressBar();
        dialog.setTitle("GenePattern");
        progressBar.setIndeterminate(true);
        label.setFont(new Font("Dialog", Font.BOLD, 14));
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dialog.getContentPane().add(label);
        dialog.getContentPane().add(progressBar, BorderLayout.SOUTH);
        dialog.setResizable(false);
        dialog.pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation((screenSize.width - dialog.getWidth()) / 2, (screenSize.height - dialog.getHeight()) / 2);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
        return dialog;
    }

    private void downloadSupportFiles(GPTask task)
    {
        try{

            String[] supportFileURLs = task.getSupportFileUrls();

            for(String supportFileURL : supportFileURLs)
            {
                int slashIndex = supportFileURL.lastIndexOf('=');
                String filenameWithExtension =  supportFileURL.substring(slashIndex + 1);

                Util.downloadFile(new URL(supportFileURL), new File("."), filenameWithExtension, basicAuthString);
            }
        }
        catch(MalformedURLException m)
        {
            m.printStackTrace();
        }
        catch(IOException io)
        {
            io.printStackTrace();
        }
    }

    private String doGetRequest(String URL) throws IOException
    {
        String responseBody = "";
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();

        try {
            HttpGet httpget = new HttpGet(URL);
            httpget.setHeader("Authorization", basicAuthString);

            System.out.println("Executing request " + httpget.getRequestLine());

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

                @Override
                public String handleResponse(
                        final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }

            };
            responseBody = httpClient.execute(httpget, responseHandler);
            System.out.println("----------------------------------------");
            System.out.println(responseBody);

        } finally {
            httpClient.close();
        }

        return responseBody;
    }

    private void retrievejobDetails() throws Exception
    {
        if(jobInfo == null || jobInfo.getJobNumber() == null)
        {
            throw new IllegalArgumentException("No valid job found");
        }
        String getJobAPICall = gpServer + VisualizerLauncher.REST_API_JOB_PATH + "/" + jobInfo.getJobNumber();
        String response = doGetRequest(getJobAPICall);

        JSONTokener tokener = new JSONTokener(response);
        JSONObject root = new JSONObject(tokener);

        String taskLsid = root.getString("taskLsid");

        if(taskLsid == null || taskLsid.length() == 0)
        {
            throw new Exception("Task lsid was not found");
        }

        GPTask gpTask = new GPTask();
        gpTask.setLsid(taskLsid);
        jobInfo.setGpTask(gpTask);
    }

    /*private void retrievejobDetails() throws ClientProtocolException, IOException, JSONException, Exception
    {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(null, -1),
                new UsernamePasswordCredentials("username", null));
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        //HttpClients.custom()
         //       .setDefaultCredentialsProvider(credsProvider)
         //       .build();

        try {
            if(jobInfo == null || jobInfo.getJobNumber() == null)
            {
                throw new IllegalArgumentException("No valid job found");
            }
            String getJobAPICall = gpServer + DesktopLauncher.REST_API_JOB_PATH + "/" + jobInfo.getJobNumber();
            HttpGet httpget = new HttpGet(getJobAPICall);
            httpget.setHeader("Authorization", basicAuthString);

            System.out.println("Executing request " + httpget.getRequestLine());

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

                @Override
                public String handleResponse(
                        final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }

            };
            String responseBody = httpClient.execute(httpget, responseHandler);
            System.out.println("----------------------------------------");
            System.out.println(responseBody);

            JSONTokener tokener = new JSONTokener(responseBody);
            JSONObject root = new JSONObject(tokener);

            String taskLsid = root.getString("taskLsid");

            if(taskLsid == null || taskLsid.length() == 0)
            {
                throw new Exception("Task lsid was not found");
            }

            GPTask gpTask = new GPTask();
            gpTask.setLsid(taskLsid);
            jobInfo.setGpTask(gpTask);

        } finally {
            httpClient.close();
        }
    }*/

    /** Converts a string into something you can safely insert into a URL. */
    @SuppressWarnings("deprecation")
    public static String encodeURIcomponent(String str) {
        String encoded = str;
        try {
            encoded = URLEncoder.encode(str, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            encoded = URLEncoder.encode(str);
        }

        //replace all '+' with '%20'
        encoded = encoded.replace("+", "%20");
        return encoded;

    }

    private void retrieveTaskDetails() throws Exception
    {
        if(jobInfo == null || jobInfo.getGpTask() == null || jobInfo.getGpTask().getLsid() == null
                || jobInfo.getGpTask().getLsid().length() == 0)
        {
            throw new IllegalArgumentException("No valid task found");
        }

        String getTaskRESTCall = gpServer + REST_API_TASK_PATH  + "/" + jobInfo.getGpTask().getLsid() + "?includeSupportFiles=true";
        String response = doGetRequest(getTaskRESTCall);

        JSONTokener tokener = new JSONTokener(response);
        JSONObject root = new JSONObject(tokener);

        String commandLine = root.getString("command_line");

        if(commandLine == null || commandLine.length() == 0)
        {
            throw new Exception("No command line found for task with LSID: " + jobInfo.getGpTask().getLsid());
        }

        JSONArray supportFileURIs = root.getJSONArray("supportFiles");

        if(supportFileURIs == null || supportFileURIs.length() == 0)
        {
            throw new Exception("No support files found for task with LSID: " + jobInfo.getGpTask().getLsid());
        }

        String[] supportFileURLs = new String[supportFileURIs.length()];
        for(int i=0;i<supportFileURIs.length();i++)
        {
            String supportFileURI = supportFileURIs.getString(i);
            String supportFileURL = gpServer +  supportFileURI;
            supportFileURLs[i] = supportFileURL;
        }

        GPTask gpTask = jobInfo.getGpTask();
        gpTask.setSupportFileUrls(supportFileURLs);
        gpTask.setCommandLine(commandLine);
    }

    private void runVisualizer() throws  Exception
    {
        if(jobInfo == null || jobInfo.getGpTask() == null || jobInfo.getGpTask().getCommandLine() == null
                || jobInfo.getGpTask().getCommandLine().length() == 0)
        {
            throw new IllegalArgumentException("No command line found");
        }

        String cmdLine = jobInfo.getGpTask().getCommandLine();

        //substitute <libdir> on the commandline with empty string since
        //all support files are in the current directory
        cmdLine = cmdLine.replace("<libdir>", new File("").getAbsolutePath() + "/");

        //substitute the <java> on the command
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        //add .exe extension if this is Windows
        java += (System.getProperty("os.name").startsWith("Windows") ? ".exe" : "");
        cmdLine = cmdLine.replace("<java>", java);

        //get the substituted commandline from the serverField
        //String getTaskRESTCall = gpServer + REST_API_TASK_PATH  + "/" + jobInfo.getGpTask().getLsid() + "/substitute?commandline=" + encodeURIcomponent(cmdLine);
        String getTaskRESTCall = gpServer + REST_API_JOB_PATH  + "/" + jobInfo.getJobNumber() + "/cmdLine?commandline=" + encodeURIcomponent(cmdLine);

        String response = doGetRequest(getTaskRESTCall);

        JSONTokener tokener = new JSONTokener(response);
        JSONObject root = new JSONObject(tokener);

        JSONArray cmdLineArr = root.getJSONArray("commandline");
        System.out.println("commandline: " + cmdLineArr);

        Map<String, String> inputURLMap = jobInfo.getInputURLToFilePathMap();
        String[] cmdLineList = new String[cmdLineArr.length()];
        for(int i=0;i< cmdLineArr.length(); i++)
        {
            String argValue = cmdLineArr.getString(i);
            if(inputURLMap.containsKey(argValue))
            {
                argValue = inputURLMap.get(argValue);
            }

            cmdLineList[i] = argValue;
        }

        /*for(final Map.Entry<String, String> entry : jobInfo.getInputURLToFilePathMap().entrySet())
        {
            commandLine = commandLine.replace(entry.getKey(), new File("").getAbsolutePath() + "/" + entry.getValue());
        }*/

        //JOptionPane.showMessageDialog(panel, "An error occurred while downloading the module support files: "+e.getLocalizedMessage());

        jobInfo.setCommandLine(cmdLineList);

        try {
            runCommand(jobInfo.getCommandLine());
        }
        catch (IOException e) {
            e.printStackTrace();

            displayErrorMsg("An error occurred while running the visualizer: "
                    +e.getLocalizedMessage());
            return;
        }
    }

    private void downloadInputFiles() throws Exception
    {
        if(jobInfo == null || jobInfo.getGpTask() == null || jobInfo.getGpTask().getCommandLine() == null
                || jobInfo.getGpTask().getCommandLine().length() == 0)
        {
            throw new IllegalArgumentException("No command line found");
        }

        //get the URLs to the input files for the job
        String getJobInputFilesRESTCall = gpServer + REST_API_JOB_PATH  + "/" + jobInfo.getJobNumber() + "/inputfiles";

        String response = doGetRequest(getJobInputFilesRESTCall);

        JSONTokener tokener = new JSONTokener(response);
        JSONObject root = new JSONObject(tokener);

        JSONArray inputFiles = root.getJSONArray("inputFiles");

        Map<String, String> inputURLToFilePathMap = new HashMap<String, String>();

        for(int i=0;i<inputFiles.length();i++)
        {
            String inputFileURL = inputFiles.getString(i);
            int slashIndex = inputFileURL.lastIndexOf('/');
            String filenameWithExtension =  inputFileURL.substring(slashIndex + 1);

            URL fileURL = new URL(inputFileURL);
            inputURLToFilePathMap.put(fileURL.toString(), filenameWithExtension);
            Util.downloadFile(fileURL, new File("."), filenameWithExtension, basicAuthString);
        }

        jobInfo.setInputURLToFilePathMap(inputURLToFilePathMap);
    }

    private void exec()
    {
        try
        {
            //retrieve the job details in order to get the task details
            retrievejobDetails();

            //retrieve the visualizer module details
            retrieveTaskDetails();

            //download the support files
            downloadSupportFiles(jobInfo.getGpTask());

            //download the input files
            downloadInputFiles();

            //run the visualizer
            runVisualizer();
        }
        catch(Exception e)
        {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "An error occurred while running the visualizer: " + e.getLocalizedMessage());
        }
    }


    public void runCommand(final String[] command) throws IOException
    {
        Thread t = new Thread() {
            public void run() {

                Process process = null;
                try {
                    ProcessBuilder probuilder = new ProcessBuilder(command);
                    //You can set up your work directory
                    //probuilder.directory(new File(currentdirectory.getAbsolutePath()));
                    //probuilder.directory(new File("c:\\xyzwsdemo"));

                    process = probuilder.start();
                }
                catch (IOException e1) {
                    e1.printStackTrace();

                    String msg = "An error occurred while running the visualizer: " + e1.getLocalizedMessage();

                    displayErrorMsg(msg);

                    return;
                }

                //Read out dir output
                InputStream is = process.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);


                // drain the output and error streams
                copyStream(process.getInputStream(), System.out);
                copyStream(process.getErrorStream(), System.err);

                //Wait to get exit value
                try {
                    int exitValue = process.waitFor();
                    System.out.println("\n\nExit Value is " + exitValue);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        };
        t.start();
    }

    private void displayErrorMsg(String msg) {
        JTextArea jta = new JTextArea(msg);
        JScrollPane scrollPane = new JScrollPane(jta){
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(450, 300);
            }
        };

        JOptionPane.showMessageDialog(
                null, scrollPane, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /*public void runCommand(final String commandLine) throws IOException
    {
        Thread t = new Thread() {
            public void run() {
                Process p = null;
                try {
                    
                    p = Runtime.getRuntime().exec(commandLine);
                } catch (IOException e1) {
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(null, "An error occurred while running the visualizer: " + e1.getLocalizedMessage());
                    return;
                }
                // drain the output and error streams
                copyStream(p.getInputStream(), System.out);
                copyStream(p.getErrorStream(), System.err);

                try {
                    p.waitFor();
                } catch (InterruptedException e) {

                }
            }
        };
        t.start();
    }*/

    public static void main(String[] args)
    {
        String jobNumber = "";
        VisualizerLauncher dsLauncher = new VisualizerLauncher(jobNumber);
        dsLauncher.run();
    }
}
