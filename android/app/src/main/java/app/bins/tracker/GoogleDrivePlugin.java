package app.bins.tracker;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;

import java.util.Collections;

/**
 * Gets a Google Drive access token with the phone's own Google account picker.
 * (Google doesn't allow its sign-in page inside an app's web view, so the app asks Android for it.)
 * Scope is drive.file: the app can only see files it created itself.
 */
@CapacitorPlugin(name = "GoogleDrive")
public class GoogleDrivePlugin extends Plugin {

    private static final String SCOPE = "https://www.googleapis.com/auth/drive.file";
    private ActivityResultLauncher<IntentSenderRequest> consentLauncher;
    private PluginCall pending;

    @Override
    public void load() {
        consentLauncher = getBridge().registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(), this::onConsent);
    }

    /** getToken({interactive}) -> {accessToken} */
    @PluginMethod
    public void getToken(PluginCall call) {
        boolean interactive = Boolean.TRUE.equals(call.getBoolean("interactive", true));
        Activity activity = getActivity();
        AuthorizationRequest req = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(new Scope(SCOPE)))
            .build();
        Identity.getAuthorizationClient(activity).authorize(req)
            .addOnSuccessListener(result -> {
                if (result.hasResolution()) {
                    if (!interactive) { call.reject("Not signed in to Google Drive", "NEEDS_SIGN_IN"); return; }
                    PendingIntent pi = result.getPendingIntent();
                    if (pi == null) { call.reject("Google sign-in unavailable"); return; }
                    if (pending != null) pending.reject("Cancelled");
                    pending = call;
                    consentLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
                } else {
                    resolve(call, result);
                }
            })
            .addOnFailureListener(e -> call.reject("Google sign-in failed: " + e.getMessage()));
    }

    private void onConsent(ActivityResult r) {
        PluginCall call = pending;
        pending = null;
        if (call == null) return;
        if (r.getResultCode() != Activity.RESULT_OK || r.getData() == null) { call.reject("Cancelled"); return; }
        try {
            AuthorizationResult result = Identity.getAuthorizationClient(getActivity())
                .getAuthorizationResultFromIntent(r.getData());
            resolve(call, result);
        } catch (Exception e) {
            call.reject("Google sign-in failed: " + e.getMessage());
        }
    }

    private void resolve(PluginCall call, AuthorizationResult result) {
        String token = result.getAccessToken();
        if (token == null) { call.reject("Google didn't return access"); return; }
        JSObject o = new JSObject();
        o.put("accessToken", token);
        call.resolve(o);
    }
}
