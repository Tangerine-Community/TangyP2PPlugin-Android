package org.rti.tangerine.p2p;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.LOG;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServerAsyncTask extends AsyncTask<Void, Void, String> {
    private Context context;
    private String statusText;
    private static final String TAG = "TangyP2PPlugin:FS";
    private int port;
    private CallbackContext cbContext;


    public FileServerAsyncTask(Context context, String statusText, int port, CallbackContext cbContext) {
        this.context = context;
        this.statusText = statusText;
        this.port = port;
        this.cbContext = cbContext;
    }

    @Override
        protected String doInBackground(Void... params) {
        try {
            Log.d(TAG, "FileServerAsyncTask doInBackground started.");

            /**
             * Create a server socket and wait for client connections. This
             * call blocks until a connection is accepted from a client
             */
            ServerSocket serverSocket = new ServerSocket(port);
            Socket client = serverSocket.accept();
            String message = "Accepting connections on the server at " + port;
            TangyP2PPlugin.sendPluginMessage(message, true, cbContext, TAG);

            /**
             * If this code is reached, a client has connected and transferred data
             * Save the input stream from the client as a JPEG file
             */
            final File f = new File(Environment.getExternalStorageDirectory() + "/"
                    + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                    + ".jpg");

            File dirs = new File(f.getParent());
            if (!dirs.exists())
                dirs.mkdirs();
            f.createNewFile();
            InputStream inputstream = client.getInputStream();
            copyFile(inputstream, new FileOutputStream(f));

            serverSocket.close();
//            return f.getAbsolutePath();
            return message;
        } catch (IOException e) {
            TangyP2PPlugin.sendPluginMessage(e.getMessage(), true, cbContext, TAG);
            return null;
        }
    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);

            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(TAG, e.toString());
            return false;
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * @see android.os.AsyncTask#onPreExecute()
     */
    @Override
    protected void onPreExecute() { Log.d(TAG, "Opening a server socket"); }


    /**
     * Start activity that can handle the JPEG image
     */
    @Override
    protected void onPostExecute(String result) {
        if (result != null) {
//            statusText.setText("File copied - " + result);
//            Intent intent = new Intent();
//            intent.setAction(android.content.Intent.ACTION_VIEW);
//            intent.setDataAndType(Uri.parse("file://" + result), "image/*");
//            context.startActivity(intent);
            String message = "Here is the result: " + result;
            TangyP2PPlugin.sendPluginMessage(message, true, cbContext, TAG);
        }
    }
//
//    @Override
//    protected Object doInBackground(Object[] objects) {
//        return null;
//    }
}
