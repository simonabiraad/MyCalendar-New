package com.example.mycalendar2026sar;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SecureBoxActivity extends AppCompatActivity {

    private LinearLayout notesSection;
    private GridLayout notesContainer;
    private EditText noteTitleInput, noteContentInput;
    private ScrollView notesScrollView;
    private LinearLayout buttonContainer;
    private SharedPreferences securePrefs, colorPrefs, fontPrefs, categoryPrefs;
    
    private String activeCategoryKey = "";
    private int activeCategoryColor = 0;

    private static final String SEPARATOR = "###NOTE_SEP###";
    private static final String TITLE_SEP = "###TITLE_SEP###";

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

        colorPrefs = getSharedPreferences("AppColors", Context.MODE_PRIVATE);
        securePrefs = getSharedPreferences("SecureBoxNotes", Context.MODE_PRIVATE);
        fontPrefs = getSharedPreferences("AppFonts", Context.MODE_PRIVATE);
        categoryPrefs = getSharedPreferences("SecureBoxCategories", Context.MODE_PRIVATE);

        initViews();
        loadCategories();
        refreshColors();
        setupAutoScroll();

        String action = getIntent().getStringExtra("action");
        if ("new_note".equals(action)) {
            // Default to Personal if coming from "New Sticky Note" shortcut
            selectCategory("personal_notes", colorPrefs.getInt("color_sb_personal", getColor(R.color.light_green)));
            noteTitleInput.requestFocus();
        }
    }

    private void initViews() {
        notesSection = findViewById(R.id.notesSection);
        notesContainer = findViewById(R.id.notesContainer);
        noteTitleInput = findViewById(R.id.noteTitleInput);
        noteContentInput = findViewById(R.id.noteContentInput);
        notesScrollView = findViewById(R.id.notesScrollView);
        buttonContainer = findViewById(R.id.buttonContainer);

        findViewById(R.id.saveStickyNoteButton).setOnClickListener(v -> saveNote());
        findViewById(R.id.moveCategoryLeftButton).setOnClickListener(v -> moveCategory(activeCategoryKey, -1));
        findViewById(R.id.moveCategoryRightButton).setOnClickListener(v -> moveCategory(activeCategoryKey, 1));
        findViewById(R.id.addCategoryButton).setOnClickListener(v -> showAddCategoryDialog());
    }

    private void loadCategories() {
        // Clear except the + button
        View addButton = findViewById(R.id.addCategoryButton);
        buttonContainer.removeAllViews();
        
        // Add default categories if not exists
        if (categoryPrefs.getAll().isEmpty()) {
            categoryPrefs.edit()
                    .putString("personal_notes", "Personal")
                    .putString("password_notes", "Password")
                    .putString("family_notes", "Family")
                    .putString("work_notes", "Work")
                    .putString("others_notes", "Others")
                    .putString("cats_order", "personal_notes,password_notes,family_notes,work_notes,others_notes")
                    .apply();
        }

        String orderStr = categoryPrefs.getString("cats_order", "");
        if (orderStr.isEmpty()) {
            Map<String, ?> all = categoryPrefs.getAll();
            List<String> keys = new ArrayList<>();
            for (String k : all.keySet()) {
                if (!k.equals("cats_order")) keys.add(k);
            }
            orderStr = String.join(",", keys);
        }

        String[] order = orderStr.split(",");
        for (String key : order) {
            String name = categoryPrefs.getString(key, null);
            if (name != null) {
                addCategoryButtonToUI(key, name);
            }
        }
        
        buttonContainer.addView(addButton);
    }

    private void addCategoryButtonToUI(String key, String name) {
        Button btn = new Button(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 8, 0);
        btn.setLayoutParams(params);
        btn.setText(name);
        btn.setAllCaps(false);
        btn.setPadding(24, 0, 24, 0);
        btn.setTextColor(Color.WHITE);
        applyFontSettings(btn, 10);

        int defaultColor;
        if (key.equals("personal_notes")) defaultColor = getColor(R.color.light_green);
        else if (key.equals("password_notes")) { defaultColor = getColor(R.color.unmellow_yellow); btn.setTextColor(Color.BLACK); }
        else if (key.equals("family_notes")) defaultColor = getColor(R.color.blue);
        else if (key.equals("work_notes")) defaultColor = getColor(R.color.honey);
        else if (key.equals("others_notes")) defaultColor = getColor(R.color.teal_200);
        else defaultColor = colorPrefs.getInt("color_cat_" + key, Color.GRAY);

        int color = colorPrefs.getInt("color_sb_" + key.replace("_notes", ""), defaultColor);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));

        btn.setOnClickListener(v -> selectCategory(key, color));
        
        btn.setOnLongClickListener(v -> {
            showCategoryOptionsDialog(key, btn);
            return true;
        });

        buttonContainer.addView(btn);
    }

    private void selectCategory(String key, int color) {
        activeCategoryKey = key;
        activeCategoryColor = color;
        notesSection.setVisibility(View.VISIBLE);
        
        // Update Save Button color to match category
        findViewById(R.id.saveStickyNoteButton).setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        if (color == getColor(R.color.unmellow_yellow)) {
            ((Button)findViewById(R.id.saveStickyNoteButton)).setTextColor(Color.BLACK);
        } else {
            ((Button)findViewById(R.id.saveStickyNoteButton)).setTextColor(Color.WHITE);
        }

        loadNotes(key);
    }

    private void showAddCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Category");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Category Name");
        layout.addView(nameInput);

        builder.setView(layout);
        builder.setPositiveButton("Next: Choose Color", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            if (!name.isEmpty()) {
                showColorPickerForCategory(name);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showColorPickerForCategory(String name) {
        String[] colorNames = {"Green", "Blue", "Red", "Orange", "Purple", "Teal", "Grey", "Pink"};
        int[] colorValues = {0xFF4CAF50, 0xFF2196F3, 0xFFF44336, 0xFFFF9800, 0xFF9C27B0, 0xFF009688, 0xFF9E9E9E, 0xFFE91E63};

        new AlertDialog.Builder(this)
                .setTitle("Pick Color for " + name)
                .setItems(colorNames, (dialog, which) -> {
                    String key = name.toLowerCase().replace(" ", "_") + "_notes_" + System.currentTimeMillis();
                    categoryPrefs.edit().putString(key, name).apply();
                    
                    // Update order
                    String order = categoryPrefs.getString("cats_order", "");
                    if (!order.isEmpty()) order += ",";
                    order += key;
                    categoryPrefs.edit().putString("cats_order", order).apply();

                    colorPrefs.edit().putInt("color_sb_" + key.replace("_notes", ""), colorValues[which]).apply();
                    loadCategories();
                    selectCategory(key, colorValues[which]);
                })
                .show();
    }

    private void showCategoryOptionsDialog(String key, Button button) {
        String[] options = {"Rename", "Change Color", "Move Left", "Move Right", "Delete Category"};
        new AlertDialog.Builder(this)
                .setTitle("Category Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showRenameDialog(key, button);
                    else if (which == 1) showColorPickerForExisting(key, button);
                    else if (which == 2) moveCategory(key, -1);
                    else if (which == 3) moveCategory(key, 1);
                    else if (which == 4) showDeleteCategoryConfirm(key);
                })
                .show();
    }

    private void moveCategory(String key, int direction) {
        String orderStr = categoryPrefs.getString("cats_order", "");
        if (orderStr.isEmpty()) return;
        List<String> order = new ArrayList<>(Arrays.asList(orderStr.split(",")));
        int index = order.indexOf(key);
        if (index == -1) return;
        int newIndex = index + direction;
        if (newIndex >= 0 && newIndex < order.size()) {
            order.remove(index);
            order.add(newIndex, key);
            categoryPrefs.edit().putString("cats_order", String.join(",", order)).apply();
            loadCategories();
        }
    }

    private void showRenameDialog(String key, Button button) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Category");
        final EditText input = new EditText(this);
        input.setText(button.getText().toString());
        builder.setView(input);
        builder.setPositiveButton("OK", (d, w) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                categoryPrefs.edit().putString(key, newName).apply();
                button.setText(newName);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showColorPickerForExisting(String key, Button button) {
        String[] colorNames = {"Green", "Blue", "Red", "Orange", "Purple", "Teal", "Grey", "Pink"};
        int[] colorValues = {0xFF4CAF50, 0xFF2196F3, 0xFFF44336, 0xFFFF9800, 0xFF9C27B0, 0xFF009688, 0xFF9E9E9E, 0xFFE91E63};

        new AlertDialog.Builder(this)
                .setTitle("Pick New Color")
                .setItems(colorNames, (dialog, which) -> {
                    int color = colorValues[which];
                    colorPrefs.edit().putInt("color_sb_" + key.replace("_notes", ""), color).apply();
                    button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                    if (activeCategoryKey.equals(key)) selectCategory(key, color);
                })
                .show();
    }

    private void showDeleteCategoryConfirm(String key) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure? This will delete the category and all notes inside it.")
                .setPositiveButton("Yes", (dialog, which) -> {
                    categoryPrefs.edit().remove(key).apply();
                    securePrefs.edit().remove(key).apply();
                    
                    // Update order
                    String orderStr = categoryPrefs.getString("cats_order", "");
                    if (!orderStr.isEmpty()) {
                        List<String> order = new ArrayList<>(Arrays.asList(orderStr.split(",")));
                        order.remove(key);
                        categoryPrefs.edit().putString("cats_order", String.join(",", order)).apply();
                    }

                    if (activeCategoryKey.equals(key)) notesSection.setVisibility(View.GONE);
                    loadCategories();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void saveNote() {
        String title = noteTitleInput.getText().toString().trim();
        String content = noteContentInput.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "Please enter a note", Toast.LENGTH_SHORT).show();
            return;
        }

        String fullNote = (title.isEmpty() ? "No Name" : title) + TITLE_SEP + content;
        String existingNotes = securePrefs.getString(activeCategoryKey, "");
        String updatedNotes = existingNotes.isEmpty() ? fullNote : existingNotes + SEPARATOR + fullNote;
        securePrefs.edit().putString(activeCategoryKey, updatedNotes).apply();

        noteTitleInput.setText("");
        noteContentInput.setText("");
        loadNotes(activeCategoryKey);
        notesScrollView.postDelayed(() -> notesScrollView.fullScroll(View.FOCUS_DOWN), 100);
        Toast.makeText(this, "Note saved!", Toast.LENGTH_SHORT).show();
    }

    private void loadNotes(String key) {
        notesContainer.removeAllViews();
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
            notesContainer.setColumnCount(count == 1 ? 1 : 2);

            LayoutInflater inflater = LayoutInflater.from(this);
            for (int i = 0; i < count; i++) {
                final String rawNote = notesList.get(i);
                String title = "Note";
                String content = rawNote;
                if (rawNote.contains(TITLE_SEP)) {
                    String[] parts = rawNote.split(TITLE_SEP);
                    title = parts[0];
                    content = parts.length > 1 ? parts[1] : "";
                }

                final int index = i;
                final String finalTitle = title;
                final String finalContent = content;

                View noteView = inflater.inflate(R.layout.sticky_note_item, notesContainer, false);
                TextView titleTv = noteView.findViewById(R.id.noteTitle);
                TextView contentTv = noteView.findViewById(R.id.noteText);
                MaterialCardView card = noteView.findViewById(R.id.cardView);

                titleTv.setText(title);
                contentTv.setText(content);
                applyFontSettings(titleTv, 14);
                applyFontSettings(contentTv, 12);

                card.setCardBackgroundColor(activeCategoryColor);

                View moveLeft = noteView.findViewById(R.id.moveLeftBtn);
                View moveRight = noteView.findViewById(R.id.moveRightBtn);

                moveLeft.setVisibility(i > 0 ? View.VISIBLE : View.INVISIBLE);
                moveRight.setVisibility(i < count - 1 ? View.VISIBLE : View.INVISIBLE);

                moveLeft.setOnClickListener(v -> moveNote(key, index, index - 1));
                moveRight.setOnClickListener(v -> moveNote(key, index, index + 1));

                noteView.setOnClickListener(v -> showEditFullPage(key, index, finalTitle, finalContent, notesContainer));

                noteView.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete Note")
                            .setMessage("Are you sure you want to delete this note?")
                            .setPositiveButton("Yes", (dialog, which) -> deleteNote(key, index, notesContainer))
                            .setNegativeButton("No", null)
                            .show();
                    return true;
                });

                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                if (count == 1) {
                    params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.7);
                    params.columnSpec = GridLayout.spec(0, 1, GridLayout.START, 1f);
                } else {
                    params.width = 0;
                    params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
                }
                params.height = GridLayout.LayoutParams.WRAP_CONTENT;
                noteView.setLayoutParams(params);

                notesContainer.addView(noteView);
            }
        }
    }

    private void showEditFullPage(String key, int index, String currentTitle, String currentContent, GridLayout container) {
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        int bgColor = activeCategoryColor;
        int textColor = (bgColor == getColor(R.color.unmellow_yellow)) ? Color.BLACK : Color.BLACK; 
        // Actually the current logic uses Black text for sticky notes, so let's stick with that.

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(bgColor);
        layout.setPadding(32, 32, 32, 32);

        EditText titleEdit = new EditText(this);
        titleEdit.setText(currentTitle);
        titleEdit.setTextColor(textColor);
        applyFontSettings(titleEdit, 22);
        titleEdit.setHint("Title (Name)");
        titleEdit.setPadding(0, 0, 0, 16);
        layout.addView(titleEdit);

        EditText contentEdit = new EditText(this);
        contentEdit.setText(currentContent);
        contentEdit.setTextColor(textColor);
        applyFontSettings(contentEdit, 18);
        contentEdit.setGravity(android.view.Gravity.START | android.view.Gravity.TOP);
        contentEdit.setBackground(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentEdit.setLayoutParams(lp);
        layout.addView(contentEdit);

        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setGravity(android.view.Gravity.END);

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setTextColor(textColor);
        applyFontSettings(cancel, 14);
        cancel.setOnClickListener(v -> dialog.dismiss());
        btnLayout.addView(cancel);

        Button print = new Button(this);
        print.setText("Print");
        applyFontSettings(print, 14);
        print.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));
        print.setTextColor(Color.WHITE);
        print.setOnClickListener(v -> printSingleNote(titleEdit.getText().toString() + "\n\n" + contentEdit.getText().toString()));
        btnLayout.addView(print);

        Button save = new Button(this);
        save.setText("Save");
        applyFontSettings(save, 14);
        save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));
        save.setTextColor(Color.WHITE);
        save.setOnClickListener(v -> {
            String newTitle = titleEdit.getText().toString().trim();
            String newContent = contentEdit.getText().toString().trim();
            if (!newContent.isEmpty()) {
                updateNote(key, index, (newTitle.isEmpty() ? "No Name" : newTitle) + TITLE_SEP + newContent);
                dialog.dismiss();
            }
        });
        btnLayout.addView(save);

        layout.addView(btnLayout);
        dialog.setContentView(layout);
        dialog.show();
    }

    private void updateNote(String key, int index, String fullNote) {
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
            notesList.set(index, fullNote);
            String updatedNotes = String.join(SEPARATOR, notesList);
            securePrefs.edit().putString(key, updatedNotes).apply();
            loadNotes(key);
            Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show();
        }
    }

    private void moveNote(String key, int fromIndex, int toIndex) {
        String notesStr = securePrefs.getString(key, "");
        if (notesStr.isEmpty()) return;

        String[] rawArray = notesStr.split(SEPARATOR);
        List<String> notesList = new ArrayList<>();
        for (String s : rawArray) {
            if (!s.trim().isEmpty()) {
                notesList.add(s);
            }
        }

        if (fromIndex >= 0 && fromIndex < notesList.size() && toIndex >= 0 && toIndex < notesList.size()) {
            String note = notesList.remove(fromIndex);
            notesList.add(toIndex, note);
            String updatedNotes = String.join(SEPARATOR, notesList);
            securePrefs.edit().putString(key, updatedNotes).apply();
            loadNotes(key);
        }
    }

    private void printSingleNote(String note) {
        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<h1>Secure Note</h1>");
        html.append("<p>").append(note.replace("\n", "<br>")).append("</p>");
        html.append("</body></html>");
        doPrint(html.toString(), "Secure_Note");
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
            loadNotes(key);
            Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshColors() {
        int mainTheme = colorPrefs.getInt("color_main_theme", getColor(R.color.light_green));
        int bgColor = colorPrefs.getInt("color_app_background", Color.BLACK);

        View root = findViewById(R.id.main);
        if (root != null) root.setBackgroundColor(bgColor);

        TextView title = findViewById(R.id.secureBoxTitle);
        if (title != null) {
            title.setTextColor(mainTheme);
            applyFontSettings(title, 24);
        }

        if (noteTitleInput != null) applyFontSettings(noteTitleInput, 18);
        if (noteContentInput != null) applyFontSettings(noteContentInput, 18);
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

        int sizeIndex = fontPrefs.getInt("font_size_index", 1);
        float multiplier = 1.0f;
        switch (sizeIndex) {
            case 0: multiplier = 0.8f; break;
            case 1: multiplier = 1.0f; break;
            case 2: multiplier = 1.3f; break;
            case 3: multiplier = 1.6f; break;
        }
        textView.setTextSize(baseSize * multiplier);
    }

    private void setupAutoScroll() {
        View.OnFocusChangeListener scrollListener = (v, hasFocus) -> {
            if (hasFocus) {
                notesScrollView.postDelayed(() -> notesScrollView.fullScroll(View.FOCUS_DOWN), 300);
            }
        };

        noteTitleInput.setOnFocusChangeListener(scrollListener);
        noteContentInput.setOnFocusChangeListener(scrollListener);
    }
}
