package com.example.mycalendar2026sar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private SharedPreferences sharedPreferences;
    private SharedPreferences archivePreferences;
    private SharedPreferences deletedPreferences;
    private SharedPreferences reminderPreferences;
    private CalendarAdapter adapter;

    private String lastDeletedNote;
    private int lastDeletedIndex;
    private String lastDeletedDateKey;

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

        calendarGrid = findViewById(R.id.calendarGrid);
        monthYearText = findViewById(R.id.monthYearText);
        noteInput = findViewById(R.id.noteInput);
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

        // Hide folders initially
        archiveHistoryContainer.setVisibility(View.GONE);
        deletedHistoryContainer.setVisibility(View.GONE);

        // Always initialize to current day
        selectedDate = Calendar.getInstance();
        calendar = (Calendar) selectedDate.clone();
        calendar.set(Calendar.DAY_OF_MONTH, 1); // Start at beginning of current month

        updateCalendar();
        updateRemarkHistory();

        // Initial state: hide Archive and Deleted containers
        archiveHistoryContainer.setVisibility(View.GONE);
        deletedHistoryContainer.setVisibility(View.GONE);

        findViewById(R.id.remarkHistoryTitle).setOnClickListener(v -> {
            if (remarkHistoryContainer.getVisibility() == View.VISIBLE) {
                remarkHistoryContainer.setVisibility(View.GONE);
            } else {
                remarkHistoryContainer.setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.archiveHistoryTitle).setOnClickListener(v -> {
            if (archiveHistoryContainer.getVisibility() == View.VISIBLE) {
                archiveHistoryContainer.setVisibility(View.GONE);
            } else {
                archiveHistoryContainer.setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.deletedHistoryTitle).setOnClickListener(v -> {
            if (deletedHistoryContainer.getVisibility() == View.VISIBLE) {
                deletedHistoryContainer.setVisibility(View.GONE);
            } else {
                deletedHistoryContainer.setVisibility(View.VISIBLE);
            }
        });
        
        saveNoteButton.setOnClickListener(v -> saveNote());

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
    }

    private void autoCleanArchive() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Calendar now = Calendar.getInstance();
        
        // 1 month threshold for Archive -> Delete
        Calendar archiveThresholdCal = (Calendar) now.clone();
        archiveThresholdCal.add(Calendar.MONTH, -1);
        Date archiveThreshold = archiveThresholdCal.getTime();

        // 2 months threshold for Permanent Delete (1 month in Archive + 1 month in Deleted)
        Calendar deleteThresholdCal = (Calendar) now.clone();
        deleteThresholdCal.add(Calendar.MONTH, -2);
        Date deleteThreshold = deleteThresholdCal.getTime();

        // Archive -> Delete
        Map<String, ?> allArchived = archivePreferences.getAll();
        for (Map.Entry<String, ?> entry : allArchived.entrySet()) {
            String dateKey = entry.getKey();
            try {
                Date noteDate = sdf.parse(dateKey);
                if (noteDate != null && noteDate.before(archiveThreshold)) {
                    String value = entry.getValue().toString();
                    if (!value.isEmpty()) {
                        String existingDeleted = deletedPreferences.getString(dateKey, "");
                        String updatedDeleted = existingDeleted.isEmpty() ? value : existingDeleted + "\n" + value;
                        deletedPreferences.edit().putString(dateKey, updatedDeleted).apply();
                        archivePreferences.edit().remove(dateKey).apply();
                    }
                }
            } catch (Exception ignored) {}
        }

        // Permanent Delete from Trash after 1 month in Trash (2 months total age)
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
            noRemarks.setTextColor(Color.GREEN);
            dayRemarksContainer.addView(noRemarks);
        }
    }

    private void addRemarkView(String remarkText, int index, SharedPreferences sourcePrefs) {
        LinearLayout horizontalLayout = new LinearLayout(this);
        horizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
        horizontalLayout.setGravity(Gravity.CENTER_VERTICAL);
        horizontalLayout.setPadding(0, 4, 0, 4);

        TextView textView = createRemarkTextView(remarkText);
        horizontalLayout.addView(textView);

        int iconSize = (int) (23 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        btnParams.setMargins(4, 0, 4, 0);

        ImageButton alarmButton = createActionButton(android.R.drawable.ic_lock_idle_alarm, btnParams, v -> showTimePickerDialog(remarkText));
        
        // Check if a reminder is set for this specific note
        String reminderKey = currentDateKey + "_" + remarkText;
        boolean hasReminder = reminderPreferences.contains(reminderKey);
        if (hasReminder) {
            alarmButton.setImageTintList(ColorStateList.valueOf(Color.GREEN));
        } else {
            alarmButton.setImageTintList(null); // Keep original color
        }
        
        horizontalLayout.addView(alarmButton);

        horizontalLayout.addView(createActionButton(android.R.drawable.ic_menu_edit, btnParams, v -> showEditDialog(remarkText, index, sourcePrefs)));

        horizontalLayout.addView(createActionButton(android.R.drawable.ic_menu_share, btnParams, v -> {
            String[] options = {"Share as Text", "Share as .ics File"};
            new AlertDialog.Builder(this)
                    .setTitle("Share Note")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            String noteBody = remarkText.startsWith("• ") ? remarkText.substring(2) : remarkText;
                            String shareText = "SAR CALENDAR REMINDER:\n" + noteBody + "\nDate: " + currentDateKey;
                            if (hasReminder) {
                                shareText += "\n(Check SAR Calendar to sync this reminder)";
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
        String noteBody = noteText.startsWith("• ") ? noteText.substring(2) : noteText;
        String reminderKey = dateKey + "_" + noteText;
        String savedTime = reminderPreferences.getString(reminderKey, null);

        // Prepare ICS content
        String icsContent = "BEGIN:VCALENDAR\n" +
                "VERSION:2.0\n" +
                "PRODID:-//SAR Calendar//EN\n" +
                "BEGIN:VEVENT\n" +
                "SUMMARY:" + noteBody + "\n";
        
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = sdf.parse(dateKey);
            if (date != null) {
                if (savedTime != null && savedTime.contains(":")) {
                    String[] parts = savedTime.split(":");
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);
                    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                    cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
                    cal.set(Calendar.SECOND, 0);
                    
                    SimpleDateFormat icsSdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault());
                    String dateStr = icsSdf.format(cal.getTime());
                    icsContent += "DTSTART:" + dateStr + "\n";
                    
                    // Add 30 mins for end time
                    cal.add(Calendar.MINUTE, 30);
                    String endDateStr = icsSdf.format(cal.getTime());
                    icsContent += "DTEND:" + endDateStr + "\n";
                } else {
                    SimpleDateFormat icsSdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                    String dateStr = icsSdf.format(date);
                    icsContent += "DTSTART;VALUE=DATE:" + dateStr + "\n" +
                            "DTEND;VALUE=DATE:" + dateStr + "\n";
                }
            }
        } catch (Exception ignored) {}
        
        icsContent += "DESCRIPTION:SAR Calendar Note\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";

        try {
            // Create temporary file
            File cachePath = new File(getCacheDir(), "shared_notes");
            cachePath.mkdirs();
            File icsFile = new File(cachePath, "note_reminder.ics");
            FileOutputStream stream = new FileOutputStream(icsFile);
            stream.write(icsContent.getBytes());
            stream.close();

            // Get URI using FileProvider
            android.net.Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", icsFile);

            // Create share intent
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/calendar");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Note via"));

        } catch (Exception e) {
            Toast.makeText(this, "Error creating .ics file", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView createRemarkTextView(String remarkText) {
        TextView textView = new TextView(this);
        textView.setText(remarkText);
        textView.setTextColor(Color.WHITE);
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        
        textView.setOnLongClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            String textToCopy = remarkText.startsWith("• ") ? remarkText.substring(2) : remarkText;
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
            
            // Move back to personal notes (active)
            String savedRemarks = sharedPreferences.getString(dateKey, "");
            String updatedSaved = savedRemarks.isEmpty() ? noteToRestore : savedRemarks + "\n" + noteToRestore;
            sharedPreferences.edit().putString(dateKey, updatedSaved).apply();

            // Update source
            String updatedSource = String.join("\n", notesList);
            if (updatedSource.isEmpty()) {
                sourcePrefs.edit().remove(dateKey).apply();
            } else {
                sourcePrefs.edit().putString(dateKey, updatedSource).apply();
            }
            
            updateRemarkHistory();
            if (dateKey.equals(currentDateKey)) {
                loadRemarksForSelectedDate();
            }
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Note restored to personal notes", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteSingleNote(String dateKey, int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(dateKey, "");
        if (currentText.isEmpty()) return;

        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            String noteToDelete = remarksList.remove(index);
            
            // Move to deleted notes storage
            String currentDeleted = deletedPreferences.getString(dateKey, "");
            String updatedDeleted = currentDeleted.isEmpty() ? noteToDelete : currentDeleted + "\n" + noteToDelete;
            deletedPreferences.edit().putString(dateKey, updatedDeleted).apply();

            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) {
                sourcePrefs.edit().remove(dateKey).apply();
            } else {
                sourcePrefs.edit().putString(dateKey, updatedRemarks).apply();
            }

            updateRemarkHistory();
            if (dateKey.equals(currentDateKey)) {
                loadRemarksForSelectedDate();
            }
            adapter.notifyDataSetChanged();
            deletedHistoryContainer.setVisibility(View.VISIBLE);
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
                        if (updatedSource.isEmpty()) {
                            sourcePrefs.edit().remove(dateKey).apply();
                        } else {
                            sourcePrefs.edit().putString(dateKey, updatedSource).apply();
                        }
                        updateRemarkHistory();
                        if (dateKey.equals(currentDateKey)) {
                            loadRemarksForSelectedDate();
                        }
                        adapter.notifyDataSetChanged();
                        Toast.makeText(this, "Permanently Deleted", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void unarchiveNote(int index) {
        String archivedRemarks = archivePreferences.getString(currentDateKey, "");
        if (archivedRemarks.isEmpty()) return;

        List<String> remarksList = new ArrayList<>(Arrays.asList(archivedRemarks.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            String noteToRestore = remarksList.remove(index);
            
            // Move back to active notes
            String savedRemarks = sharedPreferences.getString(currentDateKey, "");
            String updatedSaved = savedRemarks.isEmpty() ? noteToRestore : savedRemarks + "\n" + noteToRestore;
            sharedPreferences.edit().putString(currentDateKey, updatedSaved).apply();

            // Update archive
            String updatedArchived = String.join("\n", remarksList);
            if (updatedArchived.isEmpty()) {
                archivePreferences.edit().remove(currentDateKey).apply();
            } else {
                archivePreferences.edit().putString(currentDateKey, updatedArchived).apply();
            }
            
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Note restored from archive", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTimePickerDialog(String noteText) {
        Calendar currentTime = Calendar.getInstance();
        int hour = currentTime.get(Calendar.HOUR_OF_DAY);
        int minute = currentTime.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, selectedHour, selectedMinute) -> setReminder(selectedHour, selectedMinute, noteText), hour, minute, true);
        timePickerDialog.setTitle("Set Reminder Time");
        timePickerDialog.show();
    }

    private void setReminder(int hour, int minute, String noteText) {
        Calendar reminderTime = (Calendar) selectedDate.clone();
        reminderTime.set(Calendar.HOUR_OF_DAY, hour);
        reminderTime.set(Calendar.MINUTE, minute);
        reminderTime.set(Calendar.SECOND, 0);

        if (reminderTime.before(Calendar.getInstance())) {
            Toast.makeText(this, "Cannot set reminder in the past!", Toast.LENGTH_SHORT).show();
            return;
        }

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("noteText", noteText);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_IMMUTABLE);

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime.getTimeInMillis(), pendingIntent);

        // Save reminder status and refresh UI
        String reminderKey = currentDateKey + "_" + noteText;
        String timeValue = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
        reminderPreferences.edit().putString(reminderKey, timeValue).apply();
        loadRemarksForSelectedDate();

        Toast.makeText(this, "Reminder set for " + timeValue, Toast.LENGTH_SHORT).show();
    }

    private void showEditDialog(String currentText, int index, SharedPreferences sourcePrefs) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Note");

        final EditText input = new EditText(this);
        input.setText(currentText.startsWith("• ") ? currentText.substring(2) : currentText);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String updatedText = input.getText().toString().trim();
            if (!updatedText.isEmpty()) {
                updateRemark(updatedText, index, sourcePrefs);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updateRemark(String newText, int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(currentDateKey, "");
        if (currentText.isEmpty()) return;

        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            remarksList.set(index, "• " + newText);
            String updatedRemarks = String.join("\n", remarksList);
            sourcePrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
        }
    }

    private void archiveNote(int index) {
        new AlertDialog.Builder(this)
                .setTitle("Archive Note")
                .setMessage("Are you sure you want to archive this note?")
                .setPositiveButton("Yes", (dialog, which) -> performArchiveNote(index))
                .setNegativeButton("No", null)
                .show();
    }

    private void performArchiveNote(int index) {
        String savedRemarks = sharedPreferences.getString(currentDateKey, "");
        if (savedRemarks.isEmpty()) return;

        List<String> remarksList = new ArrayList<>(Arrays.asList(savedRemarks.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            String noteToArchive = remarksList.remove(index);
            
            // Move to archive
            String archivedRemarks = archivePreferences.getString(currentDateKey, "");
            String updatedArchived = archivedRemarks.isEmpty() ? noteToArchive : archivedRemarks + "\n" + noteToArchive;
            archivePreferences.edit().putString(currentDateKey, updatedArchived).apply();

            // Update original
            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) {
                sharedPreferences.edit().remove(currentDateKey).apply();
            } else {
                sharedPreferences.edit().putString(currentDateKey, updatedRemarks).apply();
            }
            
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            archiveHistoryContainer.setVisibility(View.VISIBLE);
            Toast.makeText(this, R.string.archive_success, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteRemark(int index, SharedPreferences sourcePrefs) {
        if (sourcePrefs == deletedPreferences) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Note")
                    .setMessage("Permanently delete this note?")
                    .setPositiveButton("Yes", (dialog, which) -> performPermanentDelete(index, sourcePrefs))
                    .setNegativeButton("No", null)
                    .show();
            return;
        }

        String[] options = {"Move to Trash", "Delete Permanently"};
        new AlertDialog.Builder(this)
                .setTitle("Delete Note")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        performDeleteRemark(index, sourcePrefs);
                    } else {
                        performPermanentDelete(index, sourcePrefs);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performPermanentDelete(int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(currentDateKey, "");
        if (currentText.isEmpty()) return;

        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            remarksList.remove(index);
            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) {
                sourcePrefs.edit().remove(currentDateKey).apply();
            } else {
                sourcePrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            }
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Permanently Deleted", Toast.LENGTH_SHORT).show();
        }
    }

    private void performDeleteRemark(int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(currentDateKey, "");
        if (currentText.isEmpty()) return;

        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            // Save for undo
            lastDeletedNote = remarksList.get(index);
            lastDeletedIndex = index;
            lastDeletedDateKey = currentDateKey;

            remarksList.remove(index);
            
            // Move to deleted notes storage
            String currentDeleted = deletedPreferences.getString(currentDateKey, "");
            String updatedDeleted = currentDeleted.isEmpty() ? lastDeletedNote : currentDeleted + "\n" + lastDeletedNote;
            deletedPreferences.edit().putString(currentDateKey, updatedDeleted).apply();

            String updatedRemarks = String.join("\n", remarksList);
            
            if (updatedRemarks.isEmpty()) {
                sourcePrefs.edit().remove(currentDateKey).apply();
            } else {
                sourcePrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            }

            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            deletedHistoryContainer.setVisibility(View.VISIBLE);
            
            showUndoSnackbar();
        }
    }

    private void showUndoSnackbar() {
        View rootView = findViewById(R.id.main);
        Snackbar snackbar = Snackbar.make(rootView, "Note deleted", Snackbar.LENGTH_LONG);
        snackbar.setAction("UNDO", v -> undoDelete());
        snackbar.setActionTextColor(Color.YELLOW);
        snackbar.show();
    }

    private void undoDelete() {
        if (lastDeletedNote == null || lastDeletedDateKey == null) return;

        // Find correct storage for restoration based on date
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayKey = sdf.format(Calendar.getInstance().getTime());
        SharedPreferences targetPrefs = sharedPreferences;
        try {
            Date noteDate = sdf.parse(lastDeletedDateKey);
            Date today = sdf.parse(todayKey);
            if (noteDate != null && today != null && noteDate.before(today)) {
                targetPrefs = archivePreferences;
            }
        } catch (Exception ignored) {}

        String currentRemarks = targetPrefs.getString(lastDeletedDateKey, "");
        List<String> remarksList = new ArrayList<>();
        if (!currentRemarks.isEmpty()) {
            remarksList.addAll(Arrays.asList(currentRemarks.split("\n")));
        }

        // Put it back at its original index if possible, or at the end
        if (lastDeletedIndex >= 0 && lastDeletedIndex <= remarksList.size()) {
            remarksList.add(lastDeletedIndex, lastDeletedNote);
        } else {
            remarksList.add(lastDeletedNote);
        }

        String updatedRemarks = String.join("\n", remarksList);
        targetPrefs.edit().putString(lastDeletedDateKey, updatedRemarks).apply();

        // If we are still on the same date, refresh the view
        if (lastDeletedDateKey.equals(currentDateKey)) {
            loadRemarksForSelectedDate();
        }

        // Remove from deleted notes storage
        String currentDeleted = deletedPreferences.getString(lastDeletedDateKey, "");
        if (!currentDeleted.isEmpty()) {
            List<String> deletedList = new ArrayList<>(Arrays.asList(currentDeleted.split("\n")));
            deletedList.remove(lastDeletedNote);
            if (deletedList.isEmpty()) {
                deletedPreferences.edit().remove(lastDeletedDateKey).apply();
            } else {
                deletedPreferences.edit().putString(lastDeletedDateKey, String.join("\n", deletedList)).apply();
            }
        }

        updateRemarkHistory();
        adapter.notifyDataSetChanged();
        
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
        
        String entry = "• " + newText;
        
        // Decide which storage to use based on the date
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayKey = sdf.format(Calendar.getInstance().getTime());
        SharedPreferences targetPrefs = sharedPreferences;
        try {
            Date selected = sdf.parse(currentDateKey);
            Date today = sdf.parse(todayKey);
            if (selected != null && today != null && selected.before(today)) {
                targetPrefs = archivePreferences;
            }
        } catch (Exception ignored) {}

        String existingRemarks = targetPrefs.getString(currentDateKey, "");
        String updatedRemarks = existingRemarks.isEmpty() ? entry : existingRemarks + "\n" + entry;
        
        targetPrefs.edit().putString(currentDateKey, updatedRemarks).apply();
        
        loadRemarksForSelectedDate();
        noteInput.setText("");
        updateRemarkHistory();
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "Note added!", Toast.LENGTH_SHORT).show();
    }

    private void updateRemarkHistory() {
        remarkHistoryContainer.removeAllViews();
        archiveHistoryContainer.removeAllViews();
        deletedHistoryContainer.removeAllViews();

        // Count active notes (Today & Future)
        int activeCount = countTotalNotes(sharedPreferences);
        TextView activeTitle = findViewById(R.id.remarkHistoryTitle);
        if (activeTitle != null) {
            String countText = getString(R.string.all_personal_notes) + " (" + activeCount + ")";
            activeTitle.setText(countText);
            activeTitle.setTextColor(Color.GREEN);
        }

        // Show active personal notes (Today & Future)
        loadHistoryFromPrefs(sharedPreferences, remarkHistoryContainer, R.string.no_notes_saved, Color.GREEN);

        // Archive Button
        Button archiveBtn = new Button(this);
        archiveBtn.setText(R.string.archive_all_past_notes);
        archiveBtn.setTextSize(12);
        archiveBtn.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
        archiveBtn.setTextColor(Color.WHITE);
        archiveBtn.setOnClickListener(v -> archiveAllPastNotes());
        remarkHistoryContainer.addView(archiveBtn);

        // Count archived notes
        int archiveCount = countTotalNotes(archivePreferences);
        TextView archiveTitle = findViewById(R.id.archiveHistoryTitle);
        if (archiveTitle != null) {
            String countText = getString(R.string.archive_folder) + " (" + archiveCount + ")";
            archiveTitle.setText(countText);
            archiveTitle.setTextColor(Color.YELLOW);
        }

        // Show archived notes
        loadHistoryFromPrefs(archivePreferences, archiveHistoryContainer, R.string.no_archived_notes, Color.YELLOW);

        // Count deleted notes
        int deletedCount = countTotalNotes(deletedPreferences);
        TextView deletedTitle = findViewById(R.id.deletedHistoryTitle);
        if (deletedTitle != null) {
            String countText = getString(R.string.deleted_notes_title) + " (" + deletedCount + ")";
            deletedTitle.setText(countText);
            deletedTitle.setTextColor(Color.RED);
        }

        // Show deleted notes in RED
        loadHistoryFromPrefs(deletedPreferences, deletedHistoryContainer, R.string.no_deleted_notes, Color.RED);

        // Add "Clear All Deleted Notes" button
        Button clearTrashBtn = new Button(this);
        clearTrashBtn.setText(R.string.clear_trash_btn);
        clearTrashBtn.setTextSize(10);
        clearTrashBtn.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        clearTrashBtn.setTextColor(Color.WHITE);
        clearTrashBtn.setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("Clear Trash")
                .setMessage("Permanently delete all notes in trash?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    deletedPreferences.edit().clear().apply();
                    updateRemarkHistory();
                    Toast.makeText(MainActivity.this, "Trash cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", null)
                .show());
        deletedHistoryContainer.addView(clearTrashBtn);
    }

    private int countTotalNotes(SharedPreferences prefs) {
        int count = 0;
        Map<String, ?> allEntries = prefs.getAll();
        for (Object entry : allEntries.values()) {
            if (entry != null) {
                String val = entry.toString();
                if (!val.isEmpty()) {
                    count += val.split("\n").length;
                }
            }
        }
        return count;
    }

    private void archiveAllPastNotes() {
        int movedCount = archiveAllPastNotesSilent();
        
        updateRemarkHistory();
        loadRemarksForSelectedDate();
        adapter.notifyDataSetChanged();

        if (movedCount > 0) {
            archiveHistoryContainer.setVisibility(View.VISIBLE);
            Toast.makeText(this, movedCount + " dates moved to archive", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No old notes to archive", Toast.LENGTH_SHORT).show();
        }

        // Scroll to the Archive Folder section
        mainScrollView.post(() -> {
            View title = findViewById(R.id.archiveHistoryTitle);
            if (title != null) {
                mainScrollView.smoothScrollTo(0, title.getTop());
            }
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
                    // Move to archive
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
            String value = allEntries.get(dateKey).toString();
            if (value.isEmpty()) continue;
            String[] notes = value.split("\n");

            // Date Header
            TextView dateHeader = new TextView(this);
            String label = dateKey.equals(todayKey) ? "TODAY: " + dateKey : "Date: " + dateKey;
            dateHeader.setText(label);
            dateHeader.setTextColor(container == deletedHistoryContainer ? Color.RED : (dateKey.equals(todayKey) ? Color.GREEN : dateColor));
            dateHeader.setTextSize(13);
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
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(14);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                noteLayout.addView(tv);

                int iconSize = (int) (28 * getResources().getDisplayMetrics().density);

                // Share button for all history notes
                ImageButton shareBtn = new ImageButton(this);
                shareBtn.setImageResource(android.R.drawable.ic_menu_share);
                shareBtn.setBackgroundColor(Color.TRANSPARENT);
                shareBtn.setPadding(4, 4, 4, 4);
                shareBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
                noteLayout.addView(shareBtn, new LinearLayout.LayoutParams(iconSize, iconSize));
                shareBtn.setOnClickListener(v -> {
                    String[] options = {"Share as Text", "Share as .ics File"};
                    new AlertDialog.Builder(this)
                            .setTitle("Share Note")
                            .setItems(options, (dialog, which) -> {
                                if (which == 0) {
                                    String textToShare = noteText.startsWith("• ") ? noteText.substring(2) : noteText;
                                    Intent sendIntent = new Intent();
                                    sendIntent.setAction(Intent.ACTION_SEND);
                                    sendIntent.putExtra(Intent.EXTRA_TEXT, textToShare);
                                    sendIntent.setType("text/plain");
                                    Intent shareIntent = Intent.createChooser(sendIntent, "Share Note via");
                                    startActivity(shareIntent);
                                } else {
                                    shareNoteAsIcs(noteText, dateKey);
                                }
                            })
                            .show();
                });

                if (container == archiveHistoryContainer || container == deletedHistoryContainer) {
                    // Restore button
                    ImageButton restoreBtn = new ImageButton(this);
                    restoreBtn.setImageResource(android.R.drawable.ic_menu_revert);
                    restoreBtn.setBackgroundColor(Color.TRANSPARENT);
                    restoreBtn.setPadding(4, 4, 4, 4);
                    restoreBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    noteLayout.addView(restoreBtn, new LinearLayout.LayoutParams(iconSize, iconSize));
                    restoreBtn.setOnClickListener(v -> restoreSingleNote(dateKey, index, prefs));

                    // Delete button
                    ImageButton delBtn = new ImageButton(this);
                    delBtn.setImageResource(android.R.drawable.ic_menu_delete);
                    delBtn.setBackgroundColor(Color.TRANSPARENT);
                    delBtn.setPadding(4, 4, 4, 4);
                    delBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    noteLayout.addView(delBtn, new LinearLayout.LayoutParams(iconSize, iconSize));

                    if (container == archiveHistoryContainer) {
                        // Move from Archive to Trash
                        delBtn.setOnClickListener(v -> deleteSingleNote(dateKey, index, prefs));
                    } else if (container == deletedHistoryContainer) {
                        // Permanent delete from Trash
                        delBtn.setOnClickListener(v -> deleteSingleNotePermanently(dateKey, index, prefs));
                    }
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
            ImageView flag = itemView.findViewById(R.id.noteFlag);

            Date date = days.get(position);
            Calendar cellCal = Calendar.getInstance();
            cellCal.setTime(date);

            dayText.setText(String.valueOf(cellCal.get(Calendar.DAY_OF_MONTH)));

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String key = sdf.format(date);
            String savedNotes = sharedPreferences.getString(key, "");
            String archivedNotes = archivePreferences.getString(key, "");
            boolean hasPersonalNotes = !savedNotes.isEmpty();
            boolean hasArchivedNotes = !archivedNotes.isEmpty();
            boolean hasNotes = hasPersonalNotes || hasArchivedNotes;

            flag.setVisibility(hasNotes ? View.VISIBLE : View.INVISIBLE);

            if (hasPersonalNotes) {
                flag.setImageTintList(ColorStateList.valueOf(Color.GREEN));
            } else if (hasArchivedNotes) {
                flag.setImageTintList(ColorStateList.valueOf(Color.YELLOW));
            }

            if (cellCal.get(Calendar.MONTH) != currentMonth.get(Calendar.MONTH)) {
                dayText.setTextColor(Color.GRAY);
            } else {
                dayText.setTextColor(Color.WHITE);
            }

            Calendar today = Calendar.getInstance();
            if (cellCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cellCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                cellCal.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)) {
                dayText.setBackgroundResource(R.drawable.today_circle);
                itemView.setBackgroundColor(Color.TRANSPARENT);
            } else if (cellCal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
                cellCal.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH) &&
                cellCal.get(Calendar.DAY_OF_MONTH) == selectedDate.get(Calendar.DAY_OF_MONTH)) {
                dayText.setBackgroundColor(Color.TRANSPARENT);
                itemView.setBackgroundColor(Color.parseColor("#33FFFFFF"));
            } else {
                dayText.setBackgroundColor(Color.TRANSPARENT);
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            itemView.setOnClickListener(v -> {
                // Set the selected date to whatever cell was clicked
                selectedDate.set(cellCal.get(Calendar.YEAR), cellCal.get(Calendar.MONTH), cellCal.get(Calendar.DAY_OF_MONTH));
                
                // If the user clicked a day from a different month (padding days), 
                // move the calendar to that month automatically.
                if (cellCal.get(Calendar.MONTH) != currentMonth.get(Calendar.MONTH)) {
                    calendar.set(Calendar.MONTH, cellCal.get(Calendar.MONTH));
                    calendar.set(Calendar.YEAR, cellCal.get(Calendar.YEAR));
                    updateCalendar();
                } else {
                    // Just update the UI if it's the same month
                    updateDateInfo(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH));
                    notifyDataSetChanged();
                }
            });

            return itemView;
        }
    }
}
