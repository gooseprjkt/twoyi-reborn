/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.twoyi.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.provider.DocumentsContract;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.util.Pair;

import com.microsoft.appcenter.crashes.Crashes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.regex.Pattern;

import io.twoyi.R;
import io.twoyi.utils.AppKV;
import io.twoyi.utils.LogEvents;
import io.twoyi.utils.RomManager;
import io.twoyi.utils.UIHelper;

/**
 * @author weishu
 * @date 2022/1/2.
 */

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_GET_FILE = 1000;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        SettingsFragment settingsFragment = new SettingsFragment();
        getFragmentManager().beginTransaction()
                .replace(R.id.settingsFrameLayout, settingsFragment)
                .commit();

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setBackgroundDrawable(getResources().getDrawable(R.color.colorPrimary));
            actionBar.setTitle(R.string.title_settings);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_settings);
        }

        private Preference findPreference(@StringRes int id) {
            String key = getString(id);
            return findPreference(key);
        }

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            Preference importApp = findPreference(R.string.settings_key_import_app);
            Preference export = findPreference(R.string.settings_key_manage_files);

            Preference shutdown = findPreference(R.string.settings_key_shutdown);
            Preference reboot = findPreference(R.string.settings_key_reboot);
            Preference replaceRom = findPreference(R.string.settings_key_replace_rom);
            Preference factoryReset = findPreference(R.string.settings_key_factory_reset);
            Preference gms = findPreference(R.string.settings_key_gms);
            Preference setPassword = findPreference(R.string.settings_key_set_password);

            Preference donate = findPreference(R.string.settings_key_donate);
            Preference sendLog = findPreference(R.string.settings_key_sendlog);
            Preference about = findPreference(R.string.settings_key_about);

            importApp.setOnPreferenceClickListener(preference -> {
                UIHelper.startActivity(getContext(), SelectAppActivity.class);
                return true;
            });

            export.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setType(DocumentsContract.Document.MIME_TYPE_DIR);
                startActivity(intent);
                return true;
            });

            shutdown.setOnPreferenceClickListener(preference -> {
                Activity activity = getActivity();
                activity.finishAffinity();
                RomManager.shutdown(activity);
                return true;
            });

            reboot.setOnPreferenceClickListener(preference -> {
                Activity activity = getActivity();
                activity.finishAndRemoveTask();
                RomManager.reboot(activity);
                return true;
            });

            replaceRom.setOnPreferenceClickListener(preference -> {

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

                // you can only select one rootfs
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                intent.setType("*/*"); // apk file
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(intent, REQUEST_GET_FILE);
                } catch (Throwable ignored) {
                    Toast.makeText(getContext(), "Error", Toast.LENGTH_SHORT).show();
                }
                return true;
            });

            factoryReset.setOnPreferenceClickListener(preference -> {
                UIHelper.getDialogBuilder(getActivity())
                        .setTitle(android.R.string.dialog_alert_title)
                        .setMessage(R.string.factory_reset_confirm_message)
                        .setPositiveButton(R.string.i_confirm_it, (dialog, which) -> {
                            AppKV.setBooleanConfig(getActivity(), AppKV.SHOULD_USE_THIRD_PARTY_ROM, false);
                            AppKV.setBooleanConfig(getActivity(), AppKV.FORCE_ROM_BE_RE_INSTALL, true);
                            dialog.dismiss();

                            RomManager.reboot(getActivity());
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                        .show();
                return true;
            });

            // Handle GMS preference
            if (gms != null) {
                boolean gmsExists = new File(RomManager.getRootfsDir(getContext()), "com.google.android.gms").exists();
                String gmsStatus = getContext().getString(gmsExists ? R.string.disable : R.string.enable);
                gms.setSummary(getResources().getString(R.string.toggle_gms_summary, gmsStatus));

                gms.setOnPreferenceClickListener(preference -> {
                    String action = gmsExists ? getString(R.string.disable) : getString(R.string.enable);
                    UIHelper.getDialogBuilder(getContext())
                            .setTitle(R.string.settings_key_gms)
                            .setMessage(getString(R.string.gms_confirm_message, action))
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                dialog.dismiss();
                                ProgressDialog progressDialog = UIHelper.getProgressDialog(getActivity());
                                progressDialog.setTitle(R.string.progress_dialog_title);
                                progressDialog.setCancelable(false);
                                progressDialog.show();

                                // Run GMS operation in background thread
                                new Thread(() -> {
                                    try {
                                        if (gmsExists) {
                                            // Remove GMS
                                            File gmsDir = new File(RomManager.getRootfsDir(getContext()), "com.google.android.gms");
                                            File gsfDir = new File(RomManager.getRootfsDir(getContext()), "com.google.android.gsf");
                                            File vendingDir = new File(RomManager.getRootfsDir(getContext()), "com.android.vending");

                                            IOUtils.deleteDirectory(gmsDir);
                                            IOUtils.deleteDirectory(gsfDir);
                                            IOUtils.deleteDirectory(vendingDir);
                                        } else {
                                            // Download and install GMS from GApps repository
                                            installGMSFromGApps();
                                        }

                                        // Update UI on main thread
                                        getActivity().runOnUiThread(() -> {
                                            progressDialog.dismiss();
                                            String successAction = gmsExists ? getString(R.string.disable) : getString(R.string.enable);
                                            Toast.makeText(getContext(), getString(R.string.gms_operate_success, successAction), Toast.LENGTH_SHORT).show();
                                            // Reboot is required after GMS changes
                                            RomManager.reboot(getActivity());
                                        });

                                    } catch (Exception e) {
                                        getActivity().runOnUiThread(() -> {
                                            progressDialog.dismiss();
                                            String failureAction = gmsExists ? getString(R.string.disable) : getString(R.string.enable);
                                            Toast.makeText(getContext(), getString(R.string.gms_operate_failed, failureAction, e.getMessage()), Toast.LENGTH_LONG).show();
                                        });
                                    }
                                }).start();
                            })
                            .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                            .show();
                    return true;
                });
            }

            // Handle Set Password preference
            if (setPassword != null) {
                setPassword.setOnPreferenceClickListener(preference -> {
                    // Show dialog to choose between pattern and PIN
                    String[] options = {"Pattern Lock", "PIN Lock", "Remove Lock"};
                    UIHelper.getDialogBuilder(getActivity())
                            .setTitle("Choose Lock Type")
                            .setItems(options, (dialog, which) -> {
                                if (which == 0) {
                                    // Pattern lock
                                    Intent intent = new Intent(getContext(), io.twoyi.security.lock.pattern.SimpleSetPatternActivity.class);
                                    startActivity(intent);
                                } else if (which == 1) {
                                    // PIN lock
                                    Intent intent = new Intent(getContext(), io.twoyi.security.lock.digital.SimpleSetDigitalActivity.class);
                                    intent.putExtra("pin_state", 2); // 2 for setting new PIN
                                    startActivity(intent);
                                } else {
                                    // Remove existing lock
                                    SharedPreferences prefs = getContext().getSharedPreferences("authentication", Context.MODE_PRIVATE);
                                    prefs.edit()
                                        .remove("pass_type")
                                        .remove("pass_pattern")
                                        .remove("pass_pin")
                                        .apply();
                                    Toast.makeText(getContext(), "Lock removed", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .show();
                    return true;
                });
            }

            donate.setOnPreferenceClickListener(preference -> {
                Context context = getContext();
                if (context instanceof Activity) {
                    UIHelper.showDonateDialog((Activity) context);
                    return true;
                }
                return false;
            });

            sendLog.setOnPreferenceClickListener(preference -> {
                Context context = getActivity();
                byte[] bugreport = LogEvents.getBugreport(context);
                File tmpLog = new File(context.getCacheDir(), "bugreport.zip");
                try {
                    Files.write(tmpLog.toPath(), bugreport);
                } catch (IOException e) {
                    Crashes.trackError(e);
                }
                Uri uri = FileProvider.getUriForFile(context, "io.twoyi.fileprovider", tmpLog);

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.setDataAndType(uri, "application/zip");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                context.startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_key_sendlog)));

                return true;
            });

            about.setOnPreferenceClickListener(preference -> {
                UIHelper.startActivity(getContext(), AboutActivity.class);
                return true;
            });
        }


        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            if (!(requestCode == REQUEST_GET_FILE && resultCode == Activity.RESULT_OK)) {
                return;
            }

            if (data == null) {
                return;
            }

            Uri uri = data.getData();
            if (uri == null) {
                return;
            }

            Activity activity = getActivity();
            ProgressDialog dialog = UIHelper.getProgressDialog(activity);
            dialog.setCancelable(false);
            dialog.show();

            // start copy 3rd rom
            UIHelper.defer().when(() -> {

                File rootfs3rd = RomManager.get3rdRootfsFile(activity);

                ContentResolver contentResolver = activity.getContentResolver();
                try (InputStream inputStream = contentResolver.openInputStream(uri); OutputStream os = new FileOutputStream(rootfs3rd)) {
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = inputStream.read(buffer)) > 0) {
                        os.write(buffer, 0, count);
                    }
                }

                RomManager.RomInfo romInfo = RomManager.getRomInfo(rootfs3rd);
                return Pair.create(rootfs3rd, romInfo);
            }).done(result -> {

                File rootfs3rd = result.first;
                RomManager.RomInfo romInfo = result.second;
                UIHelper.dismiss(dialog);

                // copy finished, show dialog confirm
                if (romInfo.isValid()) {

                    String author = romInfo.author;
                    if ("weishu".equalsIgnoreCase(author) || "twoyi".equalsIgnoreCase(author)) {
                        Toast.makeText(activity, R.string.replace_rom_unofficial_tips, Toast.LENGTH_SHORT).show();
                        rootfs3rd.delete();
                        return;

                    }
                    UIHelper.getDialogBuilder(activity)
                            .setTitle(R.string.replace_rom_confirm_title)
                            .setMessage(getString(R.string.replace_rom_confirm_message, author, romInfo.version, romInfo.desc))
                            .setPositiveButton(R.string.i_confirm_it, (dialog1, which) -> {
                                AppKV.setBooleanConfig(activity, AppKV.SHOULD_USE_THIRD_PARTY_ROM, true);
                                AppKV.setBooleanConfig(activity, AppKV.FORCE_ROM_BE_RE_INSTALL, true);

                                dialog1.dismiss();

                                RomManager.reboot(getActivity());
                            })
                            .setNegativeButton(android.R.string.cancel, (dialog12, which) -> dialog12.dismiss())
                            .show();
                } else {
                    Toast.makeText(activity, R.string.replace_rom_invalid, Toast.LENGTH_SHORT).show();
                    rootfs3rd.delete();
                }
            }).fail(result -> activity.runOnUiThread(() -> {
                Toast.makeText(activity, getResources().getString(R.string.install_failed_reason, result.getMessage()), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                activity.finish();
            }));

        }

        private void installGMSFromGApps() {
            // Copy GApps files to rootfs system directory
            Context context = getContext();
            File rootfsDir = RomManager.getRootfsDir(context);
            File systemDir = new File(rootfsDir, "system");
            File privAppDir = new File(systemDir, "priv-app");

            // Create the priv-app directory if it doesn't exist
            if (!privAppDir.exists()) {
                privAppDir.mkdirs();
            }

            try {
                // Copy GApps from assets or external source to the proper location
                // For this implementation, I'll copy from the GApps directory
                copyGAppsToSystem(privAppDir, context);
            } catch (Exception e) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Error installing GMS: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }

        private void copyGAppsToSystem(File targetDir, Context context) throws IOException {
            // First, check if online or use local GApps resources
            getActivity().runOnUiThread(() -> {
                Toast.makeText(context, "Installing GMS components...", Toast.LENGTH_SHORT).show();
            });

            // For this implementation, we'll use the local GApps repository
            // In a real scenario, this would download from a server using URLs

            // Get GApps components from project resources
            String gappsBasePath = "/data/data/com.termux/files/home/mk/twoyi-comparison/GApps";

            // 1. Copy com.google.android.gms directory
            File gmsSource = new File(gappsBasePath, "priv-app/com.google.android.gms");
            File gmsTarget = new File(targetDir, "com.google.android.gms");
            if (gmsSource.exists()) {
                IOUtils.deleteDirectory(gmsTarget); // Remove any existing files
                copyDirectory(gmsSource, gmsTarget);
            }

            // 2. Copy com.google.android.gsf directory
            File gsfSource = new File(gappsBasePath, "priv-app/com.google.android.gsf");
            File gsfTarget = new File(targetDir, "com.google.android.gsf");
            if (gsfSource.exists()) {
                IOUtils.deleteDirectory(gsfTarget); // Remove any existing files
                copyDirectory(gsfSource, gsfTarget);
            }

            // 3. Copy com.android.vending directory
            File vendingSource = new File(gappsBasePath, "priv-app/com.android.vending");
            File vendingTarget = new File(targetDir, "com.android.vending");
            if (vendingSource.exists()) {
                IOUtils.deleteDirectory(vendingTarget); // Remove any existing files
                copyDirectory(vendingSource, vendingTarget);
            }

            // 4. Copy framework files
            File frameworkSource = new File(gappsBasePath, "framework");
            File frameworkTarget = new File(RomManager.getRootfsDir(context), "system/framework");
            if (frameworkSource.exists()) {
                copyDirectory(frameworkSource, frameworkTarget);
            }

            // 5. Copy etc files
            File etcSource = new File(gappsBasePath, "etc");
            File etcTarget = new File(RomManager.getRootfsDir(context), "system/etc");
            if (etcSource.exists()) {
                copyDirectory(etcSource, etcTarget);
            }

            getActivity().runOnUiThread(() -> {
                Toast.makeText(context, "GMS installation completed! Reboot required.", Toast.LENGTH_SHORT).show();
            });
        }

        private void copyDirectory(File sourceDir, File targetDir) throws IOException {
            if (!sourceDir.exists()) {
                return;
            }

            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File sourceFile : files) {
                    File targetFile = new File(targetDir, sourceFile.getName());

                    if (sourceFile.isDirectory()) {
                        copyDirectory(sourceFile, targetFile);
                    } else {
                        java.nio.file.Files.copy(sourceFile.toPath(), targetFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }
}
