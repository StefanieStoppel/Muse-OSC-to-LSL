package com.museosctolsl.museio;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import netP5.TcpClient;
import oscP5.OscMessage;
import oscP5.OscP5;

/**
 * The responsibility of this class is to receive Muse data from Muse-IO via the
 * OSC protocol. It will then pass any brainwave information that is received to
 * any registered MuseDataListeners.
 * <p>
 * There is a single muse per connection policy. So, UDP ports should only
 * receive data from a single muse. TCP ports can have multiple connections and
 * so can support multiple muses on a single port for each connection.
 * <p>
 * Makes use of a modified version of the oscp5 library. Modifications to the
 * library include some bug fixes with regards to disconnecting from a TCP
 * socket and better exception handling.
 */
public class MuseIOReceiver {

	/**
	 * A data structure containing all of the various values obtained from a
	 * /muse/config OSC message from muse-io
	 */
	public static class MuseConfig {
		public String macAddr, serialNumber, preset, eegChannelLayout,
				eegUnits, accUnits;
		public boolean filtersEnabled, batteryDataEnabled, compressionEnabled,
				accDataEnabled, drlrefDataEnabled, errorDataEnabled;
		int notchFrequencyHz, eegSampleFrequencyHz, eegOutputFrequencyHz,
				eegChannelCount, eegSameplesBitwidth, eegDownsample, afeGain,
				batteryPercentRemaining, batteryMillivolts,
				accSampleFrequencyHz, dsprefSampleFrequencyHz;
		double eegConversionFactor, drlrefConversionFactor,
				accConversionFactor;

		private MuseConfig() {
		}
	}

	/**
	 * A listener interface for incoming data. Implement this interface and
	 * register the listener with
	 * {@link MuseIOReceiver#registerMuseDataListener(MuseDataListener)} to
	 * receive data. Data is received on a separate connection thread.
	 */
	public interface MuseDataListener {
		void receiveMuseElementsAlpha(MuseConfig config, float[] alpha);

		void receiveMuseElementsBeta(MuseConfig config, float[] beta);

		void receiveMuseElementsTheta(MuseConfig config, float[] theta);

		void receiveMuseElementsDelta(MuseConfig config, float[] delta);
		
		void receiveMuseEeg(MuseConfig config, float[] eeg);
		
		void receiveMuseEegWithTimestamps(MuseConfig config, float[] eeg, int[] timestamps);
		
		void receiveMuseBlink(MuseConfig config, int blink);
		
		void receiveMuseAccel(MuseConfig config, float[] accel, int[] timestamps);
		
		void receiveMuseBattery(MuseConfig config, int[] battery);
		
		void receiveMuseExperimentalMellow(MuseConfig config, float[] mellow);
		
		void receiveMuseExperimentalConcentration(MuseConfig config, float[] concentration);
	}

	private final int PORT;

	private final boolean UDP;

	private final CopyOnWriteArrayList<MuseDataListener> listeners;

	private OscReceiver osc;
	
	private final int MUSE_BLINK = 1;

	/**
	 * Creates an instance that can listen for muse-io OSC messages using the
	 * default muse-io port (5000) using the default communication protocol
	 * (TCP).
	 */
	public MuseIOReceiver() {
		this(5000, false);
	}

	/**
	 * Creates an instance that can listen for muse-io OSC messages using the
	 * specified port using the default communication protocol (TCP).
	 * 
	 * @param port
	 *            Specified port.
	 */
	public MuseIOReceiver(int port) {
		this(port, false);
	}

	/**
	 * Creates an instance that can listen for muse-io OSC messages using the
	 * specified port using the specified communication protocol.
	 * 
	 * @param port
	 *            Specified port.
	 * @param udp
	 *            Specified communication protocol. True if using UDP, otherwise
	 *            using TCP.
	 */
	public MuseIOReceiver(int port, boolean udp) {
		this.PORT = port;
		this.UDP = udp;
		this.listeners = new CopyOnWriteArrayList<MuseDataListener>();
	}

	/**
	 * Registers the specified listener to receive muse data if it has not
	 * already been registered to do so, otherwise does nothing.
	 * 
	 * @param listener
	 *            Specified listener.
	 */
	public void registerMuseDataListener(MuseDataListener listener) {
		this.listeners.addIfAbsent(listener);
	}

	/**
	 * Unregisters the specified listener if it is currently registered,
	 * otherwise does nothing.
	 * 
	 * @param listener
	 *            Specified listener.
	 */
	public void unregisterMuseDataListener(MuseDataListener listener) {
		this.listeners.remove(listener);
	}

	/**
	 * Binds to the port that was selected for use during construction.
	 * 
	 * @throws IOException
	 *             if the socket is already bound.
	 */
	public void connect() throws IOException {
		this.osc = new OscReceiver(this.PORT, this.UDP);
	}

	/**
	 * Unbinds the socket to stop listening for OSC messages if it is currently
	 * bound, otherwise does nothing.
	 */
	public void disconnect() {
		if (this.osc != null) {
			this.osc.disconnect();
			this.osc = null;
		}
	}

	private class OscReceiver {

		private final HashMap<TcpClient, MuseConfig> museConfigs;

		private OscP5 osc;

		public OscReceiver(int port, boolean udp) throws IOException {
			this.museConfigs = new HashMap<TcpClient, MuseConfig>();
			if (udp)
				this.osc = new OscP5(this, port);
			else
				this.osc = new OscP5(this, port, OscP5.TCP);
		}

		public synchronized void disconnect() {
			this.osc.dispose();
			this.osc = null;
		}

		// The oscp5 library is kind of weird in that it makes use of reflection
		// to send OSC messages to this method. The reason for this is that the
		// library is intended for use for people working with Processing. I use
		// this library because it supports a good variety of data types, but
		// you could make use of a different library if you want.
		@SuppressWarnings("unused")
		public synchronized void oscEvent(final OscMessage msg) {
			MuseConfig config = null;

			// Reuse the configuration instead of creating a new one each time
			// a muse configuration message is received.
			if (MuseIOReceiver.this.UDP) {
				config = this.museConfigs.get(null);
			} else {
				config = this.museConfigs.get(msg.tcpConnection());
			}

			String addressPattern = msg.addrPattern();
			if (config == null && addressPattern.equals("/muse/config")) {
				Gson gson = new GsonBuilder().setFieldNamingPolicy(
						FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

				// Parse the JSON muse configuration message.
				MuseConfig newConfig = gson.fromJson(msg.get(0).stringValue(),
						MuseConfig.class);

				if (MuseIOReceiver.this.UDP) {
					this.museConfigs.put(null, newConfig);
				} else {
					this.museConfigs.put(msg.tcpConnection(), newConfig);
				}
			}

			// Once a muse configuration message has been received then I can
			// figure out from which muse messages are coming from and then
			// start to send the information received to the listeners.
			else if (config != null) {
				if (addressPattern.equals("/muse/eeg"))
					this.sendEeg(config, msg);
				else if(addressPattern.equals("/muse/elements/blink"))
					this.sendBlink(config, msg);
				else if (addressPattern.equals("/muse/elements/alpha_relative"))
					this.sendAlpha(config, msg);
				else if (addressPattern.equals("/muse/elements/beta_relative"))
					this.sendBeta(config, msg);
				else if (addressPattern.equals("/muse/elements/theta_relative"))
					this.sendTheta(config, msg);
				else if (addressPattern.equals("/muse/elements/delta_relative"))
					this.sendDelta(config, msg);
				else if (addressPattern.equals("/muse/elements/experimental/mellow"))
					this.sendMellow(config, msg);
				else if (addressPattern.equals("/muse/elements/experimental/concentration"))
					this.sendConcentration(config, msg);
				else if (addressPattern.equals("/muse/acc"))
					this.sendAccel(config, msg);
				else if (addressPattern.equals("/muse/batt"))
					this.sendBattery(config, msg);
				
				
			}
		}

		private float[] getFloatVals(OscMessage msg) {
			int numChannels = msg.typetag().length();
			float[] floatVals = new float[numChannels];
			for (int i = 0; i < numChannels; i++)
				floatVals[i] = msg.get(i).floatValue();
			return floatVals;
		}
		
		private int[] getIntVals(OscMessage msg) {
			int numChannels = msg.typetag().length();
			int[] intVals = new int[numChannels];
			for (int i = 0; i < numChannels; i++)
				intVals[i] = msg.get(i).intValue();
			return intVals;
		}
		
		private float getSingleFloatValue(OscMessage msg) {
			float val = 0.0f;
			int numChannels = msg.typetag().length();
			if(numChannels == 1){
				val = msg.get(0).floatValue();
			}
			return val;
		}
		
		private int getSingleIntValue(OscMessage msg){
			int numChannels = msg.typetag().length();
			int intVal = -1;
			if(numChannels == 1){
				intVal = msg.get(0).intValue();
			}
			return intVal;
		}

		private void sendBlink(MuseConfig config, OscMessage msg){
			int blinkValue = this.getSingleIntValue(msg);
			if(blinkValue == MUSE_BLINK){//only send if it was a blink
				for (MuseDataListener l : MuseIOReceiver.this.listeners) {
					l.receiveMuseBlink(config, blinkValue);
				}
			}
		}	

		private void sendConcentration(MuseConfig config, OscMessage msg) {
			float[] concentrationVal = new float[1];
			concentrationVal[0] = this.getSingleFloatValue(msg);
			System.out.println("Concentration: " + concentrationVal[0]);
			for (MuseDataListener l : MuseIOReceiver.this.listeners) {
				l.receiveMuseExperimentalMellow(config, concentrationVal);
			}			
		}

		private void sendMellow(MuseConfig config, OscMessage msg) {
			float[] mellowVal = new float[1];
			mellowVal[0] = this.getSingleFloatValue(msg);
			System.out.println("Mellow: " + mellowVal[0]);
			for (MuseDataListener l : MuseIOReceiver.this.listeners) {
				l.receiveMuseExperimentalMellow(config, mellowVal);
			}
		}
		
		private void sendEeg(MuseConfig config, OscMessage msg) {
			float[] eegValues;
			int[] timeStamps;
			int msgLength = msg.typetag().length();
			/**
			 * msgLength == 4: eeg and no timestamps, format: ffff
			 * msgLength > 4: eeg and timestamps, format: ffffii
			 */
			if(msgLength > 4){
				eegValues = new float[4];
				timeStamps = new int[2];
				for (int i = 0; i < msgLength; i++){
					if(i < 4){
						eegValues[i] = msg.get(i).floatValue();
					}else{
						timeStamps[i-4] = msg.get(i).intValue();
					}
				}
				for (MuseDataListener l : MuseIOReceiver.this.listeners) {
					l.receiveMuseEegWithTimestamps(config, eegValues, timeStamps);
				}
			}else{
				eegValues = new float[4];
				timeStamps = null;
				//eeg values in array of floats
				for (int i = 0; i < eegValues.length; i++)
					eegValues[i] = msg.get(i).floatValue();
				for (MuseDataListener l : MuseIOReceiver.this.listeners) {
					l.receiveMuseEeg(config, eegValues);
				}
			}			
		}
		
		private void sendAccel(MuseConfig config, OscMessage msg) {
			float[] accelValues;
			int[] timeStamps;
			int msgLength = msg.typetag().length();
			
			//we have 6 channels, two of which are integer values
			if(msgLength > 3){
				accelValues = new float[3];
				timeStamps = new int[2];
				//accel values in array of floats
				for (int i = 0; i < accelValues.length; i++)
					accelValues[i] = msg.get(i).floatValue();
				//timestamp values in array of integers
				for(int j = 0; j < 2; j++){
					timeStamps[j] = msg.get(j+3).intValue();
				}
			}else{
				accelValues = new float[3];
				timeStamps = null;
				//eeg values in array of floats
				for (int i = 0; i < accelValues.length; i++)
					accelValues[i] = msg.get(i).floatValue();
			}
			for (MuseDataListener l : MuseIOReceiver.this.listeners) {
				l.receiveMuseAccel(config, accelValues, timeStamps);
			}
		}
		
		private void sendAlpha(MuseConfig config, OscMessage msg) {
			float[] alpha = this.getFloatVals(msg);
			for (MuseDataListener l : MuseIOReceiver.this.listeners) {
				l.receiveMuseElementsAlpha(config, alpha);
			}
		}

		private void sendBeta(MuseConfig config, OscMessage msg) {
			float[] beta = this.getFloatVals(msg);
			for (MuseDataListener l : MuseIOReceiver.this.listeners) {
				l.receiveMuseElementsBeta(config, beta);
			}
		}

		private void sendTheta(MuseConfig config, OscMessage msg) {
			float[] theta = this.getFloatVals(msg);
			for (MuseDataListener l : MuseIOReceiver.this.listeners) {
				l.receiveMuseElementsTheta(config, theta);
			}
		}

		private void sendDelta(MuseConfig config, OscMessage msg) {
			float[] delta = this.getFloatVals(msg);
			for (MuseDataListener l : MuseIOReceiver.this.listeners) {
				l.receiveMuseElementsDelta(config, delta);
			}
		}
		
		
		
		private void sendBattery(MuseConfig config, OscMessage msg) {
			int[] battery = this.getIntVals(msg);
			for (MuseDataListener l : MuseIOReceiver.this.listeners) {
				l.receiveMuseBattery(config, battery);
			}
		}
	}
}