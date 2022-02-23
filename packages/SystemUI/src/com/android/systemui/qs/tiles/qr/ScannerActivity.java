/*
 * Copyright (C) 2022 ShapeShiftOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles.qr;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import com.android.systemui.R;

public class ScannerActivity extends AppCompatActivity implements View.OnClickListener {
    Button scanBtn;
    static ArrayList<String> history = new ArrayList<>();
    static ArrayAdapter arrayAdapter;
    static final int MENU_HISTORY_DELETE = Menu.FIRST + 1;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem clear = menu.add(0, MENU_HISTORY_DELETE, 0, R.string.delete_text_history)
                .setIcon(R.drawable.ic_delete);
        clear.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_HISTORY_DELETE:
                clearHistory();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_AppCompat);
        super.onCreate(savedInstanceState);
        int primaryColor = getResources().getColor(R.color.system_background_ssos);
        int primaryTextColor = getResources().getColor(R.color.system_text_color_ssos);

        Window window = ScannerActivity.this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setNavigationBarColor(primaryColor);
        window.setNavigationBarDividerColor(primaryColor);
        window.setStatusBarColor(primaryColor);

        setContentView(R.layout.scanner_activity);
        Spannable spannableTitle = new SpannableString(getString(R.string.qr_code_activity_title));
        spannableTitle.setSpan(new ForegroundColorSpan(primaryTextColor), 0, spannableTitle.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        getSupportActionBar().setTitle(spannableTitle);

        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources()
                            .getColor(R.color.system_background_ssos)));

        scanBtn = findViewById(R.id.scanBtn);
        scanBtn.setOnClickListener(this);

        ListView listView = findViewById(R.id.listOfHistory);
        SharedPreferences sharedPreferences = ScannerActivity.this.getSharedPreferences("com.android.systemui", ScannerActivity.this.MODE_PRIVATE);
        HashSet<String> set = (HashSet<String>) sharedPreferences.getStringSet("history", null);

        if (set == null) {
            history.add("History of scanned codes");
        } else {
            history = new ArrayList(set);
        }

        arrayAdapter = new ArrayAdapter(this, R.layout.simple_list_item_ssos, history);
        listView.setAdapter(arrayAdapter);

        attemptKeepListItems(7);

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                final int itemToDelete = i;
                new AlertDialog.Builder(ScannerActivity.this)
                        .setMessage("Do you want to delete this code from history?")
                        .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                history.remove(itemToDelete);
                                updateSharedPreferences();
                            }
                        }).setNegativeButton(getString(android.R.string.cancel), null).show();
                return true;
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final String itemToCopy = (String) listView.getItemAtPosition(i);
                copyToClipboard(itemToCopy);
            }
        });
    }

    private void clearHistory() {
        new AlertDialog.Builder(ScannerActivity.this)
                .setMessage("Do you want to clear history?")
                .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        attemptKeepListItems(0);
                    }
                }).setNegativeButton(getString(android.R.string.cancel), null).show();
    }

    private void attemptKeepListItems(int i) {
        if (ScannerActivity.arrayAdapter.getCount() > i) {
            new Thread() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            while (ScannerActivity.arrayAdapter.getCount() > i) {
                                history.remove(0);
                            }
                            updateSharedPreferences();
                        }
                    });
                }
            }.start();
        }
    }

    @Override
    public void onClick(View v) {
        barcodeLauncher.launch(new ScanOptions());
    }

    private void updateSharedPreferences() {
        ScannerActivity.arrayAdapter.notifyDataSetChanged();
        SharedPreferences sharedPreferences = ScannerActivity.this.getSharedPreferences("com.android.systemui", ScannerActivity.this.MODE_PRIVATE);
        HashSet<String> set = new HashSet(ScannerActivity.history);
        sharedPreferences.edit().putStringSet("history", set).apply();
    }

    private void copyToClipboard(String string) {
        ClipboardManager clipboard = (ClipboardManager) ScannerActivity.this.getSystemService(ScannerActivity.this.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("QR info", string);
        Toast.makeText(ScannerActivity.this, "Copied data: " + string, Toast.LENGTH_LONG).show();
        clipboard.setPrimaryClip(clip);
    }

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
        result -> {
            if (result.getContents() == null) {
                Intent originalIntent = result.getOriginalIntent();
                if (originalIntent == null) {
                    Toast.makeText(ScannerActivity.this, "Cancelled", Toast.LENGTH_SHORT).show();
                }
            } else {
                copyToClipboard(result.getContents());
                ScannerActivity.history.add(result.getContents());
                updateSharedPreferences();
                attemptKeepListItems(7);
            }
    });
}
