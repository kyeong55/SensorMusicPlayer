package com.example.taegyeong.sensormusicplayer;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity {

    public Typeface branBlack;
    public Typeface branBold;
    public Typeface branRegular;
    public Typeface branLight;

    private final int duration = 3; // seconds
    private final int sampleRate = 8000;
    private final int numSamples = sampleRate*duration;
    private final double sample[] = new double[numSamples];
    private final byte generatedSnd[] = new byte[2 * numSamples];
    private int lastFreq=0;

    private AudioTrack audioTrack;

    private SensorManager mSensorManager;
    private SensorEventListener proximityListener;
    private SensorEventListener rotationListener;

    private int volume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        branBlack = Typeface.createFromAsset(getAssets(), "brandon_blk.otf");
        branBold = Typeface.createFromAsset(getAssets(), "brandon_bld.otf");
        branRegular = Typeface.createFromAsset(getAssets(), "brandon_med.otf");
        branLight = Typeface.createFromAsset(getAssets(), "brandon_reg.otf");

        TextView titleBar = (TextView) findViewById(R.id.title_bar);
        TextView proximityLabel = (TextView) findViewById(R.id.proximity_subtitle);
        TextView rotationLabel = (TextView) findViewById(R.id.roatation_subtitle);
        TextView frequencyLabel = (TextView) findViewById(R.id.frequency_subtitle);
        final TextView proximity = (TextView) findViewById(R.id.proximity);
        final TextView rotation = (TextView) findViewById(R.id.rotation);
        final TextView frequency = (TextView) findViewById(R.id.frequency);

        assert titleBar != null;
        assert proximityLabel != null;
        assert rotationLabel != null;
        assert frequencyLabel != null;
        assert proximity != null;
        assert rotation != null;
        assert frequency != null;

        titleBar.setTypeface(branBlack);
        proximityLabel.setTypeface(branRegular);
        rotationLabel.setTypeface(branRegular);
        frequencyLabel.setTypeface(branRegular);
        proximity.setTypeface(branBold);
        rotation.setTypeface(branBold);
        frequency.setTypeface(branBold);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        proximityListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float proximityDistance = event.values[0];
                if (proximityDistance > 0) {
                    volume = 10;
                    audioTrack.setVolume(10);
                    proximity.setText("Far: " + proximityDistance + " cm");
                } else {
                    volume = 0;
                    audioTrack.setVolume(0);
                    proximity.setText("Near: " + proximityDistance + " cm");
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        rotationListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                int freq = (int)(500 - event.values[1]*500/180);
                rotation.setText(printRotationDegree(event.values[1]) + "°");
                if(freq != lastFreq){
                    frequency.setText(freq + " Hz");
                    lastFreq = freq;
                    SoundGenTask task = new SoundGenTask();
                    task.execute(freq);
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        SoundGenTask task = new SoundGenTask();
        volume = 10;
        task.execute(440);
    }

    @Override
    protected void onDestroy(){
        mSensorManager.unregisterListener(proximityListener);
        mSensorManager.unregisterListener(rotationListener);
        if(audioTrack != null) {
            audioTrack.pause();
            audioTrack.release();
        }
        super.onDestroy();
    }

    private float printRotationDegree(float degree){
        int size = 10000;
        int cutter = (int)(degree * size);
        return ((float)cutter)/size;
    }

    private void registerSensor(int samplePeriod){
        Sensor proximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        Sensor rotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        mSensorManager.registerListener(proximityListener, proximitySensor,
                SensorManager.SENSOR_DELAY_NORMAL, samplePeriod);
        mSensorManager.registerListener(rotationListener, rotationSensor,
                SensorManager.SENSOR_DELAY_NORMAL, samplePeriod);
    }

    private void genTone(double freqOfTone){
        Log.d("debugging","freq: "+freqOfTone);

        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
        }
        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    }

    private void playSound(){
        AudioTrack newAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                AudioTrack.MODE_STATIC);
        newAudioTrack.write(generatedSnd, 0, generatedSnd.length);
        newAudioTrack.setLoopPoints(0, generatedSnd.length/2, -1);
        newAudioTrack.setVolume(volume);
        newAudioTrack.play();

        if(audioTrack != null) {
            audioTrack.pause();
            audioTrack.release();
            audioTrack = null;
        }
        audioTrack = newAudioTrack;
        registerSensor(100);
    }

    public class SoundGenTask extends AsyncTask<Integer, Void, Void> {
        @Override
        public Void doInBackground(Integer... params) {
            mSensorManager.unregisterListener(proximityListener);
            mSensorManager.unregisterListener(rotationListener);
            genTone(params[0]);
            playSound();
            return null;
        }

        @Override
        public void onPostExecute(Void result) {
            super.onPostExecute(result);
        }
    }
}
