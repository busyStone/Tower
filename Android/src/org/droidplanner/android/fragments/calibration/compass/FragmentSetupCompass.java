package org.droidplanner.android.fragments.calibration.compass;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.CalibrationApi;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeEventExtra;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.calibration.magnetometer.MagnetometerCalibrationProgress;
import com.o3dr.services.android.lib.drone.calibration.magnetometer.MagnetometerCalibrationResult;
import com.o3dr.services.android.lib.drone.property.State;

import org.droidplanner.android.R;
import org.droidplanner.android.fragments.helpers.ApiListenerFragment;
import org.w3c.dom.Text;

/**
 * Created by clover on 16/3/17.
 */
public class FragmentSetupCompass extends ApiListenerFragment {

    private static final IntentFilter intentFilter = new IntentFilter();
    static {
        intentFilter.addAction(AttributeEvent.CALIBRATION_MAG_PROGRESS);
        intentFilter.addAction(AttributeEvent.CALIBRATION_MAG_COMPLETED);
        intentFilter.addAction(AttributeEvent.CALIBRATION_MAG_CANCELLED);
        intentFilter.addAction(AttributeEvent.STATE_CONNECTED);
        intentFilter.addAction(AttributeEvent.STATE_DISCONNECTED);
    }

    private Button btnStart;
    private Button btnCancel;
    private ProgressBar pbCalibrate;
    private TextView pbValue;
    private RelativeLayout inProgressContainer;
    private TextView fitnessLable;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case AttributeEvent.CALIBRATION_MAG_PROGRESS:
                    MagnetometerCalibrationProgress progress =
                            (MagnetometerCalibrationProgress)intent.getExtras(
                            ).get(AttributeEventExtra.EXTRA_CALIBRATION_MAG_PROGRESS);
                    if (progress != null){
                        updateCalibratingProgress(progress.getCompletionPercentage());
                    }
                    break;
                case AttributeEvent.CALIBRATION_MAG_COMPLETED:
                    MagnetometerCalibrationResult result =
                            (MagnetometerCalibrationResult)intent.getExtras(
                            ).get(AttributeEventExtra.EXTRA_CALIBRATION_MAG_RESULT);
                    updateCalibrationResult(result);
                    break;
                case AttributeEvent.CALIBRATION_MAG_CANCELLED:
                    break;
                case AttributeEvent.STATE_CONNECTED:
                    updateUIState();
                    break;
                case AttributeEvent.STATE_DISCONNECTED:
                    updateUIState();
                    break;

            }
        }
    };

    @Override
    public void onApiConnected(){

        updateUIState();

        getBroadcastManager().registerReceiver(broadcastReceiver, intentFilter);
    };

    @Override
    public void onApiDisconnected(){
        getBroadcastManager().unregisterReceiver(broadcastReceiver);
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setup_compass, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnStart = (Button) view.findViewById(R.id.buttonStart);
        btnStart.setEnabled(false);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Drone drone = getDrone();
                CalibrationApi.getApi(drone).startMagnetometerCalibration();

                btnStart.setEnabled(false);
                showInProgressContainer();

            }
        });

        btnCancel = (Button)view.findViewById(R.id.buttonCancel);
        btnCancel.setEnabled(false);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Drone drone = getDrone();
                CalibrationApi.getApi(drone).cancelMagnetometerCalibration();

                updateUIState();
            }
        });

        inProgressContainer = (RelativeLayout)view.findViewById(R.id.in_progress_compass_cal_container);
        pbCalibrate = (ProgressBar)view.findViewById(R.id.compass_cal_progress_bar);
        pbValue = (TextView)view.findViewById(R.id.compass_cal_progress);

        fitnessLable = (TextView)view.findViewById(R.id.compass_cal_result);
    }

    void updateCalibratingProgress(int val){
        pbCalibrate.setProgress(val);
        pbValue.setText(String.format("%s%%", String.format("%d", val)));
    }

    void updateCalibrationResult(MagnetometerCalibrationResult result){

        btnCancel.setEnabled(false);
        btnStart.setEnabled(true);
        updateCalibratingProgress(100);

        if (result == null){
            return;
        }

        if (result.isCalibrationSuccessful()){
            inProgressContainer.setBackgroundColor(Color.GREEN);
        }else{
            inProgressContainer.setBackgroundColor(Color.RED);
        }

        fitnessLable.setVisibility(View.VISIBLE);
        fitnessLable.setText(String.format("fitness %f",result.getFitness()));
    }

    void showInProgressContainer(){
        inProgressContainer.setBackgroundColor(Color.WHITE);
        inProgressContainer.setVisibility(View.VISIBLE);
        btnCancel.setEnabled(true);
        updateCalibratingProgress(0);
        fitnessLable.setVisibility(View.INVISIBLE);
    }

    void hideInProgressContainer(){
        inProgressContainer.setVisibility(View.INVISIBLE);
        btnCancel.setEnabled(false);
        updateCalibratingProgress(0);
    }

    void updateButtonStartEnableState(){
        Drone drone = getDrone();
        State droneState = drone.getAttribute(AttributeType.STATE);

        if (drone.isConnected() && !droneState.isFlying()) {
            btnStart.setEnabled(true);
            if (droneState.isCalibrating()){
                showInProgressContainer();
            }
        }else{
            btnStart.setEnabled(false);
        }
    }

    void updateUIState(){
        hideInProgressContainer();
        updateButtonStartEnableState();
    }

    public static CharSequence getTitle(Context context) {
        return context.getText(R.string.setup_mag_title);
    }
}
