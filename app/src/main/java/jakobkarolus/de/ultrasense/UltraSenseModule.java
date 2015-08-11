package jakobkarolus.de.ultrasense;

import android.app.Activity;
import android.content.SharedPreferences;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import jakobkarolus.de.ultrasense.algorithm.AlgoHelper;
import jakobkarolus.de.ultrasense.audio.AudioManager;
import jakobkarolus.de.ultrasense.audio.CWSignalGenerator;
import jakobkarolus.de.ultrasense.audio.FMCWSignalGenerator;
import jakobkarolus.de.ultrasense.audio.SignalGenerator;
import jakobkarolus.de.ultrasense.features.DummyFeatureDetector;
import jakobkarolus.de.ultrasense.features.DummyFeatureProcessor;
import jakobkarolus.de.ultrasense.features.FeatureDetector;
import jakobkarolus.de.ultrasense.features.FeatureProcessor;
import jakobkarolus.de.ultrasense.features.GaussianFE;
import jakobkarolus.de.ultrasense.features.MeanBasedFD;
import jakobkarolus.de.ultrasense.features.activities.ActivityFP;
import jakobkarolus.de.ultrasense.features.activities.BedFallAE;
import jakobkarolus.de.ultrasense.features.activities.InferredContextCallback;
import jakobkarolus.de.ultrasense.features.activities.WorkdeskPresenceAE;
import jakobkarolus.de.ultrasense.features.gestures.DownUpGE;
import jakobkarolus.de.ultrasense.features.gestures.GestureCallback;
import jakobkarolus.de.ultrasense.features.gestures.GestureExtractor;
import jakobkarolus.de.ultrasense.features.gestures.GestureFP;
import jakobkarolus.de.ultrasense.features.gestures.SwipeGE;
import jakobkarolus.de.ultrasense.view.SettingsFragment;

/**
 * Factory class for creating different UltraSense scenarios
 *
 * <br><br>
 * Created by Jakob on 10.08.2015.
 */
public class UltraSenseModule {

    public static final double SAMPLE_RATE = 44100.0;
    private static final int fftLength = 4096;
    private static final int hopSize = 2048;
    private static double frequency = 20000;



    private AudioManager audioManager;
    private GestureFP gestureFP;
    private ActivityFP activityFP;
    private FeatureDetector featureDetector;
    private Activity activity;
    private boolean initialized;

    public UltraSenseModule(Activity activity){
        this.activity = activity;
        this.audioManager = new AudioManager(activity);
        this.initialized = false;
    }

    public void createCustomScenario(SharedPreferences settingsParameters) throws IllegalArgumentException{
        SignalGenerator signalGen;
        String mode = settingsParameters.getString(SettingsFragment.PREF_MODE, "CW");
        try {
            if(mode.equals(SettingsFragment.FMCW_MODE)) {
                double botFreq = Double.parseDouble(settingsParameters.getString(SettingsFragment.KEY_FMCW_BOT_FREQ, ""));
                double topFreq = Double.parseDouble(settingsParameters.getString(SettingsFragment.KEY_FMCW_TOP_FREQ, ""));
                double chirpDur = Double.parseDouble(settingsParameters.getString(SettingsFragment.KEY_FMCW_CHIRP_DUR, ""));
                double chirpCycles = Double.parseDouble(settingsParameters.getString(SettingsFragment.KEY_FMCW_CHIRP_CYCLES, ""));
                boolean rampUp = settingsParameters.getBoolean(SettingsFragment.KEY_FMCW_RAMP_UP, false);

                signalGen =  new FMCWSignalGenerator(topFreq, botFreq, chirpDur, chirpCycles, SAMPLE_RATE, 1.0f, !rampUp);
            }
            else {
                double freq = Double.parseDouble(settingsParameters.getString(SettingsFragment.KEY_CW_FREQ, ""));
                signalGen =  new CWSignalGenerator(freq, 0.1, 1.0, SAMPLE_RATE);
            }
        }catch (NumberFormatException e) {
            throw new IllegalArgumentException("Specified FMCW Parameters are not valid!", e);
        }

        try{
            if(mode.equals(SettingsFragment.CW_MODE)){
                int fftLength = Integer.parseInt(settingsParameters.getString(SettingsFragment.KEY_FFT_LENGTH, ""));
                double hopSizeFraction = Double.parseDouble(settingsParameters.getString(SettingsFragment.KEY_HOPSIZE, ""));
                int hopSize = (int) (hopSizeFraction * fftLength);
                int halfCarrierWidth = Integer.parseInt(settingsParameters.getString(SettingsFragment.KEY_HALF_CARRIER_WIDTH, ""));
                double dbThreshold = Double.parseDouble(settingsParameters.getString(SettingsFragment.KEY_DB_THRESHOLD, ""));
                double highFeatureThr = Double.parseDouble(settingsParameters.getString(SettingsFragment.KEY_HIGH_FEAT_THRESHOLD, ""));
                double lowFeatureThr = Double.parseDouble(settingsParameters.getString(SettingsFragment.KEY_LOW_FEAT_THRESHOLD, ""));
                int slackWidth = Integer.parseInt(settingsParameters.getString(SettingsFragment.KEY_FEAT_SLACK, ""));
                double freq = Double.parseDouble(settingsParameters.getString(SettingsFragment.KEY_CW_FREQ, ""));
                boolean ignoreNoise = settingsParameters.getBoolean(SettingsFragment.KEY_CW_IGNORE_NOISE, false);
                double maxFeatureThreshold = Double.parseDouble(settingsParameters.getString(SettingsFragment.KEY_CW_MAX_FEAT_THRESHOLD, ""));
                featureDetector = new MeanBasedFD(SAMPLE_RATE, fftLength, hopSize, freq, halfCarrierWidth, dbThreshold, highFeatureThr, lowFeatureThr, slackWidth, AlgoHelper.getHannWindow(fftLength), ignoreNoise, maxFeatureThreshold);
            }
            else
                featureDetector = new DummyFeatureDetector(0.0);

        }catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"Specified CW or Feature detection parameters are not valid!", e);
        }

        audioManager.setSignalGenerator(signalGen);
        FeatureProcessor fp = new DummyFeatureProcessor();
        featureDetector.registerFeatureExtractor(new GaussianFE(fp));

        audioManager.setFeatureDetector(featureDetector);
        this.initialized = true;
    }

    public void createGestureDetector(GestureCallback callback, boolean noisy, boolean usePreCalibration){

        audioManager.setSignalGenerator(new CWSignalGenerator(frequency, 0.1, 1.0, SAMPLE_RATE));

        if(noisy)
            featureDetector = new MeanBasedFD(SAMPLE_RATE, fftLength, hopSize, frequency, 3, -50.0, 3, 2, 1, AlgoHelper.getHannWindow(fftLength), true, 15);
        else
            featureDetector = new MeanBasedFD(SAMPLE_RATE, fftLength, hopSize, frequency, 4, -55.0, 3, 2, 0, AlgoHelper.getHannWindow(fftLength), false, 0.0);


        gestureFP = new GestureFP(callback);
        List<GestureExtractor> gestureExtractors = new Vector<>();
        gestureExtractors.add(new DownUpGE());
        gestureExtractors.add(new SwipeGE());

        for (GestureExtractor ge : gestureExtractors) {
            if (!usePreCalibration)
                initializeGEThresholds(ge, noisy);
            gestureFP.registerGestureExtractor(ge);
        }
        featureDetector.registerFeatureExtractor(new GaussianFE(gestureFP));
        audioManager.setFeatureDetector(featureDetector);
        this.initialized = true;
    }

    public void createWorkdeskPresenceDetector(InferredContextCallback callback){

        audioManager.setSignalGenerator(new CWSignalGenerator(frequency, 0.1, 1.0, SAMPLE_RATE));
        featureDetector = new MeanBasedFD(SAMPLE_RATE, fftLength, hopSize, frequency, 5, -60.0, 1.0, 0.5, 10, AlgoHelper.getHannWindow(fftLength), true, 8.0);

        activityFP = new ActivityFP();
        activityFP.registerActivityExtractor(new WorkdeskPresenceAE(callback));
        featureDetector.registerFeatureExtractor(new GaussianFE(activityFP));
        audioManager.setFeatureDetector(featureDetector);
        this.initialized = true;

    }

    public void createBedFallDetector(InferredContextCallback callback){

        audioManager.setSignalGenerator(new CWSignalGenerator(frequency, 0.1, 1.0, SAMPLE_RATE));
        featureDetector = new MeanBasedFD(SAMPLE_RATE, fftLength, hopSize, frequency, 5, -60.0, 2.0, 1.0, 5, AlgoHelper.getHannWindow(fftLength), true, 20.0);

        activityFP = new ActivityFP();
        activityFP.registerActivityExtractor(new BedFallAE(callback));
        featureDetector.registerFeatureExtractor(new GaussianFE(activityFP));
        audioManager.setFeatureDetector(featureDetector);
        this.initialized = true;

    }

    public void startDetection() throws IllegalStateException{
        if(!initialized || audioManager == null)
            throw new IllegalStateException("You must call a create method before starting any detection!");

        audioManager.startDetection();
    }

    public void stopDetection() throws IllegalStateException{
        if(!initialized || audioManager == null)
            throw new IllegalStateException("You must call a create method before stoping any detection!");

        audioManager.stopDetection();
        if(activityFP != null)
            activityFP.stopFeatureProcessing();
    }

    private boolean initializeGEThresholds(GestureExtractor ge, boolean noisy) {
        try {
            ObjectInputStream in = new ObjectInputStream(activity.openFileInput(ge.getName() + (noisy ? "_noisy" : "") + ".calib"));
            Map<String, Double> thresholds = (HashMap<String, Double>) in.readObject();
            return ge.setThresholds(thresholds);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    public ActivityFP getActivityFP() {
        return activityFP;
    }

    public GestureFP getGestureFP() {
        return gestureFP;
    }
    public AudioManager getAudioManager() {
        return audioManager;
    }


    public String printFeatureDetectionParameters(){
        if(featureDetector != null)
            return featureDetector.printParameters();
        else
            return "";
    }

    public void startRecord() {
        if(!initialized || audioManager == null)
            throw new IllegalStateException("You must call a create method before starting any detection!");

        try {
            audioManager.startRecord();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void stopRecord() {
        if(!initialized || audioManager == null)
            throw new IllegalStateException("You must call a create method before stoping any detection!");

        try {
            audioManager.stopRecord();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(activityFP != null)
            activityFP.stopFeatureProcessing();
    }

    public void saveRecordedFiles(String fileName) throws IOException {
        if(!initialized || audioManager == null)
            throw new IllegalStateException("You must call a create method before calling start/stop or save!");
        audioManager.saveWaveFiles(fileName);
    }
}
