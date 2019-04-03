package com.mycompany.projekt_embedded_android;


import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    GraphView graphTime;
    LineGraphSeries<DataPoint> timeSerie;
    GraphView graphFreq;
    LineGraphSeries<DataPoint> freqSerie;

    AudioProcessingThread audioProcessingThread;


    static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 111;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* initialize Graphs*/
        graphTime = (GraphView) findViewById(R.id.graphTime);
        graphTime.setTitle("Audio Signal");
        timeSerie = new LineGraphSeries<>();
        timeSerie.setThickness(1);
        timeSerie.setTitle("Time Serie");
        graphTime.addSeries(timeSerie);

        /* initialize Graphs*/
        graphFreq = (GraphView) findViewById(R.id.graphFrequenzy);
        freqSerie = new LineGraphSeries<>();
        freqSerie.setThickness(1);
        freqSerie.setTitle("Frequency Serie");
        graphFreq.addSeries(timeSerie);

        /* Initialize and Start Audio Processing Thread*/
        audioProcessingThread = new AudioProcessingThread();


        /* Askin for Record_Audio Permission if we dont have this Permission */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            Log.i("Main","No Audio Permission");

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        }else{

            /* If we have the permission then start the Task */
            audioProcessingThread.start();
        }
    }

    /* Happens when Request Permission is answer by the user */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!

                    /* If we have the permission then start the Task */
                    audioProcessingThread.start();


                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                    Toast.makeText(this,"This app does not work correctly without this permission.",Toast.LENGTH_LONG);
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }



    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    //public native String stringFromJNI();



    /**
     *  Audio Processing Class
     *
     * */

    public class AudioProcessingThread extends Thread {

        final String LOG_TAG = "Audio Processing Thread";

        final int SAMPLE_RATE = 44100; // The sampling rate
        boolean mShouldContinue = true; // Indicates if recording / playback should stop

        double full_buffer[] = new double[SAMPLE_RATE*1];
        int bufferSize, audiBufferSize;

        int fftBufferSize = (int)Math.pow(2,14);

        double preFFTBuffer[] = new double[fftBufferSize];
        double fftBuffer[] = new double[fftBufferSize];;

        FFT Fft = new FFT(fftBufferSize);


        DataPoint dataToPlot[];

        AudioRecord record;


        public void run() {

            /* Set thread priority */
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

            /* Calc buffersize needed */
            bufferSize = 5 * AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                bufferSize = (int)(SAMPLE_RATE * 1);
            }

            audiBufferSize = bufferSize/2;

            /* Create Audio Buffer*/
            final short[] audioBuffer = new short[audiBufferSize];

            /* Create Audio Record */
            record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);

            /* Check if Audio Record is initialized */
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(LOG_TAG, "Audio Record can't initialize!");
                return;
            }

            /* Start the Recording */
            record.startRecording();
            Log.v(LOG_TAG, "Start recording");


            long shortsRead = 0;
            while (mShouldContinue) {
                /* Read the Audio Buffer*/
                int numberOfShort = record.read(audioBuffer, 0, audioBuffer.length);
                shortsRead += numberOfShort;

                /* Process the Buffer */

                // Shift full buffer
                int i;
                for( i=0; i < full_buffer.length - 1 - audioBuffer.length; i++){
                    full_buffer[i] = full_buffer[i+audioBuffer.length];
                }

                // Copy audio Buffer into full buffer
                for( i =0; i < audioBuffer.length-1; i++){
                    full_buffer[full_buffer.length-audioBuffer.length+i] = (double)audioBuffer[i];
                }

                // Extract preFft Buffer
                for( i=0; i < preFFTBuffer.length; i++){
                    preFFTBuffer[i] = full_buffer[ full_buffer.length - preFFTBuffer.length+i];
                }

                // Make FFT
                Fft.fft(preFFTBuffer,fftBuffer);




                dataToPlot = generateData(fftBuffer, SAMPLE_RATE);

                Log.i(LOG_TAG,"AudioBuffer Size: "+ (float)audioBuffer.length/(float)SAMPLE_RATE);
                Log.i(LOG_TAG,"FullBuffer Size: "+ (float)full_buffer.length/(float)SAMPLE_RATE);





                /* Update the UI Elements. */
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        /* Reset the data of the graphTime */
                        timeSerie.resetData(dataToPlot);


                    }
                });


            }

            record.stop();
            record.release();

            Log.v(LOG_TAG, String.format("Recording stopped. Samples read: %d", shortsRead));
        }

        /* This function creates a datapoint array out of a short array */
        private DataPoint[] generateData(double data[], int fa) {
            int count = data.length;
            DataPoint[] values = new DataPoint[count];



            for (int i=0; i<count; i++) {
                double x = (double)i;
                double y = data[i];
                DataPoint v = new DataPoint(x, y);
                values[i] = v;
            }

            return values;
        }
    }



}
