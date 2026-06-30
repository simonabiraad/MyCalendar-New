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
        // Check both active and archived storage
        String savedRemarks = sharedPreferences.getString(currentDateKey, "");
        String archivedRemarks = archivePreferences.getString(currentDateKey, "");
        String deletedRemarks = deletedPreferences.getString(currentDateKey, "");
        
        String totalRemarks;
        if (!archivedRemarks.isEmpty()) {
            totalRemarks = savedRemarks.isEmpty() ? archivedRemarks : savedRemarks + "\n" + archivedRemarks;
        } else {
            totalRemarks = savedRemarks;
        }
        
        if (!deletedRemarks.isEmpty()) {
            totalRemarks = totalRemarks.isEmpty() ? deletedRemarks : totalRemarks + "\n" + deletedRemarks;
        }

        if (totalRemarks.isEmpty()) {
            TextView noRemarks = new TextView(this);
            noRemarks.setText(getString(R.string.no_notes_day));
            noRemarks.setTextColor(Color.GREEN);
            dayRemarksContainer.addView(noRemarks);
        } else {
            String[] remarksArray = totalRemarks.split("\n");
            for (int index = 0; index < remarksArray.length; index++) {
                String remarkText = remarksArray[index];
                addRemarkView(remarkText, index);
            }
        }
    }

    private void addRemarkView(String remarkText, int index) {
        LinearLayout horizontalLayout = new LinearLayout(this);
        horizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
        horizontalLayout.setGravity(Gravity.CENTER_VERTICAL);
        horizontalLayout.setPadding(0, 4, 0, 4);

        TextView textView = new TextView(this);
        textView.setText(remarkText);
        textView.setTextColor(Color.WHITE);
        
        // Long click to copy note to clipboard
        textView.setOnLongClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            String textToCopy = remarkText.startsWith("• ") ? remarkText.substring(2) : remarkText;
            android.content.ClipData clip = android.content.ClipData.newPlainText("SAR Note", textToCopy);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Note copied to clipboard", Toast.LENGTH_SHORT).show();
            return true;
        });

        textView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        int iconSize = (int) (23 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        btnParams.setMargins(4, 0, 4, 0);

        ImageButton alarmButton = new ImageButton(this);
        alarmButton.setImageResource(android.R.drawable.ic_lock_idle_alarm);
        alarmButton.setBackgroundColor(Color.TRANSPARENT);
        alarmButton.setLayoutParams(btnParams);
        alarmButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        alarmButton.setPadding(4, 4, 4, 4);
        alarmButton.setOnClickListener(v -> showTimePickerDialog(remarkText));

        ImageButton editButton = new ImageButton(this);
        editButton.setImageResource(android.R.drawable.ic_menu_edit);
        editButton.setBackgroundColor(Color.TRANSPARENT);
        editButton.setLayoutParams(btnParams);
        editButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        editButton.setPadding(4, 4, 4, 4);
        editButton.setOnClickListener(v -> showEditDialog(remarkText, index));

        ImageButton archiveButton = new ImageButton(this);
        archiveButton.setImageResource(android.R.drawable.ic_menu_save);
        archiveButton.setBackgroundColor(Color.TRANSPARENT);
        archiveButton.setLayoutParams(btnParams);
        archiveButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        archiveButton.setPadding(4, 4, 4, 4);
        
        // Show archive icon for active notes, show unarchive icon for archived notes
        boolean isArchived = archivePreferences.contains(currentDateKey) && 
                            archivePreferences.getString(currentDateKey, "").contains(remarkText);
        
        if (isArchived) {
            archiveButton.setImageResource(android.R.drawable.ic_menu_revert);
            archiveButton.setOnClickListener(v -> unarchiveNote(index));
        } else {
            archiveButton.setImageResource(android.R.drawable.ic_menu_save);
            archiveButton.setOnClickListener(v -> archiveNote(index));
        }

        ImageButton deleteButton = new ImageButton(this);
        deleteButton.setImageResource(android.R.drawable.ic_menu_delete);
        deleteButton.setBackgroundColor(Color.TRANSPARENT);
        deleteButton.setLayoutParams(btnParams);
        deleteButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        deleteButton.setPadding(4, 4, 4, 4);
        deleteButton.setOnClickListener(v -> deleteRemark(index));

        horizontalLayout.addView(textView);
        horizontalLayout.addView(alarmButton);
        horizontalLayout.addView(editButton);
        horizontalLayout.addView(archiveButton);
        horizontalLayout.addView(deleteButton);
        dayRemarksContainer.addView(horizontalLayout);
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

        TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, selectedHour, selectedMinute) -> {
            setReminder(selectedHour, selectedMinute, noteText);
        }, hour, minute, true);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime.getTimeInMillis(), pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTime.getTimeInMillis(), pendingIntent);
        }

        Toast.makeText(this, "Reminder set for " + String.format(Locale.getDefault(), "%02d:%02d", hour, minute), Toast.LENGTH_SHORT).show();
    }

    private boolean isArchivedNote(String dateKey) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date noteDate = sdf.parse(dateKey);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date today = cal.getTime();
            return noteDate != null && noteDate.before(today);
        } catch (Exception e) {
            return false;
        }
    }

    private void showEditDialog(String currentText, int index) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Note");

        final EditText input = new EditText(this);
        input.setText(currentText.startsWith("• ") ? currentText.substring(2) : currentText);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String updatedText = input.getText().toString().trim();
            if (!updatedText.isEmpty()) {
                updateRemark(updatedText, index);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updateRemark(String newText, int index) {
        // Find which storage the notes are currently in
        String savedRemarks = sharedPreferences.getString(currentDateKey, "");
        String archivedRemarks = archivePreferences.getString(currentDateKey, "");
        SharedPreferences targetPrefs = !savedRemarks.isEmpty() ? sharedPreferences : archivePreferences;
        String currentText = !savedRemarks.isEmpty() ? savedRemarks : archivedRemarks;

        if (currentText.isEmpty()) return;

        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            remarksList.set(index, "• " + newText);
            String updatedRemarks = String.join("\n", remarksList);
            targetPrefs.edit().putString(currentDateKey, updatedRemarks).apply();
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

    private void deleteRemark(int index) {
        String[] options = {"Move to Trash", "Delete Permanently"};
        new AlertDialog.Builder(this)
                .setTitle("Delete Note")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        performDeleteRemark(index);
                    } else {
                        performPermanentDelete(index);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performPermanentDelete(int index) {
        // Find which storage contains the note
        String savedRemarks = sharedPreferences.getString(currentDateKey, "");
        String archivedRemarks = archivePreferences.getString(currentDateKey, "");
        String deletedRemarks = deletedPreferences.getString(currentDateKey, "");
        
        SharedPreferences targetPrefs;
        String currentText;
        
        // Use a logic to find the correct note and its original container
        if (!savedRemarks.isEmpty() && index < savedRemarks.split("\n").length) {
            targetPrefs = sharedPreferences;
            currentText = savedRemarks;
        } else if (!archivedRemarks.isEmpty()) {
            int offset = savedRemarks.isEmpty() ? 0 : savedRemarks.split("\n").length;
            if (index >= offset && index < offset + archivedRemarks.split("\n").length) {
                targetPrefs = archivePreferences;
                currentText = archivedRemarks;
                index -= offset;
            } else {
                int delOffset = offset + archivedRemarks.split("\n").length;
                targetPrefs = deletedPreferences;
                currentText = deletedRemarks;
                index -= delOffset;
            }
        } else {
            targetPrefs = deletedPreferences;
            currentText = deletedRemarks;
        }

        if (currentText.isEmpty()) return;

        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            remarksList.remove(index);
            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) {
                targetPrefs.edit().remove(currentDateKey).apply();
            } else {
                targetPrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            }
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Permanently Deleted", Toast.LENGTH_SHORT).show();
        }
    }

    private void performDeleteRemark(int index) {
        // Find which storage contains the note
        String savedRemarks = sharedPreferences.getString(currentDateKey, "");
        String archivedRemarks = archivePreferences.getString(currentDateKey, "");
        
        SharedPreferences targetPrefs;
        String currentText;
        int originalIndex = index;
        
        if (!savedRemarks.isEmpty() && index < savedRemarks.split("\n").length) {
            targetPrefs = sharedPreferences;
            currentText = savedRemarks;
        } else {
            int offset = savedRemarks.isEmpty() ? 0 : savedRemarks.split("\n").length;
            targetPrefs = archivePreferences;
            currentText = archivedRemarks;
            originalIndex = index - offset;
        }

        if (currentText.isEmpty()) return;

        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (originalIndex >= 0 && originalIndex < remarksList.size()) {
            // Save for undo
            lastDeletedNote = remarksList.get(originalIndex);
            lastDeletedIndex = originalIndex;
            lastDeletedDateKey = currentDateKey;

            remarksList.remove(originalIndex);
            
            // Move to deleted notes storage
            String currentDeleted = deletedPreferences.getString(currentDateKey, "");
            String updatedDeleted = currentDeleted.isEmpty() ? lastDeletedNote : currentDeleted + "\n" + lastDeletedNote;
            deletedPreferences.edit().putString(currentDateKey, updatedDeleted).apply();

            String updatedRemarks = String.join("\n", remarksList);
            
            if (updatedRemarks.isEmpty()) {
                targetPrefs.edit().remove(currentDateKey).apply();
            } else {
                targetPrefs.edit().putString(currentDateKey, updatedRemarks).apply();
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
        int activeCount = sharedPreferences.getAll().size();
        TextView title = findViewById(R.id.remarkHistoryTitle);
        if (title != null) {
            String countText = getString(R.string.all_personal_notes) + " (" + activeCount + ")";
            title.setText(countText);
        }

        // Show active personal notes (Today & Future)
        loadHistoryFromPrefs(sharedPreferences, remarkHistoryContainer, R.string.no_notes_saved, Color.GREEN);

        // Archive Button
        Button archiveBtn = new Button(this);
        archiveBtn.setText("Archive all past notes");
        archiveBtn.setTextSize(12);
        archiveBtn.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
        archiveBtn.setTextColor(Color.WHITE);
        archiveBtn.setOnClickListener(v -> archiveAllPastNotes());
        remarkHistoryContainer.addView(archiveBtn);

        // Show archived notes
        loadHistoryFromPrefs(archivePreferences, archiveHistoryContainer, R.string.no_archived_notes, Color.YELLOW);

        // Show deleted notes in RED
        loadHistoryFromPrefs(deletedPreferences, deletedHistoryContainer, R.string.no_deleted_notes, Color.RED);

        // Add "Clear All Deleted Notes" button
        Button clearTrashBtn = new Button(this);
        clearTrashBtn.setText("Clear All Deleted Notes");
        clearTrashBtn.setTextSize(10);
        clearTrashBtn.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        clearTrashBtn.setTextColor(Color.WHITE);
        clearTrashBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear Trash")
                    .setMessage("Permanently delete all notes in trash?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        deletedPreferences.edit().clear().apply();
                        updateRemarkHistory();
                        Toast.makeText(this, "Trash cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
        deletedHistoryContainer.addView(clearTrashBtn);
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
            // Filter out today's notes from the Archive Folder list
            if (container == archiveHistoryContainer && dateKey.equals(todayKey)) continue;

            Object valObj = allEntries.get(dateKey);
            if (valObj == null) continue;
            String value = valObj.toString();
            if (value.isEmpty()) continue;

            View historyItem = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, container, false);
            TextView text1 = historyItem.findViewById(android.R.id.text1);
            TextView text2 = historyItem.findViewById(android.R.id.text2);
            
            String label = dateKey.equals(todayKey) ? "TODAY: " + dateKey : "Date: " + dateKey;
            text1.setText(label);
            // If in deleted folder, always use RED for the date label
            text1.setTextColor((container == deletedHistoryContainer) ? Color.RED : (dateKey.equals(todayKey) ? Color.GREEN : dateColor));
            text1.setTextSize(12);
            text1.setPadding(0, 8, 0, 0);
            
            text2.setText(value);
            // Note content is now always white
            text2.setTextColor(Color.WHITE);
            text2.setTextSize(14);
            text2.setPadding(0, 4, 0, 12);
            
            // Long click to copy history note
            historyItem.setOnLongClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("SAR Note", value);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Notes for " + dateKey + " copied", Toast.LENGTH_SHORT).show();
                return true;
            });
            
            historyItem.setOnClickListener(v -> {
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
                        Toast.makeText(this, "Opening notes for " + dateKey, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ignored) {}
            });
            container.addView(historyItem);
        }
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
            boolean hasNotes = !savedNotes.isEmpty() || !archivedNotes.isEmpty();

            flag.setVisibility(hasNotes ? View.VISIBLE : View.INVISIBLE);

            if (hasNotes) {
                dayText.setTextColor(Color.WHITE);
            } else if (cellCal.get(Calendar.MONTH) != currentMonth.get(Calendar.MONTH)) {
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
