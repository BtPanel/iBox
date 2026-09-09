package com.imedia.box.ui.dialog;

import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.R;
import com.imedia.box.databinding.AdapterLiveSettingActionBinding;
import com.imedia.box.databinding.AdapterLiveSettingCategoryBinding;
import com.imedia.box.databinding.DialogLiveSettingsBinding;
import com.imedia.box.setting.LiveSetting;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LiveSettingsDialog {

    public static final int CHANNEL_LIST = 1;
    public static final int CHANNEL_SEARCH = 2;
    public static final int CHANNEL_EPG = 3;
    public static final int CHANNEL_KEEP = 4;
    public static final int CHANNEL_LINE = 5;
    public static final int PLAY_TOGGLE = 10;
    public static final int PLAY_ENGINE = 11;
    public static final int PLAY_DECODE = 12;
    public static final int PLAY_SPEED = 13;
    public static final int PLAY_AUDIO = 14;
    public static final int PLAY_TEXT = 15;
    public static final int PLAY_VIDEO = 16;
    public static final int PLAY_RENDER = 17;
    public static final int DISPLAY_SCALE = 20;
    public static final int DISPLAY_INFO = 21;
    public static final int PLAY_RELOAD = 22;
    public static final int PLAY_RECONNECT = 23;
    public static final int PLAY_CATCHUP = 24;
    public static final int PLAY_TIMEOUT = 25;
    public static final int PLAY_RTSP = 26;
    public static final int PLAY_TIMESHIFT_END = 27;
    public static final int PREFERENCE_INVERT = 30;
    public static final int PREFERENCE_ACROSS = 31;
    public static final int PREFERENCE_CHANGE = 32;
    public static final int PREFERENCE_DECODE = 33;
    public static final int MANAGE_SELECT = 40;
    public static final int MANAGE_SOURCE = 41;
    public static final int MANAGE_REFRESH = 42;
    public static final int MANAGE_ADD = 43;
    public static final int MANAGE_FILE = 44;
    public static final int PREFERENCE_BOOT = 45;
    public static final int MANAGE_EPG = 46;
    public static final int MANAGE_PUSH = 47;
    public static final int MANAGE_EPG_CUSTOM = 48;
    public static final int MANAGE_UA = 49;
    public static final int LIST_LOGO = 50;
    public static final int LIST_NUMBER = 51;
    public static final int LIST_EPG = 52;
    public static final int LIST_OPACITY = 53;
    public static final int DISPLAY_CLOCK = 54;
    public static final int DISPLAY_BOTTOM = 55;
    public static final int MANAGE_HEADER = 56;
    public static final int MANAGE_GROUP = 57;
    public static final int MANAGE_REQUEST = 58;
    public static final int PLAY_TRACKS = 59;
    public static final int MANAGE_EPG_MENU = 60;
    public static final int MANAGE_EPG_CACHE = 61;
    public static final int DISPLAY_NETWORK = 62;
    public static final int LIST_DENSITY = 63;
    public static final int MANAGE_APP_SETTINGS = 66;
    public static final int DISPLAY_BOTTOM_LOGO = 67;
    public static final int DISPLAY_CATCHUP_INDICATOR = 68;
    public static final int OPACITY_BASE = 400;
    public static final int GROUP_BASE = 1000;
    public static final int SOURCE_BASE = 2000;
    public static final int LINE_BASE = 3000;

    private static final String TAG = "live_settings";

    private LiveSettingsDialog() {
    }

    public static void show(FragmentActivity activity, Listener listener) {
        FragmentManager manager = activity.getSupportFragmentManager();
        for (Fragment fragment : manager.getFragments()) if (fragment instanceof BottomSheet || fragment instanceof SideSheet) return;
        boolean full = Util.isLeanback();
        if (full || Util.isFullscreenLand(activity)) new SideSheet(listener, full).show(manager, TAG);
        else new BottomSheet(listener).show(manager, TAG);
    }

    public interface Listener {

        void onLiveSettingAction(int action);

        String getLiveSettingSummary(int action);

        default List<String> getLiveSettingGroups() {
            return Collections.emptyList();
        }

        default List<String> getLiveSettingSources() {
            return Collections.emptyList();
        }

        default List<String> getLiveSettingLines() {
            return Collections.emptyList();
        }

        default List<String> getLiveSettingOptions(int action) {
            return Collections.emptyList();
        }

        default int getLiveSettingOptionIndex(int action) {
            return -1;
        }

        default void onLiveSettingOption(int action, int index) {
        }
    }

    private static DialogLiveSettingsBinding inflate(LayoutInflater inflater, ViewGroup container) {
        return DialogLiveSettingsBinding.inflate(inflater, container, false);
    }

    private static final class Panel {

        private final DialogLiveSettingsBinding binding;
        private final Listener listener;
        private final boolean full;
        private final Runnable dismiss;
        private final Runnable applyWindowOpacity;
        private final Runnable expandWindow;
        private final Runnable collapseWindow;
        private CategoryAdapter categoryAdapter;
        private ActionAdapter actionAdapter;
        private ActionAdapter detailAdapter;
        private int actionPosition;

        private Panel(DialogLiveSettingsBinding binding, Listener listener, boolean full, Runnable dismiss, Runnable applyWindowOpacity, Runnable expandWindow, Runnable collapseWindow) {
            this.binding = binding;
            this.listener = listener;
            this.full = full;
            this.dismiss = dismiss;
            this.applyWindowOpacity = applyWindowOpacity;
            this.expandWindow = expandWindow;
            this.collapseWindow = collapseWindow;
        }

        private void bind() {
            int orientation = full ? RecyclerView.VERTICAL : RecyclerView.HORIZONTAL;
            binding.categories.setLayoutManager(new LinearLayoutManager(binding.getRoot().getContext(), orientation, false));
            binding.actions.setLayoutManager(new LinearLayoutManager(binding.getRoot().getContext()));
            binding.details.setLayoutManager(new LinearLayoutManager(binding.getRoot().getContext()));
            binding.categories.setAdapter(categoryAdapter = new CategoryAdapter(categories(), this::selectCategory, full));
            binding.actions.setAdapter(actionAdapter = new ActionAdapter(listener, this::onAction, this::onActionKey, !full));
            binding.details.setAdapter(detailAdapter = new ActionAdapter(listener, this::onDetail, this::onDetailKey, !full));
            binding.details.setVisibility(View.GONE);
            applyPanelOpacity();
            selectCategory(0, false);
            binding.categories.post(() -> {
                RecyclerView.ViewHolder holder = binding.categories.findViewHolderForAdapterPosition(0);
                if (holder != null) holder.itemView.requestFocus();
            });
        }

        private List<Category> categories() {
            if (!full) return compactCategories();
            List<Category> result = new ArrayList<>();
            result.add(new Category(R.string.live_menu_line_category, Collections.singletonList(
                    childAction(CHANNEL_LINE, R.string.live_menu_line, dynamicActions(LINE_BASE, listener.getLiveSettingLines(), true)))));
            result.add(new Category(R.string.live_menu_playback, playbackActions()));
            result.add(new Category(R.string.live_menu_category_manage, Collections.singletonList(
                    childAction(CHANNEL_LIST, R.string.live_menu_channel_group, dynamicActions(GROUP_BASE, listener.getLiveSettingGroups(), true)))));
            result.add(new Category(R.string.live_menu_subscription, subscriptionActions()));
            result.add(new Category(R.string.live_menu_display, Arrays.asList(
                    optionAction(DISPLAY_CLOCK, R.string.live_menu_clock),
                    optionAction(DISPLAY_CATCHUP_INDICATOR, R.string.live_menu_catchup_indicator),
                    optionAction(DISPLAY_NETWORK, R.string.live_menu_network_speed),
                    optionAction(DISPLAY_BOTTOM, R.string.live_menu_bottom_info),
                    optionAction(DISPLAY_BOTTOM_LOGO, R.string.live_menu_bottom_logo),
                    childAction(LIST_OPACITY, R.string.live_menu_panel_opacity, opacityActions()),
                    action(DISPLAY_INFO, R.string.live_menu_info, true))));
            result.add(new Category(R.string.live_menu_preference, Arrays.asList(
                    optionAction(PREFERENCE_DECODE, R.string.live_menu_remember_decode),
                    optionAction(PREFERENCE_INVERT, R.string.live_menu_invert),
                    optionAction(PREFERENCE_ACROSS, R.string.live_menu_across),
                    optionAction(PREFERENCE_CHANGE, R.string.live_menu_change),
                    optionAction(PREFERENCE_BOOT, R.string.live_menu_boot))));
            result.add(new Category(R.string.live_menu_channel, channelActions()));
            result.add(new Category(R.string.live_menu_other, Arrays.asList(
                    action(PLAY_TOGGLE, R.string.live_menu_play_toggle, false),
                    action(MANAGE_REFRESH, R.string.live_refresh, true),
                    action(DISPLAY_INFO, R.string.live_menu_info, true))));
            result.add(new Category(R.string.live_menu_more, Arrays.asList(
                    action(MANAGE_SOURCE, R.string.repository_live_title, true),
                    action(MANAGE_ADD, R.string.live_menu_add_source, true),
                    action(MANAGE_FILE, R.string.live_menu_local_list, true),
                    action(MANAGE_APP_SETTINGS, R.string.live_menu_app_settings, true))));
            return result;
        }

        private List<Category> compactCategories() {
            List<Category> result = new ArrayList<>();
            result.add(new Category(R.string.live_menu_playback, playbackActions()));
            result.add(new Category(R.string.live_menu_channel, channelActions()));
            result.add(new Category(R.string.live_menu_manage, Arrays.asList(
                    action(MANAGE_SELECT, R.string.live_menu_source_select, true),
                    action(MANAGE_SOURCE, R.string.repository_live_title, true),
                    action(MANAGE_REFRESH, R.string.live_refresh, true))));
            result.add(new Category(R.string.live_menu_display, Arrays.asList(
                    action(DISPLAY_SCALE, R.string.live_menu_scale, false),
                    action(DISPLAY_INFO, R.string.live_menu_info, true))));
            return result;
        }

        private List<Action> dynamicActions(int base, List<String> labels, boolean close) {
            List<Action> result = new ArrayList<>();
            for (int i = 0; i < labels.size(); i++) result.add(new Action(base + i, labels.get(i), close));
            if (result.isEmpty() && base == LINE_BASE) result.add(action(CHANNEL_LINE, R.string.live_menu_line, false));
            return result;
        }

        private List<Action> subscriptionActions() {
            List<Action> result = new ArrayList<>();
            result.add(childAction(MANAGE_SELECT, R.string.live_menu_source_select, dynamicActions(SOURCE_BASE, listener.getLiveSettingSources(), true)));
            result.add(action(MANAGE_PUSH, R.string.live_menu_scan_push, true));
            result.add(action(CHANNEL_SEARCH, R.string.live_channel_search, true));
            result.add(action(MANAGE_EPG_MENU, R.string.live_menu_epg_subscription, false));
            result.add(childAction(MANAGE_REQUEST, R.string.live_menu_request, Arrays.asList(
                    action(MANAGE_UA, R.string.player_ua, true),
                    action(MANAGE_HEADER, R.string.live_menu_headers, true))));
            result.add(action(MANAGE_SOURCE, R.string.live_menu_subscription_list, true));
            result.add(action(MANAGE_ADD, R.string.live_menu_add_source, true));
            result.add(action(MANAGE_FILE, R.string.live_menu_local_list, true));
            return result;
        }

        private List<Action> channelActions() {
            if (!full) return new ArrayList<>(Arrays.asList(
                    action(CHANNEL_LIST, R.string.live_channel_list, true),
                    action(CHANNEL_SEARCH, R.string.live_channel_search, true),
                    action(CHANNEL_LINE, R.string.live_menu_line, false),
                    action(CHANNEL_KEEP, R.string.live_menu_keep, false),
                    action(LIST_LOGO, R.string.live_menu_show_logo, false),
                    action(LIST_NUMBER, R.string.live_menu_show_number, false),
                    action(LIST_EPG, R.string.live_menu_show_epg, false)));
            List<Action> result = new ArrayList<>(Arrays.asList(
                    optionAction(LIST_DENSITY, R.string.live_menu_list_density),
                    action(CHANNEL_LIST, R.string.live_channel_list, true),
                    action(CHANNEL_SEARCH, R.string.live_channel_search, true),
                    action(CHANNEL_EPG, R.string.live_menu_epg, true),
                    action(CHANNEL_KEEP, R.string.live_menu_keep, false),
                    optionAction(LIST_LOGO, R.string.live_menu_show_logo),
                    optionAction(LIST_NUMBER, R.string.live_menu_show_number),
                    optionAction(LIST_EPG, R.string.live_menu_show_epg)));
            return result;
        }

        private List<Action> playbackActions() {
            if (!full) return new ArrayList<>(Arrays.asList(
                    action(PLAY_RELOAD, R.string.live_menu_reload, true),
                    action(PLAY_ENGINE, R.string.player_engine, true),
                    action(PLAY_DECODE, R.string.live_menu_decode, false),
                    action(DISPLAY_SCALE, R.string.live_menu_scale, false),
                    action(PREFERENCE_CHANGE, R.string.live_menu_change, false),
                    action(PLAY_RECONNECT, R.string.live_menu_reconnect, false),
                    action(PLAY_SPEED, R.string.play_speed, true),
                    action(PLAY_AUDIO, R.string.play_track_audio, true)));
            List<Action> result = new ArrayList<>(Arrays.asList(
                    action(PLAY_TOGGLE, R.string.live_menu_play_toggle, false),
                    action(PLAY_RELOAD, R.string.live_menu_reload, true),
                    optionAction(PLAY_ENGINE, R.string.player_engine),
                    optionAction(PLAY_DECODE, R.string.live_menu_decode),
                    optionAction(DISPLAY_SCALE, R.string.live_menu_scale),
                    optionAction(PLAY_TIMEOUT, R.string.live_menu_timeout),
                    optionAction(PREFERENCE_CHANGE, R.string.live_menu_change),
                    optionAction(PLAY_RECONNECT, R.string.live_menu_reconnect),
                    optionAction(PLAY_RENDER, R.string.player_render),
                    optionAction(PLAY_RTSP, R.string.live_menu_rtsp),
                    optionAction(PLAY_TIMESHIFT_END, R.string.live_menu_timeshift_end),
                    optionAction(PLAY_SPEED, R.string.play_speed)));
            addOptionAction(result, PLAY_AUDIO, R.string.play_track_audio);
            addOptionAction(result, PLAY_TEXT, R.string.play_track_text);
            addOptionAction(result, PLAY_VIDEO, R.string.play_track_video);
            result.add(action(PLAY_CATCHUP, R.string.live_menu_catchup, true));
            return result;
        }

        private Action optionAction(int id, int title) {
            return childAction(id, title, optionActions(id));
        }

        private void addOptionAction(List<Action> actions, int id, int title) {
            List<Action> options = optionActions(id);
            if (!options.isEmpty()) actions.add(new Action(id, title, false, options));
        }

        private List<Action> optionActions(int id) {
            List<String> labels = listener.getLiveSettingOptions(id);
            List<Action> result = new ArrayList<>();
            for (int i = 0; i < labels.size(); i++) result.add(new Action(id, labels.get(i), i));
            return result;
        }

        private List<Action> opacityActions() {
            return Arrays.asList(
                    new Action(OPACITY_BASE, "70%", false),
                    new Action(OPACITY_BASE + 1, "80%", false),
                    new Action(OPACITY_BASE + 2, "90%", false),
                    new Action(OPACITY_BASE + 3, "100%", false));
        }

        private Action action(int id, int title, boolean close) {
            return new Action(id, title, close);
        }

        private Action childAction(int id, int title, List<Action> children) {
            return children.isEmpty() ? action(id, title, false) : new Action(id, title, false, children);
        }

        private void selectCategory(int position, boolean moveRight) {
            if (position < 0 || position >= categoryAdapter.items.size()) return;
            categoryAdapter.select(position);
            for (int i = 0; i < binding.categories.getChildCount(); i++) {
                View child = binding.categories.getChildAt(i);
                child.setSelected(binding.categories.getChildAdapterPosition(child) == position);
            }
            List<Action> actions = categoryAdapter.items.get(position).actions;
            collapseDetails(false);
            if (binding.actions.isComputingLayout()) binding.actions.post(() -> actionAdapter.setItems(actions));
            else actionAdapter.setItems(actions);
            if (moveRight) focusFirst(binding.actions);
        }

        private void onAction(Action action, int position) {
            if (full && !action.children.isEmpty()) {
                expandDetails(action, position);
                return;
            }
            runAction(action, binding.actions, actionAdapter);
        }

        private void onDetail(Action action, int position) {
            runAction(action, binding.details, detailAdapter);
        }

        private void runAction(Action action, RecyclerView owner, ActionAdapter adapter) {
            if (action.close) dismiss.run();
            if (action.optionIndex >= 0) listener.onLiveSettingOption(action.id, action.optionIndex);
            else listener.onLiveSettingAction(action.id);
            if (!action.close) {
                if (action.id == LIST_OPACITY) applyPanelOpacity();
                if (action.id >= OPACITY_BASE && action.id < OPACITY_BASE + 4) applyPanelOpacity();
                owner.postDelayed(() -> {
                    adapter.notifyDataSetChanged();
                    if (action.optionIndex >= 0) focusSelected(owner, action.optionIndex);
                }, 120);
                binding.actions.postDelayed(actionAdapter::notifyDataSetChanged, 120);
            }
        }

        private boolean onActionKey(Action action, int position, int keyCode) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !action.children.isEmpty()) {
                expandDetails(action, position);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                focusSelected(binding.categories, categoryAdapter.selected);
                return true;
            }
            return false;
        }

        private boolean onDetailKey(Action action, int position, int keyCode) {
            if (keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            collapseDetails(true);
            return true;
        }

        private void expandDetails(Action action, int position) {
            if (action.children.isEmpty()) return;
            actionPosition = position;
            actionAdapter.select(position);
            detailAdapter.setItems(action.children);
            binding.details.setVisibility(View.VISIBLE);
            expandWindow.run();
            int selected = listener.getLiveSettingOptionIndex(action.id);
            focusSelected(binding.details, selected < 0 ? 0 : selected);
        }

        private void collapseDetails(boolean focusAction) {
            if (!full || binding.details.getVisibility() != View.VISIBLE) return;
            binding.details.setVisibility(View.GONE);
            detailAdapter.setItems(Collections.emptyList());
            actionAdapter.select(-1);
            collapseWindow.run();
            if (focusAction) focusSelected(binding.actions, actionPosition);
        }

        private boolean onBackPressed() {
            if (binding.details.getVisibility() == View.VISIBLE) {
                collapseDetails(true);
                return true;
            }
            if (binding.actions.hasFocus()) {
                focusSelected(binding.categories, categoryAdapter.selected);
                return true;
            }
            return false;
        }

        private void focusFirst(RecyclerView view) {
            focusSelected(view, 0);
        }

        private void focusSelected(RecyclerView view, int position) {
            view.post(() -> {
                RecyclerView.ViewHolder holder = view.findViewHolderForAdapterPosition(Math.max(0, position));
                if (holder != null) holder.itemView.requestFocus();
                else view.requestFocus();
            });
        }

        private void applyPanelOpacity() {
            if (!full) return;
            binding.getRoot().setBackgroundColor(android.graphics.Color.TRANSPARENT);
            applyWindowOpacity.run();
        }
    }

    private static final class Category {

        private final int title;
        private final List<Action> actions;

        private Category(int title, List<Action> actions) {
            this.title = title;
            this.actions = actions;
        }
    }

    private static final class Action {

        private final int id;
        private final int title;
        private final String label;
        private final boolean close;
        private final List<Action> children;
        private final int optionIndex;

        private Action(int id, int title, boolean close) {
            this.id = id;
            this.title = title;
            this.label = null;
            this.close = close;
            this.children = Collections.emptyList();
            this.optionIndex = -1;
        }

        private Action(int id, String label, boolean close) {
            this.id = id;
            this.title = 0;
            this.label = label;
            this.close = close;
            this.children = Collections.emptyList();
            this.optionIndex = -1;
        }

        private Action(int id, int title, boolean close, List<Action> children) {
            this.id = id;
            this.title = title;
            this.label = null;
            this.close = close;
            this.children = children;
            this.optionIndex = -1;
        }

        private Action(int id, String label, int optionIndex) {
            this.id = id;
            this.title = 0;
            this.label = label;
            this.close = false;
            this.children = Collections.emptyList();
            this.optionIndex = optionIndex;
        }
    }

    private interface CategoryClick {

        void accept(int position, boolean moveRight);
    }

    private interface ActionClick {

        void accept(Action action, int position);
    }

    private interface ActionKey {

        boolean accept(Action action, int position, int keyCode);
    }

    private static final class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {

        private final List<Category> items;
        private final CategoryClick click;
        private final boolean selectOnFocus;
        private int selected;

        private CategoryAdapter(List<Category> items, CategoryClick click, boolean selectOnFocus) {
            this.items = items;
            this.click = click;
            this.selectOnFocus = selectOnFocus;
        }

        private void select(int position) {
            selected = position;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(AdapterLiveSettingCategoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.binding.title.setText(items.get(position).title);
            holder.binding.getRoot().setSelected(position == selected);
            holder.binding.getRoot().setOnClickListener(v -> click.accept(holder.getBindingAdapterPosition(), true));
            holder.binding.getRoot().setOnFocusChangeListener((v, focused) -> {
                int index = holder.getBindingAdapterPosition();
                if (focused && selectOnFocus && index != RecyclerView.NO_POSITION) click.accept(index, false);
            });
            holder.binding.getRoot().setOnKeyListener((v, keyCode, event) -> {
                int index = holder.getBindingAdapterPosition();
                if (!selectOnFocus || keyCode != KeyEvent.KEYCODE_DPAD_LEFT || event.getAction() != KeyEvent.ACTION_DOWN || index == RecyclerView.NO_POSITION) return false;
                click.accept(index, true);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private static final class Holder extends RecyclerView.ViewHolder {

            private final AdapterLiveSettingCategoryBinding binding;

            private Holder(AdapterLiveSettingCategoryBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private static final class ActionAdapter extends RecyclerView.Adapter<ActionAdapter.Holder> {

        private final Listener listener;
        private final ActionClick click;
        private final ActionKey key;
        private final boolean showSummaries;
        private List<Action> items = new ArrayList<>();
        private int selected = -1;

        private ActionAdapter(Listener listener, ActionClick click, ActionKey key, boolean showSummaries) {
            this.listener = listener;
            this.click = click;
            this.key = key;
            this.showSummaries = showSummaries;
        }

        private void setItems(List<Action> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        private void select(int position) {
            int old = selected;
            selected = position;
            if (old >= 0) notifyItemChanged(old);
            if (position >= 0) notifyItemChanged(position);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(AdapterLiveSettingActionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Action action = items.get(position);
            String summary = !showSummaries ? "" : action.optionIndex >= 0
                    ? (listener.getLiveSettingOptionIndex(action.id) == action.optionIndex ? holder.itemView.getContext().getString(R.string.live_menu_current) : "")
                    : (showSummary(action) ? listener.getLiveSettingSummary(action.id) : "");
            if (action.label == null) holder.binding.title.setText(action.title);
            else holder.binding.title.setText(action.label);
            holder.binding.summary.setText(summary);
            holder.binding.summary.setVisibility(summary == null || summary.isEmpty() ? View.GONE : View.VISIBLE);
            View meta = (View) holder.binding.summary.getParent();
            if (meta != holder.binding.getRoot()) {
                meta.setVisibility(summary != null && !summary.isEmpty() ? View.VISIBLE : View.GONE);
            }
            holder.binding.getRoot().setSelected(position == selected);
            holder.binding.getRoot().setOnClickListener(v -> {
                int index = holder.getBindingAdapterPosition();
                if (index != RecyclerView.NO_POSITION) click.accept(action, index);
            });
            holder.binding.getRoot().setOnKeyListener((v, keyCode, event) -> {
                int index = holder.getBindingAdapterPosition();
                return event.getAction() == KeyEvent.ACTION_DOWN && index != RecyclerView.NO_POSITION && key.accept(action, index, keyCode);
            });
        }

        private boolean showSummary(Action action) {
            if (!showSummaries || !action.children.isEmpty()) return false;
            return action.id != MANAGE_PUSH
                    && action.id != MANAGE_EPG
                    && action.id != MANAGE_EPG_CUSTOM
                    && action.id != MANAGE_UA
                    && action.id != MANAGE_HEADER;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private static final class Holder extends RecyclerView.ViewHolder {

            private final AdapterLiveSettingActionBinding binding;

            private Holder(AdapterLiveSettingActionBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    public static final class BottomSheet extends BaseBottomSheetDialog {

        private final Listener listener;
        private DialogLiveSettingsBinding binding;

        private BottomSheet(Listener listener) {
            this.listener = listener;
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = LiveSettingsDialog.inflate(inflater, container);
        }

        @Override
        protected int getMaxHeight() {
            return ResUtil.getScreenHeight() * 4 / 5;
        }

        @Override
        protected void initView() {
            new Panel(binding, listener, false, this::dismiss, () -> {}, () -> {}, () -> {}).bind();
        }
    }

    public static final class SideSheet extends BaseSideSheetDialog {

        private final Listener listener;
        private final boolean full;
        private DialogLiveSettingsBinding binding;
        private Panel panel;

        private SideSheet(Listener listener, boolean full) {
            this.listener = listener;
            this.full = full;
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = LiveSettingsDialog.inflate(inflater, container);
        }

        @Override
        protected int getWidth() {
            return Math.min(ResUtil.dp2px(full ? 600 : 520), ResUtil.getScreenWidth() * (full ? 31 : 58) / 100);
        }

        @Override
        protected int getDialogTheme() {
            return full ? R.style.ThemeOverlay_IBox_Dialog_LiveSideSheet : super.getDialogTheme();
        }

        @Override
        protected void initView() {
            panel = new Panel(binding, listener, full, this::dismiss, this::applyWindowOpacity,
                    () -> setSheetWidth(true), () -> setSheetWidth(false));
            panel.bind();
        }

        private void setSheetWidth(boolean expanded) {
            if (!full || getDialog() == null) return;
            View sheet = getDialog().findViewById(com.google.android.material.R.id.m3_side_sheet);
            if (sheet == null) return;
            ViewGroup.LayoutParams params = sheet.getLayoutParams();
            params.width = expanded
                    ? Math.min(ResUtil.dp2px(900), ResUtil.getScreenWidth() * 52 / 100)
                    : getWidth();
            sheet.setLayoutParams(params);
        }

        private void applyWindowOpacity() {
            if (!full || getDialog() == null || getDialog().getWindow() == null) return;
            android.view.WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();
            params.alpha = LiveSetting.getPanelOpacity() / 100f;
            getDialog().getWindow().setAttributes(params);
        }

        @Override
        public void onStart() {
            super.onStart();
            if (!full) return;
            View sheet = requireDialog().findViewById(com.google.android.material.R.id.m3_side_sheet);
            if (sheet != null) {
                sheet.setBackgroundTintList(null);
                sheet.setBackground(null);
            }
            if (requireDialog().getWindow() != null) {
                requireDialog().getWindow().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
                requireDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                requireDialog().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                requireDialog().getWindow().setDimAmount(0f);
                applyWindowOpacity();
            }
            requireDialog().setOnKeyListener((dialog, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_BACK || event.getAction() != KeyEvent.ACTION_DOWN) return false;
                return panel != null && panel.onBackPressed();
            });
        }
    }
}
