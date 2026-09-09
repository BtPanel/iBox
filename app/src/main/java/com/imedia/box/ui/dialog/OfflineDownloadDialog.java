package com.imedia.box.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

import com.imedia.box.ui.custom.IBoxAlertDialogBuilder;
import com.imedia.box.R;
import com.imedia.box.bean.Episode;
import com.imedia.box.bean.Flag;
import com.imedia.box.offline.OfflineDownloadCoordinator;
import com.imedia.box.ui.activity.OfflineDownloadActivity;

import java.util.ArrayList;
import java.util.List;

public final class OfflineDownloadDialog {

    private OfflineDownloadDialog() {
    }

    public static void show(Activity activity, String siteKey, String vodId, String vodName, String vodPic, Flag flag, Episode current) {
        if (flag == null || flag.getEpisodes().isEmpty()) return;
        List<Episode> episodes = flag.getEpisodes();
        String[] labels = new String[episodes.size() + 1];
        boolean[] checked = new boolean[labels.length];
        labels[0] = activity.getString(R.string.download_select_all_line);
        for (int i = 0; i < episodes.size(); i++) {
            labels[i + 1] = episodes.get(i).getName();
            checked[i + 1] = episodes.get(i).equals(current);
        }
        AlertDialog dialog = new IBoxAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.download_select_title, flag.getShow()))
                .setMultiChoiceItems(labels, checked, null)
                .setNegativeButton(R.string.dialog_negative, null)
                .setNeutralButton(R.string.download_manager, (target, which) -> OfflineDownloadActivity.start(activity))
                .setPositiveButton(R.string.download_short, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getListView().setOnItemClickListener((parent, view, position, id) -> toggle(dialog, checked, position));
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
                List<Episode> selected = selected(episodes, checked);
                if (selected.isEmpty()) return;
                OfflineDownloadCoordinator.enqueue(siteKey, vodId, vodName, vodPic, flag, selected);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private static void toggle(AlertDialog dialog, boolean[] checked, int position) {
        if (position != 0) {
            checked[position] = dialog.getListView().isItemChecked(position);
            boolean all = true;
            for (int i = 1; i < checked.length; i++) all &= checked[i];
            checked[0] = all;
            dialog.getListView().setItemChecked(0, all);
            return;
        }
        boolean selectAll = dialog.getListView().isItemChecked(0);
        for (int i = 0; i < checked.length; i++) {
            checked[i] = selectAll;
            dialog.getListView().setItemChecked(i, selectAll);
        }
    }

    private static List<Episode> selected(List<Episode> episodes, boolean[] checked) {
        List<Episode> selected = new ArrayList<>();
        for (int i = 0; i < episodes.size(); i++) if (checked[i + 1]) selected.add(episodes.get(i));
        return selected;
    }
}
