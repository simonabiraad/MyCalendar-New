package com.example.mycalendar2026sar;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
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

    private LinearLayout personelNotesSection, familyNotesSection, workNotesSection, passwordNotesSection;
    private GridLayout personelNotesContainer, familyNotesContainer, workNotesContainer, passwordNotesContainer;
    private EditText personelNoteInput, familyNoteInput, workNoteInput, passwordNoteInput;
    private SharedPreferences securePrefs;
    private static final String PERSONEL_KEY = "personel_notes";
    private static final String FAMILY_KEY = "family_notes";
    private static final String WORK_KEY = "work_notes";
    private static final String PASSWORD_KEY = "password_notes";
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

        personelNotesSection = findViewById(R.id.personelNotesSection);
        personelNotesContainer = findViewById(R.id.personelNotesContainer);
        personelNoteInput = findViewById(R.id.personelNoteInput);
        
        familyNotesSection = findViewById(R.id.familyNotesSection);
        familyNotesContainer = findViewById(R.id.familyNotesContainer);
        familyNoteInput = findViewById(R.id.familyNoteInput);

        workNotesSection = findViewById(R.id.workNotesSection);
        workNotesContainer = findViewById(R.id.workNotesContainer);
        workNoteInput = findViewById(R.id.workNoteInput);

        passwordNotesSection = findViewById(R.id.passwordNotesSection);
        passwordNotesContainer = findViewById(R.id.passwordNotesContainer);
        passwordNoteInput = findViewById(R.id.passwordNoteInput);

        Button personelButton = findViewById(R.id.personelButton);
        Button familyButton = findViewById(R.id.familyButton);
        Button workButton = findViewById(R.id.workButton);
        Button passwordButton = findViewById(R.id.passwordButton);
        
        Button savePersonelBtn = findViewById(R.id.savePersonelNoteButton);
        Button saveFamilyBtn = findViewById(R.id.saveFamilyNoteButton);
        Button saveWorkBtn = findViewById(R.id.saveWorkNoteButton);
        Button savePasswordBtn = findViewById(R.id.savePasswordNoteButton);

        securePrefs = getSharedPreferences("SecureBoxNotes", Context.MODE_PRIVATE);

        personelButton.setOnClickListener(v -> {
            hideAllSections();
            if (personelNotesSection.getVisibility() == View.VISIBLE) {
                personelNotesSection.setVisibility(View.GONE);
            } else {
                personelNotesSection.setVisibility(View.VISIBLE);
                loadNotes(PERSONEL_KEY, personelNotesContainer);
            }
        });

        familyButton.setOnClickListener(v -> {
            hideAllSections();
            if (familyNotesSection.getVisibility() == View.VISIBLE) {
                familyNotesSection.setVisibility(View.GONE);
            } else {
                familyNotesSection.setVisibility(View.VISIBLE);
                loadNotes(FAMILY_KEY, familyNotesContainer);
            }
        });

        workButton.setOnClickListener(v -> {
            hideAllSections();
            if (workNotesSection.getVisibility() == View.VISIBLE) {
                workNotesSection.setVisibility(View.GONE);
            } else {
                workNotesSection.setVisibility(View.VISIBLE);
                loadNotes(WORK_KEY, workNotesContainer);
            }
        });

        passwordButton.setOnClickListener(v -> {
            hideAllSections();
            if (passwordNotesSection.getVisibility() == View.VISIBLE) {
                passwordNotesSection.setVisibility(View.GONE);
            } else {
                passwordNotesSection.setVisibility(View.VISIBLE);
                loadNotes(PASSWORD_KEY, passwordNotesContainer);
            }
        });

        savePersonelBtn.setOnClickListener(v -> saveNote(PERSONEL_KEY, personelNoteInput, personelNotesContainer));
        saveFamilyBtn.setOnClickListener(v -> saveNote(FAMILY_KEY, familyNoteInput, familyNotesContainer));
        saveWorkBtn.setOnClickListener(v -> saveNote(WORK_KEY, workNoteInput, workNotesContainer));
        savePasswordBtn.setOnClickListener(v -> saveNote(PASSWORD_KEY, passwordNoteInput, passwordNotesContainer));
    }

    private void hideAllSections() {
        personelNotesSection.setVisibility(View.GONE);
        familyNotesSection.setVisibility(View.GONE);
        workNotesSection.setVisibility(View.GONE);
        passwordNotesSection.setVisibility(View.GONE);
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
                
                if (key.equals(PERSONEL_KEY)) {
                    card.setCardBackgroundColor(getColor(R.color.light_green));
                } else if (key.equals(FAMILY_KEY)) {
                    card.setCardBackgroundColor(getColor(R.color.blue));
                } else if (key.equals(WORK_KEY)) {
                    card.setCardBackgroundColor(getColor(R.color.honey));
                } else if (key.equals(PASSWORD_KEY)) {
                    card.setCardBackgroundColor(getColor(R.color.unmellow_yellow));
                }

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
