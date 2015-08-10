package edu.cmu.pocketsphinx.demo;

import android.app.Activity;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ServerActivity extends Activity {
    private Button record;
    private TextView text;
    private TextView recognizing;
    boolean recording=false;

    RecordAudio recordTask;
    ServerAction serverTask;
    File recordingFile;

    int frequency = 16000,channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        text=(TextView)this.findViewById(R.id.textview_speech);
        recognizing=(TextView)this.findViewById(R.id.textview_recognizing);
        record = (Button) this.findViewById(R.id.button_record);
        record.setBackgroundColor(Color.GRAY);
        record.setOnTouchListener(new View.OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        record.setBackgroundColor(Color.LTGRAY);
                        record.setText("Release to end");
                        // press
                        Thread recordThread = new Thread(new Runnable() {

                            @Override
                            public void run() {
                                recording = true;
                                startRecord();
                            }

                        });

                        recordThread.start();


                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        record.setBackgroundColor(Color.GRAY);
                        record.setText("Hold to record");
                        // release
                        recording = false;

                        serverTask = new ServerAction();
                        serverTask.execute();

                        return false;
                    }
                    default:
                        return false;
                }
            }
        });

        File path = new File(
                Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/Sphinx/");
        path.mkdirs();
        try {
            recordingFile = File.createTempFile("recording", ".pcm", path);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't create file on SD card", e);
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_server, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startRecord(){

        recordTask = new RecordAudio();
        recordTask.execute();

    }

    private class RecordAudio extends AsyncTask<Void, Integer, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            recording = true;
            try {
                DataOutputStream dos = new DataOutputStream(
                        new BufferedOutputStream(new FileOutputStream(
                                recordingFile)));
                int bufferSize = AudioRecord.getMinBufferSize(frequency,
                        channelConfiguration, audioEncoding);
                AudioRecord audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC, frequency,
                        channelConfiguration, audioEncoding, bufferSize);

                byte[] buffer = new byte[bufferSize];
                audioRecord.startRecording();
                int r = 0;
                while (recording) {
                    int bufferReadResult = audioRecord.read(buffer, 0,
                            bufferSize);
                    for (int i = 0; i < bufferReadResult; i++) {
                        dos.writeByte(buffer[i]);
                    }
                    publishProgress(new Integer(r));
                    r++;
                }
                audioRecord.stop();
                dos.close();
            } catch (Throwable t) {
                Log.e("AudioRecord", "Recording Failed");
            }
            return null;
        }
        protected void onProgressUpdate(Integer... progress) {
        }
        protected void onPostExecute(Void result) {
        }
    }

    private class ServerAction extends AsyncTask<Void, Integer, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            runOnUiThread(new Runnable() {
                public void run() {
                    recognizing.setText("Recognizing");
                }
            });
            byte[] audiodata = new byte[(int)(recordingFile.length())];
            try {
                DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(recordingFile)));
                while ( dis.available() > 0) {
                    int i = 0;
                    while (dis.available() > 0 && i < audiodata.length) {
                        audiodata[i] = dis.readByte();
                        i++;
                    }
                }
                dis.close();
                //Socket socket = new Socket("192.168.1.5",7000);   //gary's ip
                Socket socket = new Socket("192.168.1.19",7000);   //eric's ip
                DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());

                dOut.writeInt(audiodata.length); // write length of the message
                dOut.write(audiodata);           // write the message
                System.out.println("Sent to server");
                DataInputStream fromServer = new DataInputStream(socket.getInputStream());
                final String s = fromServer.readUTF();
                runOnUiThread(new Runnable() {
                    public void run() {
                        text.setText(s);
                        recognizing.setText(" ");
                    }
                });
            } catch (Throwable t) {
                Log.e("AudioTrack", "Playback Failed");
            }
            return null;
        }
    }
}
