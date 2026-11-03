package net.kdt.pojavlaunch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point of the launcher. Asks for the permissions the launcher uses:
 *  - Storage / "All files access", so the launcher folder lives in the main storage
 *    (/storage/emulated/0/ShaderLauncher, next to Download, DCIM, Music...)
 *  - Microphone, for voice chat mods (e.g. Simple Voice Chat)
 * If the user refuses storage access, the launcher still works using its private folder.
 */
public class TestStorageActivity extends Activity {
    private static final int REQUEST_RUNTIME_PERMISSIONS = 1;
    private static final String PERMISSION_PREFS = "shader_permissions";
    private static final String KEY_SKIP_ALL_FILES = "skip_all_files_access";

    private boolean mWaitingForAllFilesAccess = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestRuntimePermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from the "All files access" settings screen
        if(mWaitingForAllFilesAccess) {
            mWaitingForAllFilesAccess = false;
            if(!Tools.hasPublicStorageAccess(this)) {
                Toast.makeText(this, R.string.storage_all_files_denied, Toast.LENGTH_LONG).show();
            }
            exit();
        }
    }

    private void requestRuntimePermissions() {
        List<String> missing = new ArrayList<>();
        if(Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 30) {
            addIfMissing(missing, Manifest.permission.READ_EXTERNAL_STORAGE);
            addIfMissing(missing, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if(Build.VERSION.SDK_INT >= 23) {
            addIfMissing(missing, Manifest.permission.RECORD_AUDIO);
        }

        if(missing.isEmpty()) {
            requestAllFilesAccess();
            return;
        }
        requestPermissions(missing.toArray(new String[0]), REQUEST_RUNTIME_PERMISSIONS);
    }

    private void addIfMissing(List<String> list, String permission) {
        if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            list.add(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode != REQUEST_RUNTIME_PERMISSIONS) return;
        if(Build.VERSION.SDK_INT < 30 && !Tools.hasPublicStorageAccess(this)) {
            Toast.makeText(this, R.string.storage_all_files_denied, Toast.LENGTH_LONG).show();
        }
        requestAllFilesAccess();
    }

    /** Android 11+: the main storage folder needs the special "All files access" permission */
    private void requestAllFilesAccess() {
        if(Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) {
            exit();
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE);
        if(prefs.getBoolean(KEY_SKIP_ALL_FILES, false)) {
            exit();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.storage_all_files_title)
                .setMessage(getString(R.string.storage_all_files_message, Tools.PUBLIC_FOLDER_NAME))
                .setCancelable(false)
                .setPositiveButton(R.string.storage_all_files_allow, (d, w) -> openAllFilesSettings())
                .setNegativeButton(R.string.storage_all_files_not_now, (d, w) -> exit())
                .setNeutralButton(R.string.storage_all_files_never, (d, w) -> {
                    prefs.edit().putBoolean(KEY_SKIP_ALL_FILES, true).apply();
                    exit();
                })
                .show();
    }

    private void openAllFilesSettings() {
        mWaitingForAllFilesAccess = true;
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (ActivityNotFoundException e2) {
                mWaitingForAllFilesAccess = false;
                exit();
            }
        }
    }

    private void exit() {
        if(isFinishing()) return;
        if(!Tools.checkStorageRoot(this)) {
            startActivity(new Intent(this, MissingStorageActivity.class));
            return;
        }
        //Initialize constants (implicitly) and preferences after we confirm that we have storage.
        LauncherPreferences.loadPreferences(this);
        AsyncAssetManager.unpackComponents(this);
        AsyncAssetManager.unpackSingleFiles(this);

        Intent intent =  new Intent(this, LauncherActivity.class);
        startActivity(intent);
        finish();
    }
}
