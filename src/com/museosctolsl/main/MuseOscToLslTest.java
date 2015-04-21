package com.museosctolsl.main;

import java.io.IOException;

import com.museosctolsl.museio.MuseIOReceiver;
import com.museosctolsl.museio.MuseIOReceiver.MuseConfig;
import com.museosctolsl.museio.MuseIOReceiver.MuseDataListener;

import edu.ucsd.sccn.LSL;

public class MuseOscToLslTest implements MuseDataListener {
	//Outlet to LSL
	LSL.StreamOutlet eegOutlet;
	//OSC Receiver
	MuseIOReceiver museReceiver;
	
	void init(){
		//listen at UDP port 5001
		museReceiver = new MuseIOReceiver(5001, true);
		//StreamInfo for LSL Outlet
		LSL.StreamInfo eegStreamInfo = new LSL.StreamInfo("MuseEEG","EEG",4,220,LSL.ChannelFormat.float32,"myuid12345");
		//Add channel description to meta data of stream
		LSL.XMLElement chns = eegStreamInfo.desc().append_child("channels");
        String[] labels = {"T9","Fp1","Fp2","T10"};
        for (int k=0;k<labels.length;k++){
            chns.append_child("channel")
                .append_child_value("label", labels[k])
                .append_child_value("unit", "microvolts")
                .append_child_value("type","EEG");
        }   
        //Create the eeg outlet
        System.out.println("Creating an eeg outlet...");
        eegOutlet = new LSL.StreamOutlet(eegStreamInfo);  
	}
	
	void run(){
		//register this class as MuseDataListener
		museReceiver.registerMuseDataListener(this);
		//connect the museReceiver
		try {
			museReceiver.connect();
			System.out.println("museReceiver Connected");
		} catch (IOException e1) {
			e1.printStackTrace();
			System.out.println("Error connecting museReceiver");
		}
	}

	public void receiveMuseElementsAlpha(MuseConfig config, float[] alpha) {
		System.out.print("Alpha relative: ");
		for(int i = 0; i< alpha.length; i++){
			System.out.print(alpha[i] + "\t");
		}
		System.out.print("\n");
	}

	public void receiveMuseElementsBeta(MuseConfig config, float[] beta) {
		System.out.print("Beta relative: ");
		for(int i = 0; i< beta.length; i++){
			System.out.print(beta[i] + "\t");
		}	
		System.out.print("\n");
	}

	public void receiveMuseElementsTheta(MuseConfig config, float[] theta) {
		System.out.print("Theta relative: ");
		for(int i = 0; i< theta.length; i++){
			System.out.print(theta[i] + "\t");
		}
		System.out.print("\n");
	}

	public void receiveMuseElementsDelta(MuseConfig config, float[] delta) {
		System.out.print("Delta relative: ");
		for(int i = 0; i< delta.length; i++){
			System.out.print(delta[i] + "\t");
		}
		System.out.print("\n");
	}

	//4 values: ffff -> 4 channel eeg
	public void receiveMuseEeg(MuseConfig config, float[] eeg) {
		System.out.print("Muse EEG: ");
		for(int i = 0; i< eeg.length; i++){
			System.out.print(eeg[i] + "\t");
		} 
		System.out.print("\n");
		//push eeg to LSL outlet
		eegOutlet.push_sample(eeg);
		//to make sure you get proper timestamps, maybe do it like this:
		//eegOutlet.push_sample(eeg, LSL.local_clock());
	}

	//6 values: ffffii -> 4 channel eeg, 2 timestamp values
	public void receiveMuseEegWithTimestamps(MuseConfig config, float[] eeg,
			int[] timestamps) {// TODO Auto-generated method stub
		System.out.print("Muse EEG: ");
		for(int i = 0; i < eeg.length; i++){
			System.out.print(eeg[i] + "\t");
		} 
		System.out.println("Timestamp: " + timestamps[0] + "." + timestamps[1] +  "\t");
		//push eeg to LSL outlet
		eegOutlet.push_sample(eeg);
	}

	public void receiveMuseBlink(MuseConfig config, int blink) {
		// TODO Auto-generated method stub
		
	}

	public void receiveMuseAccel(MuseConfig config, float[] accel,
			int[] timestamps) {
		// TODO Auto-generated method stub
		
	}

	public void receiveMuseBattery(MuseConfig config, int[] battery) {
		// TODO Auto-generated method stub
		
	}

	public void receiveMuseExperimentalMellow(MuseConfig config, float[] mellow) {
		// TODO Auto-generated method stub
		
	}

	public void receiveMuseExperimentalConcentration(MuseConfig config,
			float[] concentration) {
		// TODO Auto-generated method stub
		
	}
}
