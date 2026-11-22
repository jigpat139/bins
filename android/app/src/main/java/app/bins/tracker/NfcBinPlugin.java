package app.bins.tracker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Parcelable;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Native NFC for "What's in your Storage?".
 * - While the app is open it owns the NFC reader, so tapping a bin tag opens that bin in the app.
 * - write({url}) waits for the next tag and writes the bin's link onto it.
 * - When the app is closed, tags whose link points at this app's website open the app (see AndroidManifest).
 */
@CapacitorPlugin(name = "NfcBin")
public class NfcBinPlugin extends Plugin implements NfcAdapter.ReaderCallback {

    private NfcAdapter adapter;
    private PluginCall pendingWrite;
    private boolean pendingErase;
    private String launchUrl;

    @Override
    public void load() {
        adapter = NfcAdapter.getDefaultAdapter(getContext());
        launchUrl = urlFromIntent(getActivity().getIntent());
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject r = new JSObject();
        r.put("available", adapter != null);
        r.put("enabled", adapter != null && adapter.isEnabled());
        call.resolve(r);
    }

    /** The bin link that launched the app, if it was opened by tapping a tag. Returned once. */
    @PluginMethod
    public void takeLaunchUrl(PluginCall call) {
        JSObject r = new JSObject();
        r.put("url", launchUrl);
        launchUrl = null;
        call.resolve(r);
    }

    @PluginMethod
    public void write(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) { call.reject("No link to write"); return; }
        if (adapter == null) { call.reject("This phone has no NFC"); return; }
        if (!adapter.isEnabled()) { call.reject("NFC is turned off. Turn it on in Settings."); return; }
        arm(call, false);
    }

    /** erase(): waits for the next tag and wipes it (writes an empty NDEF message). */
    @PluginMethod
    public void erase(PluginCall call) {
        if (adapter == null) { call.reject("This phone has no NFC"); return; }
        if (!adapter.isEnabled()) { call.reject("NFC is turned off. Turn it on in Settings."); return; }
        arm(call, true);
    }

    private void arm(PluginCall call, boolean erase) {
        if (pendingWrite != null) { pendingWrite.reject("Cancelled"); releaseWrite(); }
        call.setKeepAlive(true);
        pendingWrite = call;
        pendingErase = erase;
        startReader();
    }

    @PluginMethod
    public void cancelWrite(PluginCall call) {
        if (pendingWrite != null) { pendingWrite.reject("Cancelled"); releaseWrite(); }
        call.resolve();
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        getActivity().startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));
        call.resolve();
    }

    // ---- lifecycle: own the NFC reader while we're on screen ----
    @Override
    protected void handleOnResume() { startReader(); }

    @Override
    protected void handleOnPause() {
        if (adapter != null) adapter.disableReaderMode(getActivity());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        String url = urlFromIntent(intent);
        if (url != null) emitTag(url);
    }

    private void startReader() {
        Activity a = getActivity();
        if (adapter == null || a == null) return;
        int flags = NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                  | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V;
        a.runOnUiThread(() -> { try { adapter.enableReaderMode(a, this, flags, null); } catch (Exception ignored) {} });
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        PluginCall call = pendingWrite;
        if (call != null) { doWrite(tag, call); return; }
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) { emitTag(null); return; }
        NdefMessage msg = ndef.getCachedNdefMessage();
        String url = msg == null ? null : firstUri(msg);
        emitTag(url);
    }

    private void doWrite(Tag tag, PluginCall call) {
        String url = call.getString("url");
        NdefMessage msg = pendingErase
            ? new NdefMessage(new NdefRecord[]{ new NdefRecord(NdefRecord.TNF_EMPTY, null, null, null) })
            : bin(url);
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                try {
                    if (!ndef.isWritable()) throw new Exception("This tag is locked (read-only)");
                    if (ndef.getMaxSize() < msg.toByteArray().length) throw new Exception("Tag is too small for this link");
                    ndef.writeNdefMessage(msg);
                } finally { try { ndef.close(); } catch (Exception ignored) {} }
            } else {
                NdefFormatable f = NdefFormatable.get(tag);
                if (f == null) throw new Exception("This tag type can't hold a link");
                f.connect();
                try { f.format(msg); } finally { try { f.close(); } catch (Exception ignored) {} }
            }
            JSObject r = new JSObject();
            r.put("url", url);
            call.resolve(r);
        } catch (Exception e) {
            call.reject("Couldn't write the tag: " + e.getMessage() + ". Hold it still and try again.");
        }
        releaseWrite();
    }

    private NdefMessage bin(String url) {
        // 1st record: the bin's web link (iPhones and phones without the app open the website).
        // 2nd record: an Android Application Record, so Android phones with this app open the app, not Chrome.
        return new NdefMessage(new NdefRecord[]{
            NdefRecord.createUri(url),
            NdefRecord.createApplicationRecord(getContext().getPackageName())
        });
    }

    private void releaseWrite() {
        if (pendingWrite != null) {
            pendingWrite.setKeepAlive(false);
            getBridge().releaseCall(pendingWrite);
        }
        pendingWrite = null;
        pendingErase = false;
    }

    private void emitTag(String url) {
        JSObject r = new JSObject();
        r.put("url", url);
        notifyListeners("tag", r, true);
    }

    private static String firstUri(NdefMessage msg) {
        for (NdefRecord rec : msg.getRecords()) {
            Uri u = rec.toUri();
            if (u != null) return u.toString();
        }
        return null;
    }

    private static String urlFromIntent(Intent intent) {
        if (intent == null) return null;
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            Parcelable[] raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (raw != null && raw.length > 0) {
                String u = firstUri((NdefMessage) raw[0]);
                if (u != null) return u;
            }
        }
        Parcelable[] raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (raw != null && raw.length > 0) {
            String u = firstUri((NdefMessage) raw[0]);
            if (u != null) return u;
        }
        Uri data = intent.getData();
        return data == null ? null : data.toString();
    }
}
