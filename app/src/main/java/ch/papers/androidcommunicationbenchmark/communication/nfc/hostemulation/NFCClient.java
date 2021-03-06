package ch.papers.androidcommunicationbenchmark.communication.nfc.hostemulation;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.provider.Settings;

import java.io.IOException;
import java.util.Arrays;

import ch.papers.androidcommunicationbenchmark.communication.AbstractClient;
import ch.papers.androidcommunicationbenchmark.models.BenchmarkResult;
import ch.papers.androidcommunicationbenchmark.utils.Logger;
import ch.papers.androidcommunicationbenchmark.utils.Preferences;

/**
 * Created by Alessandro De Carli (@a_d_c_) on 06/12/15.
 * Papers.ch
 * a.decarli@papers.ch
 */
public class NFCClient extends AbstractClient {
    private final static String TAG = "nfcclient";

    private static final byte[] CLA_INS_P1_P2 = { (byte)0x00, (byte)0xA4, (byte)0x04, (byte)0x00 };
    private static final byte[] AID_ANDROID = { (byte)0xF0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06 };


    private static final byte[] selectCommand = {
            (byte)0x00, // CLA
            (byte)0xA4, // INS
            (byte)0x04, // P1
            (byte)0x00, // P2
            (byte)0x0A, // LC
            (byte)0x01,(byte)0x02,(byte)0x03,(byte)0x04,(byte)0x05,(byte)0x06,(byte)0x07,(byte)0x08,(byte)0x09,(byte)0xFF, // AID
            (byte)0x7F  // LE
    };

    private final Activity activity;
    private final NfcAdapter nfcAdapter;

    public NFCClient(Activity activity) {
        super(activity);
        this.activity = activity;
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(this.activity);
    }

    @Override
    public boolean isSupported() {
        return (this.nfcAdapter != null);
    }

    @Override
    public void stop() {
        nfcAdapter.disableReaderMode(this.activity);
    }

    @Override
    public short getConnectionTechnology() {
        return BenchmarkResult.ConnectionTechonology.NFC;
    }

    @Override
    protected void startBenchmark() {

        if (!nfcAdapter.isEnabled())
        {
            this.activity.startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
            return;
        }


        nfcAdapter.enableReaderMode(this.activity, new NfcAdapter.ReaderCallback() {
                    @Override
                    public void onTagDiscovered(Tag tag) {
                        try {
                            getDiscoveryTimes().put(tag.toString(),System.currentTimeMillis());

                            IsoDep isoDep = IsoDep.get(tag);
                            isoDep.connect();

                            byte[] response = isoDep.transceive(createSelectAidApdu(AID_ANDROID));
                            getConnectTimes().put(tag.toString(),System.currentTimeMillis());
                            Logger.getInstance().log(TAG,"is connected "+isoDep.isConnected());
                            Logger.getInstance().log(TAG,"is extended apdu supported "+isoDep.isExtendedLengthApduSupported());

                            byte[] payload = new byte[Preferences.getInstance().getPayloadSize()];
                            Arrays.fill(payload, (byte) 1);

                            for (int i = 0; i < Preferences.getInstance().getCycleCount(); i++) {
                                int byteCounter = 0;
                                Logger.getInstance().log(TAG, "cycle: " + i);
                                while(byteCounter < payload.length){
                                    byte[] fragment = Arrays.copyOfRange(payload,byteCounter,byteCounter+isoDep.getMaxTransceiveLength());
                                    Logger.getInstance().log(TAG, "sending bytes: " + fragment.length);
                                    response = isoDep.transceive(fragment);
                                    Logger.getInstance().log(TAG, "receiving bytes: " + response.length);
                                    byteCounter += fragment.length;
                                }
                            }

                            isoDep.transceive("CLOSE".getBytes());
                            isoDep.close();
                            getTransferTimes().put(tag.toString(),System.currentTimeMillis());
                            endBenchmark(tag.toString());
                        } catch (IOException e) {
                            Logger.getInstance().log(TAG, e.getMessage());
                        }
                    }
                }, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null);
    }

    private byte[] createSelectAidApdu(byte[] aid) {
        byte[] result = new byte[6 + aid.length];
        System.arraycopy(CLA_INS_P1_P2, 0, result, 0, CLA_INS_P1_P2.length);
        result[4] = (byte)aid.length;
        System.arraycopy(aid, 0, result, 5, aid.length);
        result[result.length - 1] = 0;
        return result;
    }
}
