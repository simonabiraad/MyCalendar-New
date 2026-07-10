package com.example.mycalendar2026sar;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.activity.EdgeToEdge;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class SecureBoxActivity extends AppCompatActivity {

    private LinearLayout personalNotesSection, passwordNotesSection, familyNotesSection, workNotesSection;
    private GridLayout personalNotesContainer, passwordNotesContainer, familyNotesContainer, workNotesContainer;
    private EditText personalNoteInput, passwordNoteInput, familyNoteInput, workNoteInput;
    private SharedPreferences securePrefs;
    private static final String PERSONAL_KEY = "personal_notes";
    private static final String PASSWORD_KEY = "password_notes";
    private static final String FAMILY_KEY = "family_notes";
    private static final String WORK_KEY = "work_notes";
    private static final String SEPARATOR = "###NOTE_SEP###";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_secure_box);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        personalNotesSection = findViewById(R.id.personalNotesSection);
        personalNotesContainer = findViewById(R.id.personalNotesContainer);
        personalNoteInput = findViewById(R.id.personalNoteInput);

        passwordNotesSection = findViewById(R.id.passwordNotesSection);
        passwordNotesContainer = findViewById(R.id.passwordNotesContainer);
        passwordNoteInput = findViewById(R.id.passwordNoteInput);

        familyNotesSection = findViewById(R.id.familyNotesSection);
        familyNotesContainer = findViewById(R.id.familyNotesContainer);
        familyNoteInput = findViewById(R.id.familyNoteInput);

        workNotesSection = findViewById(R.id.workNotesSection);
        workNotesContainer = findViewById(R.id.workNotesContainer);
        workNoteInput = findViewById(R.id.workNoteInput);

        Button personalButton = findViewById(R.id.personalButton);
        Button passwordButton = findViewById(R.id.passwordButton);
        Button familyButton = findViewById(R.id.familyButton);
        Button workButton = findViewById(R.id.workButton);

        Button savePersonalBtn = findViewById(R.id.savePersonalNoteButton);
        Button savePasswordBtn = findViewById(R.id.savePasswordNoteButton);
        Button saveFamilyBtn = findViewById(R.id.saveFamilyNoteButton);
        Button saveWorkBtn = findViewById(R.id.saveWorkNoteButton);

        securePrefs = getSharedPreferences("SecureBoxNotes", Context.MODE_PRIVATE);

        personalButton.setOnClickListener(v -> toggleSection(personalNotesSection, PERSONAL_KEY, personalNotesContainer));
        passwordButton.setOnClickListener(v -> toggleSection(passwordNotesSection, PASSWORD_KEY, passwordNotesContainer));
        familyButton.setOnClickListener(v -> toggleSection(familyNotesSection, FAMILY_KEY, familyNotesContainer));
        workButton.setOnClickListener(v -> toggleSection(workNotesSection, WORK_KEY, workNotesContainer));

        savePersonalBtn.setOnClickListener(v -> saveNote(PERSONAL_KEY, personalNoteInput, personalNotesContainer));
        savePasswordBtn.setOnClickListener(v -> saveNote(PASSWORD_KEY, passwordNoteInput, passwordNotesContainer));
        saveFamilyBtn.setOnClickListener(v -> saveNote(FAMILY_KEY, familyNoteInput, familyNotesContainer));
        saveWorkBtn.setOnClickListener(v -> saveNote(WORK_KEY, workNoteInput, workNotesContainer));
    }

    private void toggleSection(LinearLayout section, String key, GridLayout container) {
        boolean wasVisible = section.getVisibility() == View.VISIBLE;
        hideAllSections();
        if (!wasVisible) {
            section.setVisibility(View.VISIBLE);
            loadNotes(key, container);
        }
    }

    private void hideAllSections() {
        personalNotesSection.setVisibility(View.GONE);
        passwordNotesSection.setVisibility(View.GONE);
        familyNotesSection.setVisibility(View.GONE);
        workNotesSection.setVisibility(View.GONE);
    }

    private void saveNote(String key, EditText input, GridLayout container) {
        String note = input.getText().toString().trim();
        if (note.isEmpty()) {
            Toast.makeText(this, "Please enter a note", Toast.LENGTH_SHORT).show();
            return;
        }

        String existingNotes = securePrefs.getString(key, "");
        String updatedNotes = existingNotes.isEmpty() ? note : existingNotes + SEPARATOR + note;
        securePrefs.edit().putString(key, updatedNotes).apply();

        input.setText("");
        loadNotes(key, container);
        Toast.makeText(this, "Note saved!", Toast.LENGTH_SHORT).show();
    }

    private void loadNotes(String key, GridLayout container) {
        container.removeAllViews();
        String notesStr = securePrefs.getString(key, "");
        if (!notesStr.isEmpty()) {
            String[] rawArray = notesStr.split(SEPARATOR);
            List<String> notesList = new ArrayList<>();
            for (String s : rawArray) {
                if (!s.trim().isEmpty()) {
                    notesList.add(s);
                }
            }

            int count = notesList.size();
            container.setColumnCount(count == 1 ? 1 : 2);

            LayoutInflater inflater = LayoutInflater.from(this);
            for (int i = 0; i < count; i++) {
                final String note = notesList.get(i);
                final int index = i;
                View noteView = inflater.inflate(R.layout.sticky_note_item, container, false);
                TextView tv = noteView.findViewById(R.id.noteText);
                MaterialCardView card = noteView.findViewById(R.id.cardView);

                tv.setText(note);

                if (key.equals(PERSONAL_KEY)) {
                    card.setCardBackgroundColor(getColor(R.color.light_green));
                } else if (key.equals(PASSWORD_KEY)) {
                    card.setCardBackgroundColor(getColor(R.color.unmellow_yellow));
                } else if (key.equals(FAMILY_KEY)) {
                    card.setCardBackgroundColor(getColor(R.color.blue));
                } else if (key.equals(WORK_KEY)) {
                    card.setCardBackgroundColor(getColor(R.color.honey));
                }

                noteView.setOnClickListener(v -> showEditFullPage(key, index, note, container));

                noteView.setOnLongClickListener(v -> {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Delete Note")
                            .setMessage("Are you sure you want to delete this note?")
                            .setPositiveButton("Yes", (dialog, which) -> deleteNote(key, index, container))
                            .setNegativeButton("No", null)
                            .show();
                    return true;
                });

                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                if (count == 1) {
                    params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.7);
                    params.columnSpec = GridLayout.spec(0, 1, GridLayout.CENTER, 1f);
                } else {
                    params.width = 0;
                    params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
                }
                params.height = GridLayout.LayoutParams.WRAP_CONTENT;
                noteView.setLayoutParams(params);

                container.addView(noteView);
            }
        }
    }

    private void showEditFullPage(String key, int index, String currentText, GridLayout container) {
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        // Let's create a simple one here.
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.BLACK);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Edit Note");
        title.setTextColor(getColor(R.color.light_green));
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 16);
        layout.addView(title);

        EditText edit = new EditText(this);
        edit.setText(currentText);
        edit.setTextColor(Color.WHITE);
        edit.setGravity(android.view.Gravity.TOP);
        edit.setBackground(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        edit.setLayoutParams(lp);
        layout.addView(edit);

        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setGravity(android.view.Gravity.END);

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(v -> dialog.dismiss());
        btnLayout.addView(cancel);

        Button save = new Button(this);
        save.setText("Save");
        save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.light_green)));
        save.setOnClickListener(v -> {
            String newText = edit.getText().toString().trim();
            if (!newText.isEmpty()) {
                updateNote(key, index, newText, container);
                dialog.dismiss();
            }
        });
        btnLayout.addView(save);

        layout.addView(btnLayout);
        dialog.setContentView(layout);
        dialog.show();
    }

    private void updateNote(String key, int index, String newText, GridLayout container) {
        String notesStr = securePrefs.getString(key, "");
        if (notesStr.isEmpty()) return;
        String[] rawArray = notesStr.split(SEPARATOR);
        List<String> notesList = new ArrayList<>();
        for (String s : rawArray) {
            if (!s.trim().isEmpty()) {
                notesList.add(s);
            }
        }
        if (index >= 0 && index < notesList.size()) {
            notesList.set(index, newText);
            String updatedNotes = String.join(SEPARATOR, notesList);
            securePrefs.edit().putString(key, updatedNotes).apply();
            loadNotes(key, container);
            Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteNote(String key, int index, GridLayout container) {
        String notesStr = securePrefs.getString(key, "");
        if (notesStr.isEmpty()) return;

        String[] rawArray = notesStr.split(SEPARATOR);
        List<String> notesList = new ArrayList<>();
        for (String s : rawArray) {
            if (!s.trim().isEmpty()) {
                notesList.add(s);
            }
        }

        if (index >= 0 && index < notesList.size()) {
            notesList.remove(index);
            String updatedNotes = String.join(SEPARATOR, notesList);
            securePrefs.edit().putString(key, updatedNotes).apply();
            loadNotes(key, container);
            Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show();
        }
    }
}
