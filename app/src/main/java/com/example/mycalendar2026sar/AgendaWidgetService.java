package com.example.mycalendar2026sar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AgendaWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new AgendaRemoteViewsFactory(this.getApplicationContext());
    }
}

class AgendaRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private final Context context;
    private final List<AgendaItem> agendaItems = new ArrayList<>();

    public AgendaRemoteViewsFactory(Context context) {
        this.context = context;
    }

    @Override
    public void onCreate() {}

    @Override
    public void onDataSetChanged() {
        agendaItems.clear();
        SharedPreferences prefs = context.getSharedPreferences("CalendarNotes", Context.MODE_PRIVATE);
        Map<String, ?> allEntries = prefs.getAll();
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        Date today = now.getTime();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String dateKey = entry.getKey();
            try {
                Date noteDate = sdf.parse(dateKey);
                if (noteDate != null && !noteDate.before(today)) {
                    Object valObj = entry.getValue();
                    String value = valObj != null ? valObj.toString() : "";
                    if (!value.isEmpty()) {
                        String[] notes = value.split("\n");
                        for (String note : notes) {
                            agendaItems.add(new AgendaItem(dateKey, note, noteDate));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Sort by date
        agendaItems.sort((o1, o2) -> o1.date.compareTo(o2.date));
    }

    @Override
    public void onDestroy() {
        agendaItems.clear();
    }

    @Override
    public int getCount() {
        return agendaItems.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position >= agendaItems.size()) return null;

        AgendaItem item = agendaItems.get(position);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.agenda_widget_item);
        views.setTextViewText(R.id.item_date, item.dateKey);
        views.setTextViewText(R.id.item_text, item.text);

        Intent fillInIntent = new Intent();
        views.setOnClickFillInIntent(R.id.item_text, fillInIntent);

        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private static class AgendaItem {
        String dateKey;
        String text;
        Date date;

        AgendaItem(String dateKey, String text, Date date) {
            this.dateKey = dateKey;
            this.text = text;
            this.date = date;
        }
    }
}
