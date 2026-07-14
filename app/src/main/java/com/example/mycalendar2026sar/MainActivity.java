package com.example.mycalendar2026sar;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.FileProvider;

import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private GridView calendarGrid;
    private TextView monthYearText;
    private EditText noteInput;
    private TextView remarkLabel;
    private LinearLayout dayRemarksContainer;
    private LinearLayout remarkHistoryContainer;
    private LinearLayout archiveHistoryContainer;
    private LinearLayout deletedHistoryContainer;
    private ScrollView mainScrollView;

    private Calendar calendar;
    private Calendar selectedDate;
    private String currentDateKey;
    private EditText currentDialogInput;
    private SharedPreferences sharedPreferences;
    private SharedPreferences archivePreferences;
    private SharedPreferences deletedPreferences;
    private SharedPreferences reminderPreferences;
    private SharedPreferences securityPrefs;
    private SharedPreferences colorPrefs;
    private SharedPreferences fontPrefs;
    private CalendarAdapter adapter;

    private String lastDeletedNote;
    private int lastDeletedIndex;
    private String lastDeletedDateKey;

    private final ActivityResultLauncher<Intent> voiceRecognitionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        if (currentDialogInput != null) {
                            String existingText = currentDialogInput.getText().toString();
                            currentDialogInput.setText(existingText.isEmpty() ? spokenText : existingText + " " + spokenText);
                        } else if (noteInput.hasFocus()) {
                            String existingText = noteInput.getText().toString();
                            noteInput.setText(existingText.isEmpty() ? spokenText : existingText + " " + spokenText);
                        } else {
                            showNewNoteDialog(spokenText);
                        }
                    }
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Notification permission denied. Reminders won't show.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Close App")
                        .setMessage("Are you sure you want to close the app?")
                        .setPositiveButton("Yes", (dialog, which) -> finish())
                        .setNegativeButton("No", null)
                        .show();
            }
        });

        calendarGrid = findViewById(R.id.calendarGrid);
        monthYearText = findViewById(R.id.monthYearText);
        noteInput = findViewById(R.id.noteInput);
        noteInput.setTextColor(Color.WHITE);
        remarkLabel = findViewById(R.id.remarkLabel);
        dayRemarksContainer = findViewById(R.id.dayRemarksContainer);
        remarkHistoryContainer = findViewById(R.id.remarkHistoryContainer);
        archiveHistoryContainer = findViewById(R.id.archiveHistoryContainer);
        deletedHistoryContainer = findViewById(R.id.deletedHistoryContainer);
        mainScrollView = findViewById(R.id.mainScrollView);

        Button saveNoteButton = findViewById(R.id.saveNoteButton);
        ImageButton prevMonth = findViewById(R.id.prevMonth);
        ImageButton nextMonth = findViewById(R.id.nextMonth);

        sharedPreferences = getSharedPreferences("CalendarNotes", Context.MODE_PRIVATE);
        archivePreferences = getSharedPreferences("ArchivedNotes", Context.MODE_PRIVATE);
        deletedPreferences = getSharedPreferences("DeletedNotes", Context.MODE_PRIVATE);
        reminderPreferences = getSharedPreferences("ReminderStatus", Context.MODE_PRIVATE);
        securityPrefs = getSharedPreferences("SecuritySettings", Context.MODE_PRIVATE);
        colorPrefs = getSharedPreferences("AppColors", Context.MODE_PRIVATE);
        fontPrefs = getSharedPreferences("AppFonts", Context.MODE_PRIVATE);

        // Hide folders initially
        archiveHistoryContainer.setVisibility(View.GONE);
        deletedHistoryContainer.setVisibility(View.GONE);

        // Always initialize to current day
        selectedDate = Calendar.getInstance();
        calendar = (Calendar) selectedDate.clone();
        calendar.set(Calendar.DAY_OF_MONTH, 1); // Start at beginning of current month

        refreshUIColors();

        updateCalendar();
        updateRemarkHistory();

        findViewById(R.id.remarkHistoryTitle).setOnClickListener(v -> {
            if (remarkHistoryContainer.getVisibility() == View.VISIBLE) {
                remarkHistoryContainer.setVisibility(View.GONE);
            } else {
                remarkHistoryContainer.setVisibility(View.VISIBLE);
                mainScrollView.post(() -> mainScrollView.smoothScrollTo(0, findViewById(R.id.remarkHistoryTitle).getTop()));
            }
        });

        findViewById(R.id.archiveHistoryTitle).setOnClickListener(v -> {
            if (archiveHistoryContainer.getVisibility() == View.VISIBLE) {
                archiveHistoryContainer.setVisibility(View.GONE);
            } else {
                archiveHistoryContainer.setVisibility(View.VISIBLE);
                mainScrollView.post(() -> mainScrollView.smoothScrollTo(0, findViewById(R.id.archiveHistoryTitle).getTop()));
            }
        });

        findViewById(R.id.deletedHistoryTitle).setOnClickListener(v -> {
            if (deletedHistoryContainer.getVisibility() == View.VISIBLE) {
                deletedHistoryContainer.setVisibility(View.GONE);
            } else {
                deletedHistoryContainer.setVisibility(View.VISIBLE);
                mainScrollView.post(() -> mainScrollView.smoothScrollTo(0, findViewById(R.id.deletedHistoryTitle).getTop()));
            }
        });
        
        saveNoteButton.setOnClickListener(v -> saveNote());

        findViewById(R.id.voiceNoteButton).setOnClickListener(v -> {
            noteInput.requestFocus();
            startVoiceRecognition();
        });

        findViewById(R.id.mainMenuButton).setOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
            popup.getMenu().add("New Note");
            popup.getMenu().add("New Voice Note");
            popup.getMenu().add("New Sticky Note");
            popup.getMenu().add("Secure Box");
            popup.getMenu().add("Change Password");
            popup.getMenu().add("Notification Settings");
            popup.getMenu().add("Change Colors");
            popup.getMenu().add("Change Font");
            popup.getMenu().add("Print");
            popup.getMenu().add("About");
            popup.getMenu().add("Exit");

            popup.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if (title.equals("New Note")) {
                    currentDialogInput = null;
                    showNewNoteDialog("");
                } else if (title.equals("New Voice Note")) {
                    currentDialogInput = null;
                    startVoiceRecognition();
                } else if (title.equals("New Sticky Note")) {
                    launchSecureBox(true);
                } else if (title.equals("Secure Box")) {
                    launchSecureBox(false);
                } else if (title.equals("Change Password")) {
                    showChangePasswordDialog();
                } else if (title.equals("Notification Settings")) {
                    findViewById(R.id.notificationSettingsButton).performClick();
                } else if (title.equals("Change Colors")) {
                    showChangeColorsDialog();
                } else if (title.equals("Change Font")) {
                    showFontDialog();
                } else if (title.equals("Print")) {
                    showPrintDialog();
                } else if (title.equals("About")) {
                    new AlertDialog.Builder(this)
                            .setTitle("About SAR Calendar")
                            .setMessage("SAR Calendar 2026\nVersion 1.0\nCreated with care.")
                            .setPositiveButton("OK", null)
                            .show();
                } else if (title.equals("Exit")) {
                    getOnBackPressedDispatcher().onBackPressed();
                }
                return true;
            });
            popup.show();
        });

        findViewById(R.id.secureBoxButton).setOnClickListener(v -> launchSecureBox(false));

        findViewById(R.id.notificationSettingsButton).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, "calendar_reminder_channel");
            try {
                startActivity(intent);
            } catch (Exception e) {
                Intent fallback = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                fallback.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(fallback);
            }
        });

        prevMonth.setOnClickListener(v -> {
            calendar.add(Calendar.MONTH, -1);
            updateCalendar();
        });
        nextMonth.setOnClickListener(v -> {
            calendar.add(Calendar.MONTH, 1);
            updateCalendar();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Automatically scan and archive past notes every time the user enters the app
        archiveAllPastNotesSilent();
        autoCleanArchive();
        updateRemarkHistory();
        updateAllWidgets();
    }

    private void autoCleanArchive() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Calendar now = Calendar.getInstance();
        
        Calendar archiveThresholdCal = (Calendar) now.clone();
        archiveThresholdCal.add(Calendar.MONTH, -1);
        Date archiveThreshold = archiveThresholdCal.getTime();

        Calendar deleteThresholdCal = (Calendar) now.clone();
        deleteThresholdCal.add(Calendar.MONTH, -2);
        Date deleteThreshold = deleteThresholdCal.getTime();

        Map<String, ?> allArchived = archivePreferences.getAll();
        for (Map.Entry<String, ?> entry : allArchived.entrySet()) {
            String dateKey = entry.getKey();
            try {
                Date noteDate = sdf.parse(dateKey);
                if (noteDate != null && noteDate.before(archiveThreshold)) {
                    Object valObj = entry.getValue();
                    String value = valObj != null ? valObj.toString() : "";
                    if (!value.isEmpty()) {
                        String existingDeleted = deletedPreferences.getString(dateKey, "");
                        String updatedDeleted = existingDeleted.isEmpty() ? value : existingDeleted + "\n" + value;
                        deletedPreferences.edit().putString(dateKey, updatedDeleted).apply();
                        archivePreferences.edit().remove(dateKey).apply();
                    }
                }
            } catch (Exception ignored) {}
        }

        Map<String, ?> allDeleted = deletedPreferences.getAll();
        for (Map.Entry<String, ?> entry : allDeleted.entrySet()) {
            String dateKey = entry.getKey();
            try {
                Date noteDate = sdf.parse(dateKey);
                if (noteDate != null && noteDate.before(deleteThreshold)) {
                    deletedPreferences.edit().remove(dateKey).apply();
                }
            } catch (Exception ignored) {}
        }
    }

    private void updateCalendar() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthYearText.setText(sdf.format(calendar.getTime()));

        ArrayList<Date> days = new ArrayList<>();
        Calendar tempCal = (Calendar) calendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        
        int firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) - 1;
        tempCal.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek);

        while (days.size() < 42) {
            days.add(tempCal.getTime());
            tempCal.add(Calendar.DAY_OF_MONTH, 1);
        }

        adapter = new CalendarAdapter(this, days, calendar);
        calendarGrid.setAdapter(adapter);
        
        updateDateInfo(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH));
    }

    private void updateDateInfo(int year, int month, int dayOfMonth) {
        selectedDate.set(year, month, dayOfMonth);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        currentDateKey = sdf.format(selectedDate.getTime());

        SimpleDateFormat displaySdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        remarkLabel.setText(getString(R.string.remark_for, displaySdf.format(selectedDate.getTime())));

        loadRemarksForSelectedDate();
        noteInput.setText("");
    }

    private void loadRemarksForSelectedDate() {
        dayRemarksContainer.removeAllViews();
        
        String savedRemarks = sharedPreferences.getString(currentDateKey, "");

        if (!savedRemarks.isEmpty()) {
            String[] remarksArray = savedRemarks.split("\n");
            for (int i = 0; i < remarksArray.length; i++) {
                addRemarkView(remarksArray[i], i, sharedPreferences);
            }
        } else {
            TextView noRemarks = new TextView(this);
            noRemarks.setText(getString(R.string.no_notes_day));
            applyFontSettings(noRemarks, 16);
            noRemarks.setTextColor(Color.WHITE);
            dayRemarksContainer.addView(noRemarks);
        }
    }

    private void addRemarkView(String remarkText, int index, SharedPreferences sourcePrefs) {
        LinearLayout horizontalLayout = new LinearLayout(this);
        horizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
        horizontalLayout.setGravity(Gravity.CENTER_VERTICAL);
        horizontalLayout.setPadding(0, 4, 0, 4);

        TextView textView = createRemarkTextView(remarkText, index, sourcePrefs);
        horizontalLayout.addView(textView);

        int iconSize = (int) (23 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        btnParams.setMargins(4, 0, 4, 0);

        ImageButton alarmButton = createActionButton(android.R.drawable.ic_lock_idle_alarm, btnParams, v -> manageReminder(remarkText));
        
        String reminderKey = currentDateKey + "_" + remarkText;
        boolean hasReminder = reminderPreferences.contains(reminderKey);
        if (hasReminder) {
            alarmButton.setImageTintList(ColorStateList.valueOf(colorPrefs.getInt("color_main_theme", getColor(R.color.light_green))));
        } else {
            alarmButton.setImageTintList(null);
        }
        
        horizontalLayout.addView(alarmButton);

        horizontalLayout.addView(createActionButton(android.R.drawable.ic_menu_edit, btnParams, v -> showEditDialog(remarkText, index, sourcePrefs)));

        horizontalLayout.addView(createActionButton(android.R.drawable.ic_menu_share, btnParams, v -> {
            String[] options = {"Share as Text", "Share as .ics File"};
            new AlertDialog.Builder(this)
                    .setTitle("Share Note")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            String noteBody = getNoteBody(remarkText);
                            String shareText = "SAR CALENDAR REMINDER:\n" + noteBody + "\nDate: " + currentDateKey;
                            
                            String reminderKeyForShare = currentDateKey + "_" + remarkText;
                            String savedTime = reminderPreferences.getString(reminderKeyForShare, null);
                            String gCalUrl = getGoogleCalendarUrl(noteBody, currentDateKey, savedTime);
                            
                            if (!gCalUrl.isEmpty()) {
                                shareText += "\n\nAdd to Google Calendar:\n" + gCalUrl;
                            }
                            
                            if (hasReminder) {
                                shareText += "\n\n(Check SAR Calendar to sync this reminder)";
                            }
                            Intent sendIntent = new Intent(Intent.ACTION_SEND);
                            sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                            sendIntent.setType("text/plain");
                            startActivity(Intent.createChooser(sendIntent, "Share Note via"));
                        } else {
                            shareNoteAsIcs(remarkText, currentDateKey);
                        }
                    })
                    .show();
        }));

        ImageButton archiveButton = createArchiveButton(btnParams, index, sourcePrefs);
        horizontalLayout.addView(archiveButton);

        horizontalLayout.addView(createActionButton(android.R.drawable.ic_menu_delete, btnParams, v -> deleteRemark(index, sourcePrefs)));
        
        dayRemarksContainer.addView(horizontalLayout);
    }

    private void shareNoteAsIcs(String noteText, String dateKey) {
        String noteBody = getNoteBody(noteText);
        String reminderKey = dateKey + "_" + noteText;
        String savedTime = reminderPreferences.getString(reminderKey, null);

        String icsContent = "BEGIN:VCALENDAR\n" +
                "VERSION:2.0\n" +
                "PRODID:-//SAR Calendar//EN\n" +
                "BEGIN:VEVENT\n" +
                "SUMMARY:" + noteBody + "\n";
        
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = sdf.parse(dateKey);
            if (date != null) {
                if (savedTime != null) {
                    SimpleDateFormat reminderSdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                    Calendar cal = Calendar.getInstance();
                    boolean parsed = false;
                    try {
                        Date fullDate = reminderSdf.parse(savedTime);
                        if (fullDate != null) {
                            cal.setTime(fullDate);
                            parsed = true;
                        }
                    } catch (Exception e) {
                        if (savedTime.contains(":")) {
                            String[] parts = savedTime.split(":");
                            cal.setTime(date);
                            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                            cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
                            cal.set(Calendar.SECOND, 0);
                            parsed = true;
                        }
                    }

                    if (parsed) {
                        SimpleDateFormat icsSdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault());
                        String dateStr = icsSdf.format(cal.getTime());
                        icsContent += "DTSTART:" + dateStr + "\n";
                        cal.add(Calendar.MINUTE, 30);
                        String endDateStr = icsSdf.format(cal.getTime());
                        icsContent += "DTEND:" + endDateStr + "\n";
                    } else {
                        addDefaultIcsDates(icsContent, date);
                    }
                } else {
                    icsContent = addDefaultIcsDates(icsContent, date);
                }
            }
        } catch (Exception ignored) {}
        
        icsContent += "DESCRIPTION:SAR Calendar Note\\n\\nAdd to Google Calendar:\\n" + getGoogleCalendarUrl(noteBody, dateKey, savedTime) + "\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";

        try {
            File cachePath = new File(getCacheDir(), "shared_notes");
            if (!cachePath.exists() && !cachePath.mkdirs()) return;
            File icsFile = new File(cachePath, "note_reminder.ics");
            FileOutputStream stream = new FileOutputStream(icsFile);
            stream.write(icsContent.getBytes());
            stream.close();

            android.net.Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", icsFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/calendar");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Note via"));
        } catch (Exception e) {
            Toast.makeText(this, "Error creating .ics file", Toast.LENGTH_SHORT).show();
        }
    }

    private String getGoogleCalendarUrl(String title, String dateKey, String savedTime) {
        String baseUrl = "https://www.google.com/calendar/render?action=TEMPLATE";
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.name());
            String dates = "";
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = sdf.parse(dateKey);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                if (savedTime != null) {
                    SimpleDateFormat reminderSdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                    try {
                        Date fullDate = reminderSdf.parse(savedTime);
                        if (fullDate != null) cal.setTime(fullDate);
                    } catch (Exception e) {
                        if (savedTime.contains(":")) {
                            String[] parts = savedTime.split(":");
                            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                            cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
                        }
                    }
                    SimpleDateFormat urlSdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault());
                    String start = urlSdf.format(cal.getTime());
                    cal.add(Calendar.MINUTE, 30);
                    String end = urlSdf.format(cal.getTime());
                    dates = start + "/" + end;
                } else {
                    SimpleDateFormat urlSdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                    String start = urlSdf.format(cal.getTime());
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                    String end = urlSdf.format(cal.getTime());
                    dates = start + "/" + end;
                }
            }
            return baseUrl + "&text=" + encodedTitle + "&dates=" + dates + "&details=Created+via+SAR+Calendar";
        } catch (Exception e) {
            return "";
        }
    }

    private String getNoteBody(String text) {
        if (text.startsWith("• ") || text.startsWith("□ ") || text.startsWith("▣ ")) {
            return text.substring(2);
        }
        return text;
    }

    private void toggleNoteFinished(int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(currentDateKey, "");
        if (currentText.isEmpty()) return;

        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            String note = remarksList.get(index);
            if (note.startsWith("□ ")) {
                remarksList.set(index, "▣ " + note.substring(2));
            } else {
                remarksList.set(index, "□ " + getNoteBody(note));
            }

            String updatedRemarks = String.join("\n", remarksList);
            sourcePrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            updateAllWidgets();
        }
    }

    private TextView createRemarkTextView(String remarkText, int index, SharedPreferences sourcePrefs) {
        TextView textView = new TextView(this);
        textView.setText(remarkText);
        applyFontSettings(textView, 16);
        if (remarkText.startsWith("▣ ")) {
            textView.setTextColor(colorPrefs.getInt("color_note_checked", Color.GREEN));
        } else {
            textView.setTextColor(colorPrefs.getInt("color_note_text", Color.WHITE));
        }
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        textView.setOnClickListener(v -> toggleNoteFinished(index, sourcePrefs));

        textView.setOnLongClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            String textToCopy = getNoteBody(remarkText);
            android.content.ClipData clip = android.content.ClipData.newPlainText("SAR Note", textToCopy);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Note copied to clipboard", Toast.LENGTH_SHORT).show();
            return true;
        });
        return textView;
    }

    private ImageButton createActionButton(int iconRes, LinearLayout.LayoutParams params, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setLayoutParams(params);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(4, 4, 4, 4);
        button.setOnClickListener(listener);
        return button;
    }

    private ImageButton createArchiveButton(LinearLayout.LayoutParams params, int index, SharedPreferences sourcePrefs) {
        ImageButton archiveButton = new ImageButton(this);
        archiveButton.setBackgroundColor(Color.TRANSPARENT);
        archiveButton.setLayoutParams(params);
        archiveButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        archiveButton.setPadding(4, 4, 4, 4);
        if (sourcePrefs == archivePreferences) {
            archiveButton.setImageResource(android.R.drawable.ic_menu_revert);
            archiveButton.setOnClickListener(v -> unarchiveNote(index));
        } else if (sourcePrefs == deletedPreferences) {
            archiveButton.setImageResource(android.R.drawable.ic_menu_revert);
            archiveButton.setOnClickListener(v -> restoreFromTrash(index));
        } else {
            archiveButton.setImageResource(android.R.drawable.ic_menu_save);
            archiveButton.setOnClickListener(v -> archiveNote(index));
        }
        return archiveButton;
    }

    private void restoreFromTrash(int index) {
        restoreSingleNote(currentDateKey, index, deletedPreferences);
    }

    private void restoreSingleNote(String dateKey, int index, SharedPreferences sourcePrefs) {
        String currentNotes = sourcePrefs.getString(dateKey, "");
        if (currentNotes.isEmpty()) return;
        List<String> notesList = new ArrayList<>(Arrays.asList(currentNotes.split("\n")));
        if (index >= 0 && index < notesList.size()) {
            String noteToRestore = notesList.remove(index);
            String savedRemarks = sharedPreferences.getString(dateKey, "");
            String updatedSaved = savedRemarks.isEmpty() ? noteToRestore : savedRemarks + "\n" + noteToRestore;
            sharedPreferences.edit().putString(dateKey, updatedSaved).apply();
            String updatedSource = String.join("\n", notesList);
            if (updatedSource.isEmpty()) sourcePrefs.edit().remove(dateKey).apply();
            else sourcePrefs.edit().putString(dateKey, updatedSource).apply();
            updateRemarkHistory();
            if (Objects.equals(dateKey, currentDateKey)) loadRemarksForSelectedDate();
            adapter.notifyDataSetChanged();
            updateAllWidgets();
            Toast.makeText(this, "Note restored to personal notes", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteSingleNote(String dateKey, int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(dateKey, "");
        if (currentText.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            String noteToDelete = remarksList.remove(index);
            String currentDeleted = deletedPreferences.getString(dateKey, "");
            String updatedDeleted = currentDeleted.isEmpty() ? noteToDelete : currentDeleted + "\n" + noteToDelete;
            deletedPreferences.edit().putString(dateKey, updatedDeleted).apply();
            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) sourcePrefs.edit().remove(dateKey).apply();
            else sourcePrefs.edit().putString(dateKey, updatedRemarks).apply();
            updateRemarkHistory();
            if (Objects.equals(dateKey, currentDateKey)) loadRemarksForSelectedDate();
            adapter.notifyDataSetChanged();
            deletedHistoryContainer.setVisibility(View.VISIBLE);
            updateAllWidgets();
            Toast.makeText(this, "Note moved to trash", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteSingleNotePermanently(String dateKey, int index, SharedPreferences sourcePrefs) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Note")
                .setMessage("Permanently delete this note?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    String currentNotes = sourcePrefs.getString(dateKey, "");
                    if (currentNotes.isEmpty()) return;
                    List<String> notesList = new ArrayList<>(Arrays.asList(currentNotes.split("\n")));
                    if (index >= 0 && index < notesList.size()) {
                        notesList.remove(index);
                        String updatedSource = String.join("\n", notesList);
                        if (updatedSource.isEmpty()) sourcePrefs.edit().remove(dateKey).apply();
                        else sourcePrefs.edit().putString(dateKey, updatedSource).apply();
                        updateRemarkHistory();
                        if (Objects.equals(dateKey, currentDateKey)) loadRemarksForSelectedDate();
                        adapter.notifyDataSetChanged();
                        updateAllWidgets();
                        Toast.makeText(this, "Permanently Deleted", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("No", null).show();
    }

    private void unarchiveNote(int index) {
        String archivedRemarks = archivePreferences.getString(currentDateKey, "");
        if (archivedRemarks.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(archivedRemarks.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            String noteToRestore = remarksList.remove(index);
            String savedRemarks = sharedPreferences.getString(currentDateKey, "");
            String updatedSaved = savedRemarks.isEmpty() ? noteToRestore : savedRemarks + "\n" + noteToRestore;
            sharedPreferences.edit().putString(currentDateKey, updatedSaved).apply();
            String updatedArchived = String.join("\n", remarksList);
            if (updatedArchived.isEmpty()) archivePreferences.edit().remove(currentDateKey).apply();
            else archivePreferences.edit().putString(currentDateKey, updatedArchived).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            updateAllWidgets();
            Toast.makeText(this, "Note restored from archive", Toast.LENGTH_SHORT).show();
        }
    }

    private String addDefaultIcsDates(String icsContent, Date date) {
        SimpleDateFormat icsSdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String dateStr = icsSdf.format(date);
        return icsContent + "DTSTART;VALUE=DATE:" + dateStr + "\n" +
                "DTEND;VALUE=DATE:" + dateStr + "\n";
    }

    private void manageReminder(String noteText) {
        String reminderKey = currentDateKey + "_" + noteText;
        String savedValue = reminderPreferences.getString(reminderKey, null);
        if (savedValue != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Manage Reminder")
                    .setMessage("Currently set for: " + savedValue)
                    .setPositiveButton("Edit", (dialog, which) -> showReminderPicker(noteText))
                    .setNegativeButton("Delete", (dialog, which) -> deleteReminder(noteText))
                    .setNeutralButton("Cancel", null).show();
        } else {
            showReminderPicker(noteText);
        }
    }

    private void showReminderPicker(String noteText) {
        Calendar current = Calendar.getInstance();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date noteDate = sdf.parse(currentDateKey);
            if (noteDate != null) {
                current.setTime(noteDate);
                Calendar now = Calendar.getInstance();
                if (current.before(now)) {
                    current.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
                    current.set(Calendar.MINUTE, now.get(Calendar.MINUTE));
                    current.add(Calendar.MINUTE, 5);
                }
            }
        } catch (Exception ignored) {}
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar pickedDate = Calendar.getInstance();
            pickedDate.set(year, month, day);
            new TimePickerDialog(this, (v, hour, minute) -> {
                pickedDate.set(Calendar.HOUR_OF_DAY, hour);
                pickedDate.set(Calendar.MINUTE, minute);
                pickedDate.set(Calendar.SECOND, 0);
                setReminder(pickedDate, noteText);
            }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show();
        }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void setReminder(Calendar reminderTime, String noteText) {
        if (reminderTime.before(Calendar.getInstance())) {
            Toast.makeText(this, "Cannot set reminder in the past!", Toast.LENGTH_SHORT).show();
            return;
        }
        String reminderKey = currentDateKey + "_" + noteText;
        int requestCode = reminderKey.hashCode();
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("noteText", noteText);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime.getTimeInMillis(), pendingIntent);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        String timeValue = sdf.format(reminderTime.getTime());
        reminderPreferences.edit().putString(reminderKey, timeValue).apply();
        loadRemarksForSelectedDate();
        Toast.makeText(this, "Reminder set for " + timeValue, Toast.LENGTH_SHORT).show();
    }

    private void deleteReminder(String noteText) {
        String reminderKey = currentDateKey + "_" + noteText;
        int requestCode = reminderKey.hashCode();
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
        reminderPreferences.edit().remove(reminderKey).apply();
        loadRemarksForSelectedDate();
        Toast.makeText(this, "Reminder deleted", Toast.LENGTH_SHORT).show();
    }

    private void showEditDialog(String currentText, int index, SharedPreferences sourcePrefs) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Note");
        final EditText input = new EditText(this);
        input.setTextColor(Color.WHITE);
        input.setText(getNoteBody(currentText));
        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String updatedText = input.getText().toString().trim();
            if (!updatedText.isEmpty()) updateRemark(updatedText, index, sourcePrefs);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel()).show();
    }

    private void updateRemark(String newText, int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(currentDateKey, "");
        if (currentText.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            remarksList.set(index, "□ " + newText);
            String updatedRemarks = String.join("\n", remarksList);
            sourcePrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            updateAllWidgets();
        }
    }

    private void archiveNote(int index) {
        new AlertDialog.Builder(this).setTitle("Archive Note").setMessage("Are you sure you want to archive this note?")
                .setPositiveButton("Yes", (dialog, which) -> performArchiveNote(index))
                .setNegativeButton("No", null).show();
    }

    private void performArchiveNote(int index) {
        String savedRemarks = sharedPreferences.getString(currentDateKey, "");
        if (savedRemarks.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(savedRemarks.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            String noteToArchive = remarksList.remove(index);
            String archivedRemarks = archivePreferences.getString(currentDateKey, "");
            String updatedArchived = archivedRemarks.isEmpty() ? noteToArchive : archivedRemarks + "\n" + noteToArchive;
            archivePreferences.edit().putString(currentDateKey, updatedArchived).apply();
            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) sharedPreferences.edit().remove(currentDateKey).apply();
            else sharedPreferences.edit().putString(currentDateKey, updatedRemarks).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            archiveHistoryContainer.setVisibility(View.VISIBLE);
            updateAllWidgets();
            Toast.makeText(this, R.string.archive_success, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteRemark(int index, SharedPreferences sourcePrefs) {
        if (sourcePrefs == deletedPreferences) {
            new AlertDialog.Builder(this).setTitle("Delete Note").setMessage("Permanently delete this note?")
                    .setPositiveButton("Yes", (dialog, which) -> performPermanentDelete(index, sourcePrefs))
                    .setNegativeButton("No", null).show();
            return;
        }
        String[] options = {"Move to Trash", "Delete Permanently"};
        new AlertDialog.Builder(this).setTitle("Delete Note").setItems(options, (dialog, which) -> {
            if (which == 0) performDeleteRemark(index, sourcePrefs);
            else performPermanentDelete(index, sourcePrefs);
        }).setNegativeButton("Cancel", null).show();
    }

    private void performPermanentDelete(int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(currentDateKey, "");
        if (currentText.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            remarksList.remove(index);
            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) sourcePrefs.edit().remove(currentDateKey).apply();
            else sourcePrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            updateAllWidgets();
            Toast.makeText(this, "Permanently Deleted", Toast.LENGTH_SHORT).show();
        }
    }

    private void performDeleteRemark(int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(currentDateKey, "");
        if (currentText.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            lastDeletedNote = remarksList.get(index);
            lastDeletedIndex = index;
            lastDeletedDateKey = currentDateKey;
            remarksList.remove(index);
            String currentDeleted = deletedPreferences.getString(currentDateKey, "");
            String updatedDeleted = currentDeleted.isEmpty() ? lastDeletedNote : currentDeleted + "\n" + lastDeletedNote;
            deletedPreferences.edit().putString(currentDateKey, updatedDeleted).apply();
            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) sourcePrefs.edit().remove(currentDateKey).apply();
            else sourcePrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            deletedHistoryContainer.setVisibility(View.VISIBLE);
            showUndoSnackbar();
            updateAllWidgets();
        }
    }

    private void showUndoSnackbar() {
        Snackbar.make(findViewById(R.id.main), "Note moved to trash", Snackbar.LENGTH_LONG)
                .setAction("UNDO", v -> undoDelete())
                .show();
    }

    private void undoDelete() {
        if (lastDeletedNote == null || lastDeletedDateKey == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayKey = sdf.format(Calendar.getInstance().getTime());
        SharedPreferences targetPrefs = sharedPreferences;
        try {
            Date noteDate = sdf.parse(lastDeletedDateKey);
            Date today = sdf.parse(todayKey);
            if (noteDate != null && today != null && noteDate.before(today)) targetPrefs = archivePreferences;
        } catch (Exception ignored) {}
        String currentRemarks = targetPrefs.getString(lastDeletedDateKey, "");
        List<String> remarksList = new ArrayList<>();
        if (!currentRemarks.isEmpty()) remarksList.addAll(Arrays.asList(currentRemarks.split("\n")));
        if (lastDeletedIndex >= 0 && lastDeletedIndex <= remarksList.size()) remarksList.add(lastDeletedIndex, lastDeletedNote);
        else remarksList.add(lastDeletedNote);
        String updatedRemarks = String.join("\n", remarksList);
        targetPrefs.edit().putString(lastDeletedDateKey, updatedRemarks).apply();
        if (Objects.equals(lastDeletedDateKey, currentDateKey)) loadRemarksForSelectedDate();
        String currentDeleted = deletedPreferences.getString(lastDeletedDateKey, "");
        if (!currentDeleted.isEmpty()) {
            List<String> deletedList = new ArrayList<>(Arrays.asList(currentDeleted.split("\n")));
            deletedList.remove(lastDeletedNote);
            if (deletedList.isEmpty()) deletedPreferences.edit().remove(lastDeletedDateKey).apply();
            else deletedPreferences.edit().putString(lastDeletedDateKey, String.join("\n", deletedList)).apply();
        }
        updateRemarkHistory();
        adapter.notifyDataSetChanged();
        updateAllWidgets();
        lastDeletedNote = null;
        lastDeletedDateKey = null;
        Toast.makeText(this, "Restored", Toast.LENGTH_SHORT).show();
    }

    private void saveNote() {
        String newText = noteInput.getText().toString().trim();
        if (newText.isEmpty()) {
            Toast.makeText(this, "Please enter a note", Toast.LENGTH_SHORT).show();
            return;
        }
        saveNoteForDate(currentDateKey, newText);
        noteInput.setText("");
    }

    private void saveNoteForDate(String dateKey, String noteText) {
        String entry = "□ " + noteText;
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayKey = sdf.format(Calendar.getInstance().getTime());
        SharedPreferences targetPrefs = sharedPreferences;
        try {
            Date selected = sdf.parse(dateKey);
            Date today = sdf.parse(todayKey);
            if (selected != null && today != null && selected.before(today)) targetPrefs = archivePreferences;
        } catch (Exception ignored) {}
        String existingRemarks = targetPrefs.getString(dateKey, "");
        String updatedRemarks = existingRemarks.isEmpty() ? entry : existingRemarks + "\n" + entry;
        targetPrefs.edit().putString(dateKey, updatedRemarks).apply();
        if (Objects.equals(dateKey, currentDateKey)) loadRemarksForSelectedDate();
        updateRemarkHistory();
        adapter.notifyDataSetChanged();
        updateAllWidgets();
        Toast.makeText(this, "Note added!", Toast.LENGTH_SHORT).show();
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your note...");
        try {
            voiceRecognitionLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not supported", Toast.LENGTH_SHORT).show();
        }
    }

    private void showNewNoteDialog(String initialText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Note");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final Calendar noteDate = Calendar.getInstance();
        final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        final Button dateButton = new Button(this);
        dateButton.setText("Date: " + sdf.format(noteDate.getTime()));
        applyFontSettings(dateButton, 14);
        dateButton.setBackgroundTintList(ColorStateList.valueOf(colorPrefs.getInt("color_main_theme", getColor(R.color.light_green))));
        dateButton.setTextColor(Color.WHITE);
        dateButton.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                noteDate.set(year, month, dayOfMonth);
                dateButton.setText("Date: " + sdf.format(noteDate.getTime()));
            }, noteDate.get(Calendar.YEAR), noteDate.get(Calendar.MONTH), noteDate.get(Calendar.DAY_OF_MONTH)).show();
        });
        layout.addView(dateButton);

        final EditText input = new EditText(this);
        input.setHint("Enter note here...");
        input.setText(initialText);
        applyFontSettings(input, 18);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        layout.addView(input);

        final ImageButton voiceBtn = new ImageButton(this);
        voiceBtn.setImageResource(android.R.drawable.ic_btn_speak_now);
        voiceBtn.setBackgroundColor(Color.TRANSPARENT);
        voiceBtn.setOnClickListener(v -> {
            currentDialogInput = input;
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your note...");
            try {
                voiceRecognitionLauncher.launch(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Voice recognition not supported", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(voiceBtn);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            currentDialogInput = null;
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                saveNoteForDate(sdf.format(noteDate.getTime()), text);
            } else {
                Toast.makeText(this, "Note cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            currentDialogInput = null;
            dialog.cancel();
        });
        builder.show();
    }

    private void updateRemarkHistory() {
        remarkHistoryContainer.removeAllViews();
        archiveHistoryContainer.removeAllViews();
        deletedHistoryContainer.removeAllViews();

        int mainTheme = colorPrefs.getInt("color_main_theme", getColor(R.color.light_green));
        int archiveColor = colorPrefs.getInt("color_archive", Color.YELLOW);
        int deletedColor = colorPrefs.getInt("color_deleted", getColor(R.color.chili_red));

        int activeCount = countTotalNotes(sharedPreferences);
        TextView activeTitle = findViewById(R.id.remarkHistoryTitle);
        if (activeTitle != null) {
            String countText = getString(R.string.all_personal_notes) + " (" + activeCount + ")";
            activeTitle.setText(countText);
            activeTitle.setTextColor(mainTheme);
            applyFontSettings(activeTitle, 18);
        }
        loadHistoryFromPrefs(sharedPreferences, remarkHistoryContainer, R.string.no_notes_saved, mainTheme);
        Button archiveBtn = new Button(this);
        archiveBtn.setText(R.string.archive_all_past_notes);
        applyFontSettings(archiveBtn, 12);
        archiveBtn.setBackgroundTintList(ColorStateList.valueOf(mainTheme));
        archiveBtn.setTextColor(Color.WHITE);
        archiveBtn.setOnClickListener(v -> archiveAllPastNotes());
        remarkHistoryContainer.addView(archiveBtn);
        int archiveCount = countTotalNotes(archivePreferences);
        TextView archiveTitle = findViewById(R.id.archiveHistoryTitle);
        if (archiveTitle != null) {
            String countText = getString(R.string.archive_folder) + " (" + archiveCount + ")";
            archiveTitle.setText(countText);
            archiveTitle.setTextColor(archiveColor);
            applyFontSettings(archiveTitle, 18);
        }
        loadHistoryFromPrefs(archivePreferences, archiveHistoryContainer, R.string.no_archived_notes, archiveColor);
        int deletedCount = countTotalNotes(deletedPreferences);
        TextView deletedTitle = findViewById(R.id.deletedHistoryTitle);
        if (deletedTitle != null) {
            String countText = getString(R.string.deleted_notes_title) + " (" + deletedCount + ")";
            deletedTitle.setText(countText);
            deletedTitle.setTextColor(deletedColor);
            applyFontSettings(deletedTitle, 18);
        }
        loadHistoryFromPrefs(deletedPreferences, deletedHistoryContainer, R.string.no_deleted_notes, deletedColor);
        Button clearTrashBtn = new Button(this);
        clearTrashBtn.setText(R.string.clear_trash_btn);
        applyFontSettings(clearTrashBtn, 10);
        clearTrashBtn.setBackgroundTintList(ColorStateList.valueOf(deletedColor));
        clearTrashBtn.setTextColor(Color.WHITE);
        clearTrashBtn.setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("Clear Trash")
                .setMessage("Permanently delete all notes in trash?").setPositiveButton("Yes", (dialog, which) -> {
                    deletedPreferences.edit().clear().apply();
                    updateRemarkHistory();
                    updateAllWidgets();
                    Toast.makeText(MainActivity.this, "Trash cleared", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("No", null).show());
        deletedHistoryContainer.addView(clearTrashBtn);
    }

    private int countTotalNotes(SharedPreferences prefs) {
        int count = 0;
        Map<String, ?> allEntries = prefs.getAll();
        for (Object entry : allEntries.values()) {
            if (entry != null) {
                String val = entry.toString();
                if (!val.isEmpty()) count += val.split("\n").length;
            }
        }
        return count;
    }

    private void archiveAllPastNotes() {
        int movedCount = archiveAllPastNotesSilent();
        updateRemarkHistory();
        loadRemarksForSelectedDate();
        adapter.notifyDataSetChanged();
        updateAllWidgets();
        if (movedCount > 0) {
            archiveHistoryContainer.setVisibility(View.VISIBLE);
            Toast.makeText(this, movedCount + " dates moved to archive", Toast.LENGTH_SHORT).show();
        } else Toast.makeText(this, "No old notes to archive", Toast.LENGTH_SHORT).show();
        mainScrollView.post(() -> {
            View title = findViewById(R.id.archiveHistoryTitle);
            if (title != null) mainScrollView.smoothScrollTo(0, title.getTop());
        });
    }

    private int archiveAllPastNotesSilent() {
        Map<String, ?> allEntries = sharedPreferences.getAll();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayKey = sdf.format(Calendar.getInstance().getTime());
        int movedCount = 0;
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String dateKey = entry.getKey();
            try {
                Date noteDate = sdf.parse(dateKey);
                Date todayDate = sdf.parse(todayKey);
                if (noteDate != null && todayDate != null && noteDate.before(todayDate)) {
                    String value = entry.getValue().toString();
                    if (!value.isEmpty()) {
                        String existingArchived = archivePreferences.getString(dateKey, "");
                        String updatedArchived = existingArchived.isEmpty() ? value : existingArchived + "\n" + value;
                        archivePreferences.edit().putString(dateKey, updatedArchived).apply();
                        sharedPreferences.edit().remove(dateKey).apply();
                        movedCount++;
                    }
                }
            } catch (Exception ignored) {}
        }
        return movedCount;
    }

    private void loadHistoryFromPrefs(SharedPreferences prefs, LinearLayout container, int emptyTextRes, int dateColor) {
        Map<String, ?> allEntries = prefs.getAll();
        if (allEntries.isEmpty()) {
            TextView noHistory = new TextView(this);
            noHistory.setText(getString(emptyTextRes));
            noHistory.setTextColor(Color.WHITE);
            container.addView(noHistory);
            return;
        }
        List<String> sortedKeys = new ArrayList<>(allEntries.keySet());
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayKey = sdf.format(Calendar.getInstance().getTime());
        sortedKeys.sort((o1, o2) -> {
            try {
                Date d1 = sdf.parse(o1);
                Date d2 = sdf.parse(o2);
                if (d1 != null && d2 != null) return d1.compareTo(d2);
            } catch (Exception ignored) {}
            return o1.compareTo(o2);
        });
        for (String dateKey : sortedKeys) {
            Object valObj = allEntries.get(dateKey);
            String value = valObj != null ? valObj.toString() : "";
            if (value.isEmpty()) continue;
            String[] notes = value.split("\n");
            TextView dateHeader = new TextView(this);
            String label = Objects.equals(dateKey, todayKey) ? "TODAY: " + dateKey : "Date: " + dateKey;
            dateHeader.setText(label);
            applyFontSettings(dateHeader, 13);
            dateHeader.setTextColor(dateColor);
            dateHeader.setPadding(0, 16, 0, 4);
            dateHeader.setOnClickListener(v -> jumpToDate(dateKey, sdf));
            container.addView(dateHeader);
            for (int i = 0; i < notes.length; i++) {
                final int index = i;
                final String noteText = notes[i];
                LinearLayout noteLayout = new LinearLayout(this);
                noteLayout.setOrientation(LinearLayout.HORIZONTAL);
                noteLayout.setGravity(Gravity.CENTER_VERTICAL);
                noteLayout.setPadding(32, 4, 0, 4);
                TextView tv = new TextView(this);
                tv.setText(noteText);
                applyFontSettings(tv, 14);
                if (noteText.startsWith("▣ ")) {
                    tv.setTextColor(colorPrefs.getInt("color_note_checked", Color.GREEN));
                } else if (container == deletedHistoryContainer) {
                    tv.setTextColor(Color.WHITE); // Keep deleted text white, header is red
                } else {
                    tv.setTextColor(colorPrefs.getInt("color_note_text", Color.WHITE));
                }
                tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                noteLayout.addView(tv);
                int iconSize = (int) (28 * getResources().getDisplayMetrics().density);
                ImageButton shareBtn = new ImageButton(this);
                shareBtn.setImageResource(android.R.drawable.ic_menu_share);
                shareBtn.setBackgroundColor(Color.TRANSPARENT);
                shareBtn.setPadding(4, 4, 4, 4);
                shareBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
                noteLayout.addView(shareBtn, new LinearLayout.LayoutParams(iconSize, iconSize));
                shareBtn.setOnClickListener(v -> {
                    String[] options = {"Share as Text", "Share as .ics File"};
                    new AlertDialog.Builder(this).setTitle("Share Note").setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            String textToShare = getNoteBody(noteText);
                            Intent sendIntent = new Intent();
                            sendIntent.setAction(Intent.ACTION_SEND);
                            sendIntent.putExtra(Intent.EXTRA_TEXT, textToShare);
                            sendIntent.setType("text/plain");
                            startActivity(Intent.createChooser(sendIntent, "Share Note via"));
                        } else shareNoteAsIcs(noteText, dateKey);
                    }).show();
                });
                if (container == archiveHistoryContainer || container == deletedHistoryContainer) {
                    ImageButton restoreBtn = new ImageButton(this);
                    restoreBtn.setImageResource(android.R.drawable.ic_menu_revert);
                    restoreBtn.setBackgroundColor(Color.TRANSPARENT);
                    restoreBtn.setPadding(4, 4, 4, 4);
                    restoreBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    noteLayout.addView(restoreBtn, new LinearLayout.LayoutParams(iconSize, iconSize));
                    restoreBtn.setOnClickListener(v -> restoreSingleNote(dateKey, index, prefs));
                    ImageButton delBtn = new ImageButton(this);
                    delBtn.setImageResource(android.R.drawable.ic_menu_delete);
                    delBtn.setBackgroundColor(Color.TRANSPARENT);
                    delBtn.setPadding(4, 4, 4, 4);
                    delBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    noteLayout.addView(delBtn, new LinearLayout.LayoutParams(iconSize, iconSize));
                    if (container == archiveHistoryContainer) delBtn.setOnClickListener(v -> deleteSingleNote(dateKey, index, prefs));
                    else if (container == deletedHistoryContainer) delBtn.setOnClickListener(v -> deleteSingleNotePermanently(dateKey, index, prefs));
                }
                tv.setOnLongClickListener(v -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("SAR Note", noteText);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Note copied", Toast.LENGTH_SHORT).show();
                    return true;
                });
                container.addView(noteLayout);
            }
        }
    }

    private void jumpToDate(String dateKey, SimpleDateFormat sdf) {
        try {
            Date date = sdf.parse(dateKey);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                calendar.set(Calendar.MONTH, cal.get(Calendar.MONTH));
                calendar.set(Calendar.YEAR, cal.get(Calendar.YEAR));
                updateCalendar();
                updateDateInfo(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
                adapter.notifyDataSetChanged();
                mainScrollView.smoothScrollTo(0, 0);
            }
        } catch (Exception ignored) {}
    }

    private void updateAllWidgets() {
        Context context = getApplicationContext();
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        
        int[] todayIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, CalendarWidget.class));
        if (todayIds.length > 0) {
            Intent updateIntent = new Intent(context, CalendarWidget.class);
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, todayIds);
            sendBroadcast(updateIntent);
        }
        
        int[] agendaIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, AgendaWidgetProvider.class));
        if (agendaIds.length > 0) {
            appWidgetManager.notifyAppWidgetViewDataChanged(agendaIds, R.id.agenda_list);
        }
    }

    private void launchSecureBox(boolean openNewNote) {
        String customPass = securityPrefs.getString("custom_password", null);

        if (customPass != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Secure Box Access");
            builder.setMessage("Enter your custom password:");

            final EditText input = new EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            builder.setView(input);

            builder.setPositiveButton("Access", (dialog, which) -> {
                String entered = input.getText().toString().trim();
                if (entered.equals(customPass)) {
                    Intent intent = new Intent(this, SecureBoxActivity.class);
                    if (openNewNote) intent.putExtra("action", "new_note");
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } else {
            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this,
                    executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(getApplicationContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    Intent intent = new Intent(MainActivity.this, SecureBoxActivity.class);
                    if (openNewNote) intent.putExtra("action", "new_note");
                    startActivity(intent);
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Toast.makeText(getApplicationContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
                }
            });

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Secure Box Access")
                    .setSubtitle("Use your phone's PIN, Pattern, or Biometrics")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();

            biometricPrompt.authenticate(promptInfo);
        }
    }

    private void showChangePasswordDialog() {
        String[] options = {"Use Phone Lock Screen (Fingerprint/PIN)", "Set a New Custom Password"};
        new AlertDialog.Builder(this)
                .setTitle("Secure Box Access Type")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        securityPrefs.edit().remove("custom_password").apply();
                        Toast.makeText(this, "Secure Box now synchronized with phone lock.", Toast.LENGTH_SHORT).show();
                    } else {
                        showSetCustomPasswordDialog();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSetCustomPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set New Password");
        builder.setMessage("Enter the custom password you want for the Secure Box:");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newPass = input.getText().toString().trim();
            if (!newPass.isEmpty()) {
                securityPrefs.edit().putString("custom_password", newPass).apply();
                Toast.makeText(this, "Custom password saved!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            currentDialogInput = null;
            dialog.cancel();
        });
        builder.show();
    }

    private void showChangeColorsDialog() {
        String[] options = {
                "Main Theme (Buttons/Title)",
                "Active Note Text",
                "Finished Note Text",
                "Archive Folder Color",
                "Deleted Folder Color",
                "Secure Box: Personal Category",
                "Secure Box: Password Category",
                "Secure Box: Family Category",
                "Secure Box: Work Category",
                "Secure Box: Others Category",
                "App Background Color",
                "Reset All Colors"
        };
        new AlertDialog.Builder(this)
                .setTitle("Change Colors")
                .setItems(options, (dialog, which) -> {
                    if (which == 11) {
                        resetColors();
                    } else {
                        showColorPicker(which);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showColorPicker(int category) {
        String[] colorNames = {"Green", "Light Green", "Blue", "Red", "Chili Red", "Orange", "Purple", "Gold", "Unmellow Yellow", "Honey", "Teal", "White", "Black"};
        int[] colorValues = {
                0xFF4CAF50, 0xFF8BC34A, 0xFF2196F3, 0xFFFF0000, 0xFFC21807,
                0xFFFF9800, 0xFF9C27B0, 0xFFFFD700, 0xFFFFFF66, 0xFFFFC30B,
                0xFF008080, 0xFFFFFFFF, 0xFF000000
        };

        new AlertDialog.Builder(this)
                .setTitle("Select Color")
                .setItems(colorNames, (dialog, which) -> {
                    int selectedColor = colorValues[which];
                    saveColor(category, selectedColor);
                })
                .show();
    }

    private void saveColor(int category, int color) {
        String key;
        switch (category) {
            case 0: key = "color_main_theme"; break;
            case 1: key = "color_note_text"; break;
            case 2: key = "color_note_checked"; break;
            case 3: key = "color_archive"; break;
            case 4: key = "color_deleted"; break;
            case 5: key = "color_sb_personal"; break;
            case 6: key = "color_sb_password"; break;
            case 7: key = "color_sb_family"; break;
            case 8: key = "color_sb_work"; break;
            case 9: key = "color_sb_others"; break;
            case 10: key = "color_app_background"; break;
            default: return;
        }
        colorPrefs.edit().putInt(key, color).apply();
        refreshUIColors();
        Toast.makeText(this, "Color updated!", Toast.LENGTH_SHORT).show();
    }

    private void resetColors() {
        colorPrefs.edit().clear().apply();
        refreshUIColors();
        Toast.makeText(this, "Colors reset to default", Toast.LENGTH_SHORT).show();
    }

    private void showFontDialog() {
        String[] options = {"Font Style", "Font Size", "Reset Font Settings"};
        new AlertDialog.Builder(this)
                .setTitle("Font Settings")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showFontStylePicker();
                    } else if (which == 1) {
                        showFontSizePicker();
                    } else {
                        resetFontSettings();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFontStylePicker() {
        String[] fontNames = {"Default", "Sans Serif", "Serif", "Monospace"};
        new AlertDialog.Builder(this)
                .setTitle("Select Font Style")
                .setItems(fontNames, (dialog, which) -> {
                    fontPrefs.edit().putInt("font_style", which).apply();
                    refreshUI();
                    Toast.makeText(this, "Font style updated!", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showFontSizePicker() {
        String[] sizeNames = {"Small", "Normal", "Large", "Extra Large"};
        new AlertDialog.Builder(this)
                .setTitle("Select Font Size")
                .setItems(sizeNames, (dialog, which) -> {
                    fontPrefs.edit().putInt("font_size_index", which).apply();
                    refreshUI();
                    Toast.makeText(this, "Font size updated!", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void resetFontSettings() {
        fontPrefs.edit().clear().apply();
        refreshUI();
        Toast.makeText(this, "Font settings reset", Toast.LENGTH_SHORT).show();
    }

    private void refreshUI() {
        refreshUIColors();
        // Fonts are refreshed inside individual component loaders and in adapter
    }

    private void applyFontSettings(TextView textView, float baseSize) {
        int styleIndex = fontPrefs.getInt("font_style", 0);
        Typeface tf = Typeface.DEFAULT;
        switch (styleIndex) {
            case 1: tf = Typeface.SANS_SERIF; break;
            case 2: tf = Typeface.SERIF; break;
            case 3: tf = Typeface.MONOSPACE; break;
        }
        textView.setTypeface(tf);

        int sizeIndex = fontPrefs.getInt("font_size_index", 1); // Default to Normal
        float multiplier = 1.0f;
        switch (sizeIndex) {
            case 0: multiplier = 0.8f; break;
            case 1: multiplier = 1.0f; break;
            case 2: multiplier = 1.3f; break;
            case 3: multiplier = 1.6f; break;
        }
        textView.setTextSize(baseSize * multiplier);
    }

    private void refreshUIColors() {
        int mainTheme = colorPrefs.getInt("color_main_theme", getColor(R.color.light_green));
        int noteText = colorPrefs.getInt("color_note_text", Color.WHITE);
        int noteChecked = colorPrefs.getInt("color_note_checked", Color.GREEN);
        int archiveColor = colorPrefs.getInt("color_archive", Color.YELLOW);
        int deletedColor = colorPrefs.getInt("color_deleted", getColor(R.color.chili_red));
        int bgColor = colorPrefs.getInt("color_app_background", Color.BLACK);

        // Root Background
        View root = findViewById(R.id.main);
        if (root != null) root.setBackgroundColor(bgColor);

        // Header
        TextView title = findViewById(R.id.titleTextView);
        if (title != null) {
            title.setTextColor(mainTheme);
            applyFontSettings(title, 22);
        }
        TextView clock = findViewById(R.id.textClockDate);
        if (clock != null) {
            clock.setTextColor(mainTheme);
            applyFontSettings(clock, 14);
        }
        TextView remarkLbl = findViewById(R.id.remarkLabel);
        if (remarkLbl != null) {
            remarkLbl.setTextColor(mainTheme);
            applyFontSettings(remarkLbl, 18);
        }
        TextView monthYear = findViewById(R.id.monthYearText);
        if (monthYear != null) {
            applyFontSettings(monthYear, 16);
        }

        // Weekday labels
        ViewGroup weekdayLayout = findViewById(R.id.weekdayLayout);
        if (weekdayLayout != null) {
            for (int i = 0; i < weekdayLayout.getChildCount(); i++) {
                View child = weekdayLayout.getChildAt(i);
                if (child instanceof TextView) {
                    applyFontSettings((TextView) child, 11);
                }
            }
        }

        // Input
        EditText input = findViewById(R.id.noteInput);
        if (input != null) {
            applyFontSettings(input, 14);
        }

        // Buttons
        Button saveBtn = findViewById(R.id.saveNoteButton);
        if (saveBtn != null) {
            saveBtn.setBackgroundTintList(ColorStateList.valueOf(mainTheme));
            applyFontSettings(saveBtn, 11);
        }
        Button secureBtn = findViewById(R.id.secureBoxButton);
        if (secureBtn != null) {
            secureBtn.setBackgroundTintList(ColorStateList.valueOf(mainTheme));
            applyFontSettings(secureBtn, 11);
        }
        Button notifyBtn = findViewById(R.id.notificationSettingsButton);
        if (notifyBtn != null) {
            notifyBtn.setBackgroundTintList(ColorStateList.valueOf(mainTheme));
            applyFontSettings(notifyBtn, 11);
        }
        ImageButton menuBtn = findViewById(R.id.mainMenuButton);
        if (menuBtn != null) menuBtn.setImageTintList(ColorStateList.valueOf(mainTheme));
        ImageButton voiceBtn = findViewById(R.id.voiceNoteButton);
        if (voiceBtn != null) voiceBtn.setImageTintList(ColorStateList.valueOf(mainTheme));

        // History
        updateRemarkHistory(); // This will use the new colors/fonts during redraw
        loadRemarksForSelectedDate();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showPrintDialog() {
        String[] options = {"Selected Date's Notes", "All Personal Notes", "All Archived Notes", "All Deleted Notes"};
        new AlertDialog.Builder(this)
                .setTitle("Print Notes")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: printNotes(currentDateKey, sharedPreferences); break;
                        case 1: printAllNotes(sharedPreferences, "All Personal Notes"); break;
                        case 2: printAllNotes(archivePreferences, "All Archived Notes"); break;
                        case 3: printAllNotes(deletedPreferences, "All Deleted Notes"); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void printNotes(String dateKey, SharedPreferences prefs) {
        String notes = prefs.getString(dateKey, "");
        if (notes.isEmpty()) {
            Toast.makeText(this, "No notes to print for " + dateKey, Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<h1>Notes for ").append(dateKey).append("</h1>");
        html.append("<ul>");
        for (String note : notes.split("\n")) {
            html.append("<li>").append(note).append("</li>");
        }
        html.append("</ul></body></html>");
        
        doPrint(html.toString(), "Notes_" + dateKey.replace("/", "_"));
    }

    private void printAllNotes(SharedPreferences prefs, String title) {
        Map<String, ?> allEntries = prefs.getAll();
        if (allEntries.isEmpty()) {
            Toast.makeText(this, "No notes found in " + title, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<h1>").append(title).append("</h1>");

        List<String> sortedKeys = new ArrayList<>(allEntries.keySet());
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        sortedKeys.sort((o1, o2) -> {
            try {
                Date d1 = sdf.parse(o1);
                Date d2 = sdf.parse(o2);
                if (d1 != null && d2 != null) return d1.compareTo(d2);
            } catch (Exception ignored) {}
            return o1.compareTo(o2);
        });

        for (String key : sortedKeys) {
            Object val = allEntries.get(key);
            String notes = val != null ? val.toString() : "";
            if (!notes.isEmpty()) {
                html.append("<h3>Date: ").append(key).append("</h3>");
                html.append("<ul>");
                for (String note : notes.split("\n")) {
                    html.append("<li>").append(note).append("</li>");
                }
                html.append("</ul>");
            }
        }
        html.append("</body></html>");

        doPrint(html.toString(), title.replace(" ", "_"));
    }

    private void doPrint(String htmlContent, String jobName) {
        WebView webView = new WebView(this);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                PrintDocumentAdapter printAdapter = view.createPrintDocumentAdapter(jobName);
                printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
            }
        });
        webView.loadDataWithBaseURL(null, htmlContent, "text/HTML", "UTF-8", null);
    }

    private class CalendarAdapter extends BaseAdapter {
        private final ArrayList<Date> days;
        private final Calendar currentMonth;
        private final LayoutInflater inflater;

        public CalendarAdapter(Context context, ArrayList<Date> days, Calendar currentMonth) {
            this.days = days;
            this.currentMonth = currentMonth;
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() { return days.size(); }
        @Override
        public Object getItem(int position) { return days.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = inflater.inflate(R.layout.calendar_day_item, parent, false);
            }

            TextView dayText = itemView.findViewById(R.id.dayNumber);
            applyFontSettings(dayText, 14);
            ImageView flag = itemView.findViewById(R.id.noteFlag);

            Date date = days.get(position);
            Calendar cellCal = Calendar.getInstance();
            cellCal.setTime(date);

            dayText.setText(String.format(Locale.getDefault(), "%d", cellCal.get(Calendar.DAY_OF_MONTH)));

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String key = sdf.format(date);
            String savedNotes = sharedPreferences.getString(key, "");
            String archivedNotes = archivePreferences.getString(key, "");
            boolean hasPersonalNotes = !savedNotes.isEmpty();
            boolean hasArchivedNotes = !archivedNotes.isEmpty();
            boolean hasNotes = hasPersonalNotes || hasArchivedNotes;

            flag.setVisibility(hasNotes ? View.VISIBLE : View.INVISIBLE);

            if (hasPersonalNotes) {
                flag.setImageTintList(ColorStateList.valueOf(colorPrefs.getInt("color_main_theme", getColor(R.color.light_green))));
            } else if (hasArchivedNotes) {
                flag.setImageTintList(ColorStateList.valueOf(colorPrefs.getInt("color_archive", Color.YELLOW)));
            }

            if (cellCal.get(Calendar.MONTH) != currentMonth.get(Calendar.MONTH)) {
                dayText.setTextColor(Color.GRAY);
            } else {
                dayText.setTextColor(Color.WHITE);
            }

            dayText.setBackgroundColor(Color.TRANSPARENT);
            itemView.setBackgroundColor(Color.TRANSPARENT);

            Calendar today = Calendar.getInstance();
            if (cellCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cellCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                cellCal.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)) {
                dayText.setBackgroundResource(R.drawable.today_circle);
            } else if (cellCal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
                cellCal.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH) &&
                cellCal.get(Calendar.DAY_OF_MONTH) == selectedDate.get(Calendar.DAY_OF_MONTH)) {
                itemView.setBackgroundColor(Color.parseColor("#33FFFFFF"));
            }

            itemView.setOnClickListener(v -> {
                selectedDate.set(cellCal.get(Calendar.YEAR), cellCal.get(Calendar.MONTH), cellCal.get(Calendar.DAY_OF_MONTH));

                if (cellCal.get(Calendar.MONTH) != currentMonth.get(Calendar.MONTH)) {
                    calendar.set(Calendar.MONTH, cellCal.get(Calendar.MONTH));
                    calendar.set(Calendar.YEAR, cellCal.get(Calendar.YEAR));
                    updateCalendar();
                } else {
                    updateDateInfo(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH));
                    notifyDataSetChanged();
                }
            });

            return itemView;
        }
    }
}
