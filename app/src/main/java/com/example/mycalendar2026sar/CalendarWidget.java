package com.example.mycalendar2026sar;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CalendarWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.getTime()).toUpperCase();
        String day = new SimpleDateFormat("dd", Locale.getDefault()).format(calendar.getTime());

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.calendar_widget);
        views.setTextViewText(R.id.widget_month, month);
        views.setTextViewText(R.id.widget_day, day);

        // Click to open app
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
